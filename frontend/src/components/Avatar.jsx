import { cn } from '../util/cn.js'
import { initialsFor } from '../util/format.js'

const sizes = {
  xs: 'h-7 w-7 text-[11px]',
  sm: 'h-9 w-9 text-[13px]',
  md: 'h-11 w-11 text-[14px]',
  lg: 'h-14 w-14 text-[18px]',
  xl: 'h-20 w-20 text-[24px]',
}

// Deterministic warm color per name
const PALETTE = [
  ['#FFF0E7', '#E04826'],
  ['#E1F1E9', '#14805D'],
  ['#FFF2C9', '#B36804'],
  ['#F1ECE7', '#3A4854'],
  ['#FFE3DA', '#B23a1c'],
  ['#E5EDF6', '#1E4C7A'],
]

function pickPalette(seed) {
  let h = 0
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0
  return PALETTE[h % PALETTE.length]
}

export function Avatar({ name, picture, size = 'md', className, ring = false }) {
  const [bg, fg] = pickPalette(name || '?')
  const initials = initialsFor(name || '?')
  if (picture) {
    return (
      <img
        src={picture}
        alt={name}
        className={cn(
          'inline-flex items-center justify-center rounded-full object-cover border border-hairline',
          sizes[size],
          ring && 'ring-2 ring-cream',
          className,
        )}
      />
    )
  }
  return (
    <span
      aria-label={name}
      className={cn(
        'inline-flex items-center justify-center rounded-full font-semibold border border-hairline',
        sizes[size],
        ring && 'ring-2 ring-cream',
        className,
      )}
      style={{ background: bg, color: fg }}
    >
      {initials}
    </span>
  )
}

export function AvatarStack({ members, max = 4, size = 'sm' }) {
  const shown = members.slice(0, max)
  const remaining = members.length - shown.length
  return (
    <div className="flex -space-x-2">
      {shown.map((m, i) => (
        <Avatar
          key={m.userId || m.id || m.name + i}
          name={m.displayName || m.name || '?'}
          picture={m.picture}
          size={size}
          ring
        />
      ))}
      {remaining > 0 && (
        <span
          className={cn(
            'inline-flex items-center justify-center rounded-full font-semibold border border-hairline bg-cream text-muted ring-2 ring-cream',
            size === 'sm' ? 'h-9 w-9 text-[12px]' : 'h-11 w-11 text-[13px]',
          )}
        >
          +{remaining}
        </span>
      )}
    </div>
  )
}
