import { Link } from 'react-router'
import { getProblems } from '../api/problems'
import { toErrorMessage } from '../api/client'
import { useAsyncData } from '../hooks/useAsyncData'
import './ProblemListPage.css'

function ProblemListPage() {
  const { data: problems, error, loading } = useAsyncData(getProblems, [])

  if (loading) {
    return <p className="page-status">불러오는 중...</p>
  }

  if (error) {
    return (
      <p className="page-status" role="alert">
        문제 목록을 불러오지 못했습니다: {toErrorMessage(error)}
      </p>
    )
  }

  if (!problems || problems.length === 0) {
    return <p className="page-status">등록된 문제가 없습니다.</p>
  }

  return (
    <div className="problem-table" role="table" aria-label="문제 목록">
      <div className="problem-table__header" role="row">
        <span role="columnheader">ID</span>
        <span role="columnheader">제목</span>
      </div>
      {problems.map((problem) => (
        <Link key={problem.id} to={`/problems/${problem.id}`} className="problem-row" role="row">
          <span className="problem-row__id" role="cell">
            {problem.id}
          </span>
          <span className="problem-row__title" role="cell">
            {problem.title}
          </span>
        </Link>
      ))}
    </div>
  )
}

export default ProblemListPage
