import { cn } from '../util/cn.js'
import { formatMoney } from '../util/format.js'

export function Money({ value, currency, size = 'md', tone, showCode = false, className }) {
  const s = formatMoney(value, currency, { signed: false })
  const sizeClass =
    size === 'xl'
      ? 'text-[40px] sm:text-[56px] leading-none'
      : size === 'lg'
      ? 'text-2xl leading-tight'
      : size === 'sm'
      ? 'text-[13px]'
      : 'text-[15px]'
  const toneClass =
    tone === 'positive'
      ? 'text-emerald-positive'
      : tone === 'negative'
      ? 'text-negative'
      : tone === 'muted'
      ? 'text-muted'
      : 'text-ink'
  return (
    <span className={cn('mono font-semibold', sizeClass, toneClass, className)}>
      {s}
      {showCode && currency && (
        <span className="ml-2 label-caps mono-none">{currency}</span>
      )}
    </span>
  )
}
