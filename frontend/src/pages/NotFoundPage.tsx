import { Link } from 'react-router'

function NotFoundPage() {
  return (
    <div className="empty-state">
      <p className="page-status">페이지를 찾을 수 없습니다.</p>
      <Link to="/" className="btn btn--ghost">
        목록으로
      </Link>
    </div>
  )
}

export default NotFoundPage
