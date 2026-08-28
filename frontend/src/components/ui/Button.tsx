import type { ButtonHTMLAttributes } from 'react'
import { cx } from './cx'
import './Button.css'

export type ButtonVariant = 'primary' | 'ghost'
/** 'md' (default) is the normal button size; 'sm' fits a 24px row - a Panel header, alongside Select. */
export type ButtonSize = 'md' | 'sm'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
}

function Button({ variant = 'primary', size = 'md', className, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      className={cx('btn', `btn--${variant}`, size === 'sm' && 'btn--sm', className)}
    />
  )
}

export default Button
