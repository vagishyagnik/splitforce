import { cn } from '../util/cn.js'

const toneClasses = {
  neutral: 'bg-mist text-ink/80',
  coral: 'bg-coral-pale text-coral-hover',
  sage: 'bg-sage-pale text-emerald-positive',
  mustard: 'bg-mustard-pale text-mustard',
  ink: 'bg-ink text-cream',
  outline: 'border border-hairline text-ink/80',
  ghost: 'text-muted',
}

export function Pill({ tone = 'neutral', className, children, ...rest }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-semibold tracking-[0.14em] uppercase',
        toneClasses[tone],
        className,
      )}
      {...rest}
    >
      {children}
    </span>
  )
}

export function FilterPill({ active, onClick, children, testId }) {
  return (
    <button
      type="button"
      onClick={onClick}
      data-testid={testId}
      className={cn(
        'inline-flex h-11 items-center rounded-full px-4 text-[13px] font-semibold tracking-[0.06em] uppercase transition-colors',
        active
          ? 'bg-ink text-cream'
          : 'bg-cream text-ink border border-hairline hover:border-ink/40',
      )}
    >
      {children}
    </button>
  )
}
