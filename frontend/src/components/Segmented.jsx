import { cn } from '../util/cn.js'

export function Segmented({ options, value, onChange, className, testId, stretch = false }) {
  return (
    <div
      role="tablist"
      data-testid={testId}
      className={cn(
        stretch ? 'flex' : 'inline-flex',
        'p-1 rounded-full bg-mist border border-hairline',
        className,
      )}
    >
      {options.map((o) => {
        const active = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(o.value)}
            className={cn(
              'inline-flex h-10 items-center justify-center rounded-full px-5 text-[13px] font-semibold transition-colors',
              stretch && 'flex-1 px-2',
              active
                ? 'bg-cream text-ink shadow-soft'
                : 'text-muted hover:text-ink',
            )}
          >
            {o.label}
          </button>
        )
      })}
    </div>
  )
}
