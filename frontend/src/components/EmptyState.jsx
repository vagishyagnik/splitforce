import { cn } from '../util/cn.js'

export function EmptyState({ eyebrow, title, description, action, className, illustration }) {
  return (
    <div
      className={cn(
        'surface-paper flex flex-col items-center text-center px-6 py-14 sm:py-20',
        className,
      )}
    >
      {illustration ?? <EmptyOrnament />}
      {eyebrow && <div className="label-caps mt-6">{eyebrow}</div>}
      {title && (
        <h3 className="mt-3 font-editorial text-[28px] sm:text-[36px] leading-[1.05] text-ink max-w-md">
          {title}
        </h3>
      )}
      {description && (
        <p className="mt-3 text-[15px] text-muted max-w-md">{description}</p>
      )}
      {action && <div className="mt-6">{action}</div>}
    </div>
  )
}

function EmptyOrnament() {
  return (
    <svg width="88" height="66" viewBox="0 0 88 66" aria-hidden="true" className="text-ink/70">
      <circle cx="18" cy="34" r="14" fill="#FFF0E7" />
      <circle cx="18" cy="34" r="14" fill="none" stroke="#0F1720" strokeWidth="1.2" />
      <rect x="34" y="14" width="42" height="34" rx="10" fill="#F1ECE7" stroke="#0F1720" strokeWidth="1.2" />
      <path d="M40 26 h30 M40 34 h20 M40 42 h14" stroke="#0F1720" strokeOpacity="0.5" strokeWidth="1.2" strokeLinecap="round" />
    </svg>
  )
}
