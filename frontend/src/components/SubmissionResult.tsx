import type { SubmissionResponse } from '../types'
import Badge from './ui/Badge'
import { VERDICT_LABEL, VERDICT_TONE } from './verdictDisplay'
import './SubmissionResult.css'

function timeTooltip(result: SubmissionResponse): string {
  const total = `컨테이너 기동 포함 ${result.maxExecutionTimeMs}ms`
  if (result.userCpuMs == null) {
    return total
  }
  // CPU time is measured in 10ms ticks, so 0 means "under 10ms", not "unused".
  const cpu = result.userCpuMs === 0 ? '<10ms' : `${result.userCpuMs}ms`
  return `CPU ${cpu} · ${total}`
}

interface SubmissionResultProps {
  result: SubmissionResponse | null
  submitError: string | null
  submitting?: boolean
}

function SubmissionResult({ result, submitError, submitting = false }: SubmissionResultProps) {
  if (submitting) {
    return (
      <div className="submission-result submission-result--pending" role="status">
        <span className="submission-result__pending-text">채점 중...</span>
      </div>
    )
  }

  if (submitError) {
    return (
      <div className="submission-result" role="alert">
        <div className="submission-result__header">
          <Badge tone="wa">제출 실패</Badge>
        </div>
        <pre className="submission-result__error">{submitError}</pre>
      </div>
    )
  }

  if (!result) {
    return null
  }

  // The backend fills errorOutput for CE always (a compile error is about the
  // submitted code, not test data), and for RE/ERROR only on a public sample
  // case - see JudgeResult's javadoc on the backend. For a hidden-case RE/
  // ERROR, WA, or TLE it stays null and there's nothing to render.
  const canHaveErrorDetail = result.verdict === 'CE' || result.verdict === 'RE' || result.verdict === 'ERROR'
  const errorOutput = result.errorOutput

  return (
    <div className="submission-result" role="status">
      <div className="submission-result__header">
        <Badge tone={VERDICT_TONE[result.verdict]}>
          {result.verdict} · {VERDICT_LABEL[result.verdict]}
        </Badge>
        <span className="submission-result__meta">
          {result.passedCount}/{result.totalCount}
          {result.userWallMs != null && (
            <>
              {' · '}
              <span title={timeTooltip(result)}>{result.userWallMs}ms</span>
            </>
          )}
          {result.failedCaseNumber != null && ` · #${result.failedCaseNumber}`}
        </span>
      </div>
      {canHaveErrorDetail &&
        (errorOutput ? (
          <pre className="submission-result__error">{errorOutput}</pre>
        ) : (
          <p className="submission-result__note">서버가 상세 에러 메시지를 제공하지 않습니다.</p>
        ))}
    </div>
  )
}

export default SubmissionResult
