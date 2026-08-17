import type { SubmissionResponse, Verdict } from '../types'

const VERDICT_LABEL: Record<Verdict, string> = {
  AC: '정답 (Accepted)',
  WA: '오답 (Wrong Answer)',
  TLE: '시간 초과 (Time Limit Exceeded)',
  RE: '런타임 에러 (Runtime Error)',
  ERROR: '채점 실패 (Error)',
}

interface SubmissionResultProps {
  result: SubmissionResponse | null
  submitError: string | null
}

function SubmissionResult({ result, submitError }: SubmissionResultProps) {
  if (submitError) {
    return (
      <div className="submission-result submission-result--danger" role="alert">
        <p className="submission-verdict">제출 실패</p>
        <pre className="submission-error-output">{submitError}</pre>
      </div>
    )
  }

  if (!result) {
    return null
  }

  const isAccepted = result.verdict === 'AC'
  // The backend only ever fills errorOutput for RE/ERROR on a public sample case
  // (see JudgeResult's javadoc) - for a hidden-case failure, WA, or TLE it stays
  // null/undefined, and there is no stderr/message to render.
  const canHaveErrorDetail = result.verdict === 'RE' || result.verdict === 'ERROR'
  const errorOutput = result.errorOutput

  return (
    <div
      className={`submission-result ${isAccepted ? 'submission-result--success' : 'submission-result--danger'}`}
      role="status"
    >
      <p className="submission-verdict">
        {result.verdict} · {VERDICT_LABEL[result.verdict]}
      </p>
      <ul className="submission-meta">
        <li>
          통과: {result.passedCount} / {result.totalCount}
        </li>
        {result.failedCaseNumber != null && <li>실패한 테스트케이스: #{result.failedCaseNumber}</li>}
        <li>최대 실행 시간: {result.maxExecutionTimeMs}ms</li>
      </ul>
      {canHaveErrorDetail &&
        (errorOutput ? (
          <pre className="submission-error-output">{errorOutput}</pre>
        ) : (
          <p className="submission-note">서버가 상세 에러 메시지를 제공하지 않습니다.</p>
        ))}
    </div>
  )
}

export default SubmissionResult
