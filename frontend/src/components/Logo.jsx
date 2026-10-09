import { cn } from '../util/cn.js'

export function Logo({ size = 32, className }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 40 40"
        aria-hidden="true"
        className="shrink-0"
      >
        <circle cx="20" cy="20" r="18" fill="#0F1720" />
        <path d="M13 24 L20 12 L27 24 Z" fill="#FF5A36" />
        <circle cx="20" cy="24" r="2.2" fill="#FAF8F5" />
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
