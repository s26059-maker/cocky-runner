import CodeMirror, { EditorView } from '@uiw/react-codemirror'
import { python } from '@codemirror/lang-python'
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags as t } from '@lezer/highlight'
import './CodeEditor.css'

// Mirrors the backend's supported languages (see com.cocky.cockyrunner.domain.Language).
// Only python is defined server-side today; add entries here (and the matching
// @codemirror/lang-* extension) if the backend ever grows more.
const LANGUAGE_EXTENSIONS = {
  python: [python()],
} as const

export type SupportedLanguage = keyof typeof LANGUAGE_EXTENSIONS

export const SUPPORTED_LANGUAGES: SupportedLanguage[] = Object.keys(LANGUAGE_EXTENSIONS) as SupportedLanguage[]

// Chrome (background, gutters, caret, selection, active line) built entirely
// from the app's design tokens. Passed alongside theme="none" on <CodeMirror>
// below so none of @uiw/react-codemirror's bundled default theme/highlighting
// leaks in underneath this one.
const editorTheme = EditorView.theme(
  {
    '&': {
      height: '100%',
      backgroundColor: 'var(--bg-editor)',
      color: 'var(--text-primary)',
    },
    '.cm-content': {
      fontFamily: 'var(--font-mono)',
      fontSize: 'var(--text-base)',
      lineHeight: '1.6',
      caretColor: 'var(--accent)',
    },
    '.cm-cursor, .cm-dropCursor': {
      borderLeftColor: 'var(--accent)',
    },
    '.cm-scroller': {
      overflow: 'auto',
    },
    '&.cm-focused': {
      outline: 'none',
    },
    '.cm-gutters': {
      backgroundColor: 'var(--bg-editor)',
      color: 'var(--text-muted)',
      border: 'none',
    },
    '.cm-lineNumbers .cm-activeLineGutter': {
      backgroundColor: 'transparent',
      color: 'var(--text-secondary)',
    },
    // Deliberately subtle - a barely-there lightening off --bg-editor itself
    // (not --bg-hover, which is a much bigger jump meant for hover/selection
    // states elsewhere), not the loud highlight CodeMirror's default theme
    // uses for the active line.
    '.cm-activeLine': {
      backgroundColor: 'var(--bg-editor-active-line)',
    },
    '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
      backgroundColor: 'var(--accent-muted) !important',
    },
  },
  { dark: true },
)

// Syntax colors - the --syntax-* palette (src/styles/tokens.css), kept
// separate from --verdict-* so a string literal is never visually confused
// with a verdict badge elsewhere in the UI. Five categories, as specified:
// keyword / string / number / comment / function name.
const syntaxTheme = syntaxHighlighting(
  HighlightStyle.define([
    { tag: [t.keyword, t.controlKeyword, t.moduleKeyword, t.operatorKeyword], color: 'var(--syntax-keyword)' },
    { tag: [t.function(t.variableName), t.function(t.propertyName)], color: 'var(--syntax-function)' },
    { tag: [t.number, t.bool, t.null], color: 'var(--syntax-number)' },
    { tag: [t.string, t.special(t.string)], color: 'var(--syntax-string)' },
    { tag: [t.comment, t.lineComment, t.blockComment], color: 'var(--syntax-comment)', fontStyle: 'italic' },
  ]),
)

interface CodeEditorProps {
  value: string
  onChange: (value: string) => void
  language: SupportedLanguage
  readOnly?: boolean
}

function CodeEditor({ value, onChange, language, readOnly = false }: CodeEditorProps) {
  return (
    <CodeMirror
      className="code-editor"
      value={value}
      onChange={onChange}
      extensions={[...LANGUAGE_EXTENSIONS[language], editorTheme, syntaxTheme]}
      theme="none"
      readOnly={readOnly}
      height="100%"
      basicSetup={{ tabSize: 4 }}
    />
  )
}

export default CodeEditor
