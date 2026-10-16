import type { Diagnostic } from '../types'
import './DiagnosticList.css'

interface DiagnosticListProps {
  diagnostics: Diagnostic[] | undefined
  /** Clicking an entry jumps the editor to its line. */
  onSelect?: (diagnostic: Diagnostic) => void
}

/** "줄 3: SyntaxError: ..." list shown above a result's raw error text. Renders nothing when empty. */
function DiagnosticList({ diagnostics, onSelect }: DiagnosticListProps) {
  if (!diagnostics || diagnostics.length === 0) {
    return null
  }

  return (
    <ul className="diagnostic-list" aria-label="에러 위치">
      {diagnostics.map((d, i) => (
        <li key={i}>
          <button
            type="button"
            className={`diagnostic-list__item diagnostic-list__item--${d.severity.toLowerCase()}`}
            onClick={() => onSelect?.(d)}
            title="에디터에서 해당 줄로 이동"
          >
            줄 {d.line}: {d.message}
          </button>
        </li>
      ))}
    </ul>
  )
}

export default DiagnosticList
