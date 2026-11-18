import { cn } from '../util/cn.js'

export function Logo({ size = 32, className }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 100 100"
        aria-hidden="true"
        className="shrink-0"
      >
        {/* A pie with your share pulled out. */}
        <path d="M46 54 L46 14 A40 40 0 1 0 86 54 Z" fill="#0F1720" />
        <path d="M54 46 L54 6 A40 40 0 0 1 94 46 Z" fill="#FF5A36" />
      </svg>
    </span>
  )
}

export function Wordmark({ className }) {
  return (
    <span
      className={cn(
        'font-editorial text-[22px] leading-none tracking-tight text-ink',
        className,
      )}
    >
      Split<span className="italic">force</span>
    </span>
  )
}

export function BrandLockup({ className, size = 30 }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <Logo size={size} />
      <Wordmark />
    </span>
  )
}
