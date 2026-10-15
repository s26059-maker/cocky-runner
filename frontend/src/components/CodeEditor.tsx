import { useEffect, useImperativeHandle, useRef, useState, type Ref } from 'react'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/editor/editor.worker?worker'
import Editor, { loader, type OnMount } from '@monaco-editor/react'
import type { Diagnostic } from '../types'
import './CodeEditor.css'

// Bundle Monaco locally (no CDN). Only the base editor worker is registered:
// C/Python/Java use Monaco's built-in Monarch tokenizers, which run on the
// main thread, so the ts/json/css/html language workers are never needed.
self.MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
}
loader.config({ monaco })

// Mirrors the backend's supported languages (see com.cocky.cockyrunner.domain.Language) -
// the keys are the enum names themselves (uppercase, matching what SubmissionController's
// parseLanguage()/Language.valueOf() expect on the wire), the values are the Monaco
// language ids (registered in monaco-editor's languages/definitions/*/register.js).
// Add a case here if the backend ever grows another language.
const MONACO_LANGUAGE_IDS = {
  C: 'c',
  PYTHON: 'python',
  JAVA: 'java',
} as const

export type SupportedLanguage = keyof typeof MONACO_LANGUAGE_IDS

export const SUPPORTED_LANGUAGES: SupportedLanguage[] = Object.keys(MONACO_LANGUAGE_IDS) as SupportedLanguage[]

// Starter skeleton inserted when the editor is empty (see ProblemDetailPage) -
// never inserted over code the user has already written. Python gets none;
// there's no single unambiguous "empty program" skeleton worth presupposing.
export const LANGUAGE_TEMPLATES: Record<SupportedLanguage, string> = {
  C: '#include <stdio.h>\n\nint main(void) {\n    \n    return 0;\n}\n',
  PYTHON: '',
  JAVA: 'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n    }\n}\n',
}

const THEME_NAME = 'cocky-dark'

/** Reads a CSS custom property off :root as a trimmed string. */
function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

/** '#rrggbb' / '#rgb' / 'rgb(a)(r, g, b[, a])' -> 6- or 8-digit hex with leading '#', as Monaco requires. */
function toHex(color: string): string {
  const hex = (n: number) => Math.round(n).toString(16).padStart(2, '0')
  const rgba = color.match(/^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:[,\s/]+([\d.]+))?\s*\)$/)
  if (rgba) {
    const alpha = rgba[4] === undefined ? '' : hex(parseFloat(rgba[4]) * 255)
    return `#${hex(+rgba[1])}${hex(+rgba[2])}${hex(+rgba[3])}${alpha}`
  }
  if (/^#[0-9a-f]{3}$/i.test(color)) {
    return '#' + color.slice(1).split('').map((c) => c + c).join('')
  }
  return color
}

const color = (name: string) => toHex(cssVar(name))
// Monaco 토큰 규칙(foreground)은 6자리 hex만 지원하므로 알파를 버린다. --syntax-*는 불투명 색만 써야 한다.
const token = (name: string) => color(name).slice(1, 7)

// Chrome + syntax colors from the app's design tokens (src/styles/tokens.css),
// read once at mount so tokens.css stays the single source of truth. Same
// five syntax categories as before - keyword / string / number / comment /
// function name - and everything else Monaco's vs-dark would color on its own
// (types, identifiers, operators, ...) is pinned to --text-primary, so only
// those five stand out, as in the CodeMirror theme.
function defineCockyTheme(m: typeof monaco) {
  const plain = token('--text-primary')
  m.editor.defineTheme(THEME_NAME, {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: '', foreground: plain },
      ...['identifier', 'type', 'type.identifier', 'variable', 'constant', 'delimiter', 'operator', 'annotation',
        'predefined', 'tag', 'regexp', 'attribute.name', 'attribute.value', 'delimiter.bracket', 'metatag',
      ].map((t) => ({ token: t, foreground: plain })),
      { token: 'keyword', foreground: token('--syntax-keyword') },
      { token: 'string', foreground: token('--syntax-string') },
      { token: 'number', foreground: token('--syntax-number') },
      { token: 'comment', foreground: token('--syntax-comment'), fontStyle: 'italic' },
      { token: 'function', foreground: token('--syntax-function') },
    ],
    colors: {
      'editor.background': color('--bg-editor'),
      'editor.foreground': color('--text-primary'),
      'editorCursor.foreground': color('--accent'),
      'editor.lineHighlightBackground': color('--bg-editor-active-line'),
      'editor.lineHighlightBorder': '#00000000',
      'editor.selectionBackground': color('--accent-muted'),
      'editor.inactiveSelectionBackground': color('--accent-muted'),
      'editorLineNumber.foreground': color('--text-muted'),
      'editorLineNumber.activeForeground': color('--text-secondary'),
      'editorGutter.background': color('--bg-editor'),
      'minimap.background': color('--bg-editor'),
    },
  })
}

const MARKER_OWNER = 'cocky'

type ITextModel = monaco.editor.ITextModel

/** Maps a backend diagnostic to a Monaco marker, clamped to the model's bounds. */
function toMarker(m: typeof monaco, model: ITextModel, d: Diagnostic): monaco.editor.IMarkerData {
  const lastLine = model.getLineCount()
  const lineNumber = Math.min(Math.max(d.line, 1), lastLine)
  const maxColumn = model.getLineMaxColumn(lineNumber)

  let startColumn: number
  let endColumn: number
  // A clamped line no longer matches the column the tool reported, so mark the whole line then.
  if (d.column != null && lineNumber === d.line) {
    const column = Math.min(Math.max(d.column, 1), maxColumn)
    const word = model.getWordAtPosition({ lineNumber, column })
    if (word) {
      startColumn = word.startColumn
      endColumn = word.endColumn
    } else if (column < maxColumn) {
      startColumn = column
      endColumn = column + 1
    } else {
      startColumn = Math.max(column - 1, 1)
      endColumn = column
    }
  } else {
    startColumn = model.getLineFirstNonWhitespaceColumn(lineNumber) || 1
    endColumn = maxColumn
  }

  const severity =
    d.severity === 'ERROR'
      ? m.MarkerSeverity.Error
      : d.severity === 'WARNING'
        ? m.MarkerSeverity.Warning
        : m.MarkerSeverity.Info

  return { severity, message: d.message, startLineNumber: lineNumber, startColumn, endLineNumber: lineNumber, endColumn }
}

/** The only editor operation exposed to the page: jump to a line (see DiagnosticList). */
export interface CodeEditorHandle {
  revealLine: (line: number, column?: number | null) => void
}

interface CodeEditorProps {
  value: string
  onChange: (value: string) => void
  language: SupportedLanguage
  readOnly?: boolean
  /**
   * Fired on Ctrl+Shift+Enter / Cmd+Shift+Enter (KeyMod.CtrlCmd resolves to
   * Ctrl on Windows/Linux and Cmd on Mac on its own). Whether this is
   * currently a valid submission (blank code, already submitting, etc.) is
   * entirely the caller's concern - this just forwards the key press.
   */
  onSubmit: () => void
  /** Fired on Ctrl+Enter / Cmd+Enter; same contract as onSubmit. */
  onRun?: () => void
  /** Editor font size in px; defaults to --text-base. lineHeight follows at x1.6. */
  fontSize?: number
  /**
   * Compiler/runtime errors to underline. The caller decides when they appear
   * and go away (typing does not clear them); pass a stable array - the markers
   * are re-applied whenever this identity changes.
   */
  diagnostics?: Diagnostic[]
  ref?: Ref<CodeEditorHandle>
}

function CodeEditor({
  value,
  onChange,
  language,
  readOnly = false,
  onSubmit,
  onRun,
  fontSize,
  diagnostics,
  ref,
}: CodeEditorProps) {
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<typeof monaco | null>(null)
  // Markers can only be set once the editor exists; flipping this re-runs the marker effect.
  const [mounted, setMounted] = useState(false)

  useImperativeHandle(
    ref,
    () => ({
      revealLine(line, column) {
        const editor = editorRef.current
        const model = editor?.getModel()
        if (!editor || !model) return
        const lineNumber = Math.min(Math.max(line, 1), model.getLineCount())
        editor.revealLineInCenter(lineNumber)
        editor.setPosition({
          lineNumber,
          column: column != null ? Math.min(Math.max(column, 1), model.getLineMaxColumn(lineNumber))
            : model.getLineFirstNonWhitespaceColumn(lineNumber) || 1,
        })
        editor.focus()
      },
    }),
    [],
  )

  useEffect(() => {
    const m = monacoRef.current
    const model = editorRef.current?.getModel()
    if (!mounted || !m || !model) return
    m.editor.setModelMarkers(model, MARKER_OWNER, (diagnostics ?? []).map((d) => toMarker(m, model, d)))
  }, [diagnostics, mounted])
  // addCommand registers its callbacks once at mount, so they must read the
  // latest props through refs instead of closing over the first ones.
  const onSubmitRef = useRef(onSubmit)
  const onRunRef = useRef(onRun)
  useEffect(() => {
    onSubmitRef.current = onSubmit
    onRunRef.current = onRun
  }, [onSubmit, onRun])

  // document.fonts.ready can resolve after unmount; skip remeasuring then.
  // Reset on (re)mount so StrictMode's mount/cleanup/mount cycle stays correct.
  const disposedRef = useRef(false)
  useEffect(() => {
    disposedRef.current = false
    return () => {
      disposedRef.current = true
    }
  }, [])

  const handleMount: OnMount = (editor, m) => {
    editorRef.current = editor
    monacoRef.current = m
    setMounted(true)
    editor.addCommand(m.KeyMod.CtrlCmd | m.KeyCode.Enter, () => {
      onRunRef.current?.()
    })
    editor.addCommand(m.KeyMod.CtrlCmd | m.KeyMod.Shift | m.KeyCode.Enter, () => {
      onSubmitRef.current()
    })
    // JetBrains Mono comes from @fontsource and may still be loading at mount;
    // Monaco measures glyph widths once, so re-measure when fonts are ready.
    void document.fonts.ready.then(() => {
      if (!disposedRef.current) m.editor.remeasureFonts()
    })
  }

  const size = fontSize ?? (parseFloat(cssVar('--text-base')) || 13)

  return (
    <Editor
      className="code-editor"
      value={value}
      onChange={(v) => onChange(v ?? '')}
      language={MONACO_LANGUAGE_IDS[language]}
      theme={THEME_NAME}
      beforeMount={defineCockyTheme}
      onMount={handleMount}
      loading={null}
      height="100%"
      options={{
        tabSize: 4,
        fontFamily: cssVar('--font-mono'),
        fontSize: size,
        lineHeight: Math.round(size * 1.6),
        automaticLayout: true,
        minimap: { enabled: true },
        readOnly,
      }}
    />
  )
}

export default CodeEditor
