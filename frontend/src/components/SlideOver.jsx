import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { cn } from '../util/cn.js'

export function SlideOver({ open, onClose, title, subtitle, children, footer, testId }) {
  useEffect(() => {
    if (!open) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKey = (e) => {
      if (e.key === 'Escape') onClose?.()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.body.style.overflow = prev
      document.removeEventListener('keydown', onKey)
    }
  }, [open, onClose])

  if (!open) return null

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? 'slide-title' : undefined}
      data-testid={testId}
      className="fixed inset-0 z-50"
    >
      <div
        className="absolute inset-0 bg-ink/40 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <aside
        className={cn(
          'absolute inset-y-0 right-0 flex flex-col bg-cream shadow-lift border-l border-hairline',
          'w-full sm:max-w-md',
          'animate-slide-in-right',
        )}
      >
        <header className="hairline-b px-5 sm:px-7 py-5 flex items-start justify-between gap-4">
          <div>
            <div className="label-caps">Settings</div>
            {title && (
              <h2 id="slide-title" className="mt-1 font-editorial text-[28px] leading-tight text-ink">
                {title}
              </h2>
            )}
            {subtitle && <p className="mt-1 text-[14px] text-muted">{subtitle}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close settings"
            className="inline-flex h-10 w-10 items-center justify-center rounded-full text-ink/70 hover:bg-ink/[0.06]"
          >
            <X size={18} />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-5 sm:px-7 py-6">{children}</div>
        {footer && <footer className="hairline-t px-5 sm:px-7 py-4">{footer}</footer>}
      </aside>
    </div>,
    document.body,
  )
}
