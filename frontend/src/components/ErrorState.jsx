import { Button } from './Button.jsx'

export function ErrorState({ title = 'Something threw a hiccup.', description, onRetry }) {
  return (
    <div className="surface-paper p-8 text-center">
      <div className="label-caps text-negative">Error</div>
      <h3 className="mt-3 font-editorial text-[24px] leading-tight text-ink">{title}</h3>
      {description && <p className="mt-2 text-[14px] text-muted">{description}</p>}
      {onRetry && (
        <div className="mt-5 flex justify-center">
          <Button variant="ink" onClick={onRetry}>
            Try again
          </Button>
        </div>
      )}
    </div>
  )
}
