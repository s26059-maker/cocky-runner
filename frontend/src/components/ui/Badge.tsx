import type { CSSProperties, ReactNode } from 'react'
import { cx } from './cx'
import './Badge.css'

export type BadgeTone = 'ac' | 'wa' | 'tle' | 're' | 'ce' | 'pending'

interface BadgeProps {
  tone: BadgeTone
  children: ReactNode
  className?: string
}

/**
 * A verdict-colored label. The tone maps to the --verdict-* token pair
 * (foreground bar color + low-saturation background) rather than a fixed set
 * of modifier classes, so adding a new tone is a token, not a new CSS rule.
 */
function Badge({ tone, children, className }: BadgeProps) {
  const style = {
    '--badge-color': `var(--verdict-${tone})`,
    '--badge-bg': `var(--verdict-${tone}-bg)`,
  } as CSSProperties

  return (
    <span className={cx('badge', className)} style={style}>
      {children}
    </span>
  )
}

export default Badge
