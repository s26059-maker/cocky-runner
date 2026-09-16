import { useMemo } from 'react'
import CodeMirror, { EditorView, Prec, keymap } from '@uiw/react-codemirror'
import { python } from '@codemirror/lang-python'
import { cpp } from '@codemirror/lang-cpp'
import { java } from '@codemirror/lang-java'
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags as t } from '@lezer/highlight'
import './CodeEditor.css'

// Mirrors the backend's supported languages (see com.cocky.cockyrunner.domain.Language) -
// both the enum names themselves (uppercase, matching what SubmissionController's
// parseLanguage()/Language.valueOf() expect on the wire) and the CodeMirror language
// support for each. Add a case here (and the matching @codemirror/lang-* extension)
// if the backend ever grows a third language.
const LANGUAGE_EXTENSIONS = {
  C: [cpp()],
  PYTHON: [python()],
  JAVA: [java()],
} as const

export type SupportedLanguage = keyof typeof LANGUAGE_EXTENSIONS

export const SUPPORTED_LANGUAGES: SupportedLanguage[] = Object.keys(LANGUAGE_EXTENSIONS) as SupportedLanguage[]

// Starter skeleton inserted when the editor is empty (see ProblemDetailPage) -
// never inserted over code the user has already written. Python gets none;
// there's no single unambiguous "empty program" skeleton worth presupposing.
export const LANGUAGE_TEMPLATES: Record<SupportedLanguage, string> = {
  C: '#include <stdio.h>\n\nint main(void) {\n    \n    return 0;\n}\n',
  PYTHON: '',
  JAVA: 'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n    }\n}\n',
}

// Chrome (background, gutters, caret, selection, active line) built entirely
// from the app's design tokens. Passed alongside theme="none" on <CodeMirror>
// below so none of @uiw/react-codemirror's bundled default theme/highlighting
// leaks in underneath this one. Language-independent, so - like syntaxTheme
// below - this is a module-level constant, created once, not per render or
// per language.
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
// keyword / string / number / comment / function name. Also language-
// independent (Lezer's highlight tags are shared across grammars), so this
// too is a module-level constant.
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
  /**
   * Fired on Ctrl+Enter / Cmd+Enter ("Mod-Enter", CodeMirror's cross-platform
   * modifier - resolves to Ctrl on Windows/Linux and Cmd on Mac on its own,
   * no manual platform check needed). Whether this is currently a valid
   * submission (blank code, already submitting, etc.) is entirely the
   * caller's concern - this just forwards the key press unconditionally.
   *
   * Registered at Prec.highest - see the comment at its definition below for
   * why that's load-bearing, not decorative.
   */
  onSubmit: () => void
}

function CodeEditor({ value, onChange, language, readOnly = false, onSubmit }: CodeEditorProps) {
  // Recomputed only when the language (or, in principle, onSubmit) actually
  // changes - not on every keystroke - so editorTheme/syntaxTheme's stability
  // as module constants isn't undone by rebuilding the array around them.
  const extensions = useMemo(() => {
    // Prec.highest is required, not optional polish: @codemirror/commands'
    // defaultKeymap (pulled in by basicSetup, which @uiw/react-codemirror
    // places ahead of this component's own `extensions` prop in the final
    // combined list) already binds Mod-Enter to insertBlankLine. CodeMirror
    // tries keymaps in precedence order and stops at the first handler that
    // returns true ("the ones specified early or with high priority get
    // checked first" - @codemirror/view's own doc comment on the keymap
    // facet) - so without Prec.highest, defaultKeymap's binding wins every
    // time (silently inserting a newline) and this one is never reached,
    // regardless of where it sits in the extensions array.
    const submitKeymap = Prec.highest(
      keymap.of([
        {
          key: 'Mod-Enter',
          run: () => {
            onSubmit()
            return true
          },
        },
      ]),
    )
    return [...LANGUAGE_EXTENSIONS[language], submitKeymap, editorTheme, syntaxTheme]
  }, [language, onSubmit])

  return (
    <CodeMirror
      className="code-editor"
      value={value}
      onChange={onChange}
      extensions={extensions}
      theme="none"
      readOnly={readOnly}
      height="100%"
      basicSetup={{ tabSize: 4 }}
    />
  )
}

export default CodeEditor
