import { forwardRef } from 'react'
import { cn } from '../util/cn.js'

const base = 'inline-flex items-center justify-center gap-2 font-medium rounded-full transition-[transform,box-shadow,background-color,color] duration-200 select-none whitespace-nowrap disabled:opacity-45 disabled:cursor-not-allowed active:translate-y-[1px]'

const sizes = {
  sm: 'h-10 px-4 text-sm min-w-[44px]',
  md: 'h-11 px-5 text-[15px] min-w-[44px]',
  lg: 'h-14 px-7 text-base min-w-[44px]',
}

const variants = {
  primary: 'bg-coral text-cream shadow-soft hover:bg-coral-hover hover:shadow-lift',
  ink: 'bg-ink text-cream shadow-soft hover:shadow-lift hover:bg-[#1a2431]',
  outline: 'bg-cream text-ink border border-hairline hover:border-ink/40 hover:shadow-soft',
  ghost: 'bg-transparent text-ink hover:bg-ink/[0.04]',
  quiet: 'bg-mist text-ink hover:bg-[#e6e0d9]',
  danger: 'bg-negative/10 text-negative border border-negative/25 hover:bg-negative/15',
}

export const Button = forwardRef(function Button(
  { as: Comp = 'button', variant = 'primary', size = 'md', className, children, ...rest },
  ref,
) {
  return (
    <Comp
      ref={ref}
      className={cn(base, sizes[size], variants[variant], className)}
      {...rest}
    >
      {children}
    </Comp>
  )
})

export function IconButton({ as: Comp = 'button', className, children, label, ...rest }) {
  return (
    <Comp
      aria-label={label}
      className={cn(
        'inline-flex h-11 w-11 items-center justify-center rounded-full text-ink transition-colors hover:bg-ink/[0.06]',
        className,
      )}
      {...rest}
    >
      {children}
    </Comp>
  )
}
