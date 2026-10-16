import type { Diagnostic, ExecutionResponse, ExecutionStatus } from '../types'
import DiagnosticList from './DiagnosticList'
import Badge, { type BadgeTone } from './ui/Badge'
import './ExecutionResult.css'

const STATUS_LABEL: Record<ExecutionStatus, string> = {
  SUCCESS: '성공',
  RUNTIME_ERROR: 'RE · 런타임 에러',
  TIMEOUT: 'TLE · 시간 초과',
  COMPILE_ERROR: 'CE · 컴파일 에러',
  ERROR: '실행 실패',
}

// Reuses the existing verdict tones (no new colors); ERROR is an infra failure,
// shown with the same tone SubmissionResult uses for it.
const STATUS_TONE: Record<ExecutionStatus, BadgeTone> = {
  SUCCESS: 'ac',
  RUNTIME_ERROR: 're',
  TIMEOUT: 'tle',
  COMPILE_ERROR: 'ce',
  ERROR: 'wa',
}

interface ExecutionResultProps {
  result: ExecutionResponse | null
  runError: string | null
  running?: boolean
  onSelectDiagnostic?: (diagnostic: Diagnostic) => void
}

function ExecutionResult({ result, runError, running = false, onSelectDiagnostic }: ExecutionResultProps) {
  if (running) {
    return (
      <div className="execution-result" role="status">
        <span className="execution-result__pending">실행 중...</span>
      </div>
    )
  }

  if (runError) {
    return (
      <div className="execution-result" role="alert">
        <div className="execution-result__header">
          <Badge tone="wa">실행 실패</Badge>
        </div>
        <pre className="execution-result__block">{runError}</pre>
      </div>
    )
  }

  if (!result) {
    return <p className="execution-result__empty">실행 결과가 여기에 표시됩니다.</p>
  }

  return (
    <div className="execution-result" role="status">
      <div className="execution-result__header">
        <Badge tone={STATUS_TONE[result.status]}>{STATUS_LABEL[result.status]}</Badge>
        <span className="execution-result__meta">
          exit {result.exitCode}
          {result.userWallMs != null && (
            <>
              {' · '}
              <span title={`컨테이너 기동 포함 ${result.executionTimeMs}ms`}>{result.userWallMs}ms</span>
            </>
          )}
        </span>
      </div>
      <DiagnosticList diagnostics={result.diagnostics} onSelect={onSelectDiagnostic} />
      <section>
        <h3 className="execution-result__label">stdout</h3>
        <pre className="execution-result__block">{result.stdout}</pre>
      </section>
      {result.stderr && (
        <section>
          <h3 className="execution-result__label">stderr</h3>
          <pre className="execution-result__block execution-result__block--stderr">{result.stderr}</pre>
        </section>
      )}
    </div>
  )
}

export default ExecutionResult
