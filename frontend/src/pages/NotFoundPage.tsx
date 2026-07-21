import { Link } from 'react-router'

function NotFoundPage() {
  return (
    <>
      <p>페이지를 찾을 수 없습니다.</p>
      <Link to="/">목록으로</Link>
    </>
  )
}

export default NotFoundPage