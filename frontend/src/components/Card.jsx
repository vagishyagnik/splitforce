import { cn } from '../util/cn.js'

export function Card({ as: Comp = 'section', className, children, tone = 'paper', ...rest }) {
  const tones = {
    paper: 'surface-paper',
    mist: 'surface-mist',
    coral: 'surface-coral',
    ink: 'surface-ink',
    plain: 'bg-cream border border-hairline rounded-card',
  }
  return (
    <Comp className={cn(tones[tone], className)} {...rest}>
      {children}
    </Comp>
  )
}

export function CardHeader({ eyebrow, title, subtitle, right, className }) {
  return (
    <header className={cn('flex flex-wrap items-start justify-between gap-4', className)}>
      <div>
        {eyebrow && <div className="label-caps mb-2">{eyebrow}</div>}
        {title && <h2 className="font-editorial text-[26px] sm:text-[32px] leading-[1.05] text-ink">{title}</h2>}
        {subtitle && <p className="mt-1.5 text-[15px] text-muted">{subtitle}</p>}
      </div>
      {right}
    </header>
  )
}
