import type { ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { History, ListChecks } from 'lucide-react'
import './AppShell.css'

interface AppShellProps {
  children: ReactNode
}

/**
 * The IDE-window chrome around every route: a top bar, a left activity strip,
 * the routed page content, and a status bar. Purely presentational - it reads
 * the current route to highlight the active activity-strip item and to show a
 * route-derived label in the status bar, but never fetches data itself (that
 * stays inside the page components).
 */
function AppShell({ children }: AppShellProps) {
  const location = useLocation()
  const params = useParams<{ id?: string }>()

  const isProblemsSection = location.pathname === '/' || location.pathname.startsWith('/problems')

  const statusLabel =
    location.pathname === '/' ? '문제 목록' : params.id ? `문제 · ${params.id}` : location.pathname

  return (
    <div className="app-shell">
      <header className="app-shell__topbar">
        <span className="app-shell__brand">cocky-runner</span>
      </header>

      <nav className="activity-strip" aria-label="주 메뉴">
        <Link
          to="/"
          className={`activity-strip__item${isProblemsSection ? ' is-active' : ''}`}
          aria-label="문제 목록"
          aria-current={isProblemsSection ? 'page' : undefined}
        >
          <ListChecks size={20} strokeWidth={1.75} aria-hidden="true" />
        </Link>
        <button
          type="button"
          className="activity-strip__item is-disabled"
          aria-label="제출 내역 (준비 중)"
          disabled
        >
          <History size={20} strokeWidth={1.75} aria-hidden="true" />
        </button>
      </nav>

      <main className="app-shell__main">{children}</main>

      <footer className="app-shell__statusbar">
        <span className="app-shell__statusbar-left">{statusLabel}</span>
        <span className="app-shell__statusbar-right" />
      </footer>
    </div>
  )
}

export default AppShell
