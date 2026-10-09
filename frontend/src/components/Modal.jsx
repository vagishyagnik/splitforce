import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { cn } from '../util/cn.js'

export function Modal({ open, onClose, title, children, footer, size = 'md', testId }) {
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
  const sizeClass = size === 'sm' ? 'max-w-sm' : size === 'lg' ? 'max-w-2xl' : 'max-w-md'

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? 'modal-title' : undefined}
      data-testid={testId}
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center px-4 sm:px-6"
    >
      <div
        className="absolute inset-0 bg-ink/40 backdrop-blur-sm animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        className={cn(
          'relative w-full sm:w-auto sm:min-w-[420px] bg-cream rounded-t-card sm:rounded-card border border-hairline shadow-lift animate-scale-in',
          sizeClass,
        )}
      >
        {(title || onClose) && (
          <header className="flex items-center justify-between gap-3 px-6 pt-6">
            {title && <h3 id="modal-title" className="font-editorial text-[24px] leading-tight text-ink">{title}</h3>}
            {onClose && (
              <button
                type="button"
                onClick={onClose}
                aria-label="Close"
                className="inline-flex h-10 w-10 items-center justify-center rounded-full text-ink/70 hover:bg-ink/[0.06]"
              >
                <X size={18} />
              </button>
            )}
          </header>
        )}
        <div className="px-6 py-5">{children}</div>
        {footer && <footer className="px-6 pb-6">{footer}</footer>}
      </div>
    </div>,
    document.body,
  )
}
