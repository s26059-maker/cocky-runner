import { Link, useParams } from 'react-router'
import { getProblem } from '../api/problems'
import { ApiError, toErrorMessage } from '../api/client'
import { useAsyncData } from '../hooks/useAsyncData'

function ProblemDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: problem, error, loading } = useAsyncData(() => getProblem(id!), [id])

  if (loading) {
    return <p>불러오는 중...</p>
  }

  if (error) {
    if (error instanceof ApiError && error.isNotFound) {
      return (
        <>
          <p>문제를 찾을 수 없습니다.</p>
          <Link to="/">목록으로</Link>
        </>
      )
    }
    return <p role="alert">문제를 불러오지 못했습니다: {toErrorMessage(error)}</p>
  }

  if (!problem) {
    return null
  }

  return (
    <div className="problem-detail">
      <Link to="/">목록으로</Link>
      <h1>{problem.title}</h1>
      <p>제한 시간: {problem.timeLimitMs}ms</p>
      <p className="description">{problem.description}</p>

      {problem.sampleTestCases.map((sample, index) => (
        <div className="sample" key={index}>
          <h2>예제 {index + 1}</h2>
          <div className="sample-io">
            <div>
              <h3>입력</h3>
              <pre>{sample.input}</pre>
            </div>
            <div>
              <h3>예상 출력</h3>
              <pre>{sample.expectedOutput}</pre>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

export default ProblemDetailPage