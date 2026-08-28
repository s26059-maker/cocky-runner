import type { ReactNode } from 'react'
import { cx } from './cx'
import './Panel.css'

interface PanelProps {
  title?: ReactNode
  headerRight?: ReactNode
  children: ReactNode
  className?: string
}

/** A bordered container with an optional 24px header bar and a body region. */
function Panel({ title, headerRight, children, className }: PanelProps) {
  const hasHeader = title != null || headerRight != null

  return (
    <div className={cx('panel', className)}>
      {hasHeader && (
        <div className="panel__header">
          {title != null && <span className="panel__title">{title}</span>}
          {headerRight != null && <div className="panel__header-right">{headerRight}</div>}
        </div>
      )}
      <div className="panel__body">{children}</div>
    </div>
  )
}

export default Panel
