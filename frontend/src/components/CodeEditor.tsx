import CodeMirror, { EditorView } from '@uiw/react-codemirror'
import { python } from '@codemirror/lang-python'

// Mirrors the backend's supported languages (see com.cocky.cockyrunner.domain.Language).
// Only python is defined server-side today; add entries here (and the matching
// @codemirror/lang-* extension) if the backend ever grows more.
const LANGUAGE_EXTENSIONS = {
  python: [python()],
} as const

export type SupportedLanguage = keyof typeof LANGUAGE_EXTENSIONS

export const SUPPORTED_LANGUAGES: SupportedLanguage[] = Object.keys(LANGUAGE_EXTENSIONS) as SupportedLanguage[]

// A single theme built from the app's existing design tokens (index.css), so the
// editor follows the same light/dark switching as the rest of the page without a
// separate toggle.
const appTheme = EditorView.theme({
  '&': {
    backgroundColor: 'var(--code-bg)',
    color: 'var(--text-h)',
    fontSize: '14px',
    border: '1px solid var(--border)',
    borderRadius: '6px',
  },
  '.cm-content': {
    fontFamily: 'var(--mono)',
    caretColor: 'var(--text-h)',
  },
  '.cm-gutters': {
    backgroundColor: 'var(--code-bg)',
    color: 'var(--text)',
    border: 'none',
  },
  '.cm-activeLine, .cm-activeLineGutter': {
    backgroundColor: 'var(--accent-bg)',
  },
  '&.cm-focused': {
    outline: '1px solid var(--accent-border)',
  },
})

interface CodeEditorProps {
  value: string
  onChange: (value: string) => void
  language: SupportedLanguage
  readOnly?: boolean
}

function CodeEditor({ value, onChange, language, readOnly = false }: CodeEditorProps) {
  return (
    <CodeMirror
      value={value}
      onChange={onChange}
      extensions={[...LANGUAGE_EXTENSIONS[language]]}
      theme={appTheme}
      readOnly={readOnly}
      height="360px"
      basicSetup={{ tabSize: 4 }}
    />
  )
}

export default CodeEditor
