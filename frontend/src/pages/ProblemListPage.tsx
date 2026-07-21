import { Link } from 'react-router'
import { getProblems } from '../api/problems'
import { toErrorMessage } from '../api/client'
import { useAsyncData } from '../hooks/useAsyncData'

function ProblemListPage() {
  const { data: problems, error, loading } = useAsyncData(getProblems, [])

  if (loading) {
    return <p>불러오는 중...</p>
  }

  if (error) {
    return <p role="alert">문제 목록을 불러오지 못했습니다: {toErrorMessage(error)}</p>
  }

  if (!problems || problems.length === 0) {
    return <p>등록된 문제가 없습니다.</p>
  }

  return (
    <ul className="problem-list">
      {problems.map((problem) => (
        <li key={problem.id}>
          <Link to={`/problems/${problem.id}`}>{problem.title}</Link>
        </li>
      ))}
    </ul>
  )
}

export default ProblemListPage