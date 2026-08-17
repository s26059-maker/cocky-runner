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
        <pre>{submitError}</pre>
      </div>
    )
  }

  if (!result) {
    return null
  }

  const isAccepted = result.verdict === 'AC'
  // The backend deliberately never returns compiler/runtime error text in this
  // response (see JudgeResult's javadoc) - it only exposes the verdict and
  // aggregate counts, so there is no stderr/message to render here.
  const hasNoErrorDetail = result.verdict === 'RE' || result.verdict === 'ERROR'

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
      {hasNoErrorDetail && (
        <p className="submission-note">서버가 상세 에러 메시지를 제공하지 않습니다.</p>
      )}
    </div>
  )
}

export default SubmissionResult
