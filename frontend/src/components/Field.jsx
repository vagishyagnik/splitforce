import { forwardRef } from 'react'
import { cn } from '../util/cn.js'

export function Label({ children, htmlFor, className, hint }) {
  return (
    <label htmlFor={htmlFor} className={cn('block', className)}>
      <span className="label-caps">{children}</span>
      {hint && <span className="ml-2 text-xs font-normal text-muted normal-case tracking-normal">{hint}</span>}
    </label>
  )
}

export const Input = forwardRef(function Input({ className, invalid, ...rest }, ref) {
  return (
    <input
      ref={ref}
      className={cn(
        'w-full min-h-[48px] rounded-input border bg-cream px-4 py-3 text-[15px] text-ink placeholder:text-muted/70 transition-colors',
        invalid ? 'border-negative/60' : 'border-hairline hover:border-ink/30 focus:border-ink/50',
        className,
      )}
      {...rest}
    />
  )
})

export const Textarea = forwardRef(function Textarea({ className, invalid, ...rest }, ref) {
  return (
    <textarea
      ref={ref}
      className={cn(
        'w-full min-h-[112px] rounded-input border bg-cream px-4 py-3 text-[15px] text-ink placeholder:text-muted/70 transition-colors resize-y',
        invalid ? 'border-negative/60' : 'border-hairline hover:border-ink/30 focus:border-ink/50',
        className,
      )}
      {...rest}
    />
  )
})

export const Select = forwardRef(function Select({ className, children, ...rest }, ref) {
  return (
    <div className="relative">
      <select
        ref={ref}
        className={cn(
          'w-full min-h-[48px] rounded-input border border-hairline bg-cream pl-4 pr-11 py-3 text-[15px] text-ink appearance-none transition-colors hover:border-ink/30 focus:border-ink/50',
          className,
        )}
        {...rest}
      >
        {children}
      </select>
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M6 9l6 6 6-6" />
      </svg>
    </div>
  )
})

export function FieldError({ children }) {
  if (!children) return null
  return (
    <p className="mt-2 text-sm text-negative">
      {children}
    </p>
  )
}

export function Helper({ children }) {
  if (!children) return null
  return <p className="mt-2 text-[13px] text-muted">{children}</p>
}
