import type { SelectHTMLAttributes } from 'react'
import { cx } from './cx'
import './Select.css'

type SelectProps = SelectHTMLAttributes<HTMLSelectElement>

function Select({ className, ...rest }: SelectProps) {
  return <select {...rest} className={cx('select', className)} />
}

export default Select
