import type { ButtonHTMLAttributes } from 'react'
import { cx } from './cx'
import './Button.css'

export type ButtonVariant = 'primary' | 'ghost'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
}

function Button({ variant = 'primary', className, ...rest }: ButtonProps) {
  return <button type="button" {...rest} className={cx('btn', `btn--${variant}`, className)} />
}

export default Button
