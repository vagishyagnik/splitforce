// A compact UPI mark: the signature two-tone (saffron + green) arrowheads next to a "UPI"
// wordmark. Drawn inline so it scales crisply and needs no asset. Decorative — callers that
// convey meaning with it should pass an accessible label on the surrounding element.
export function UpiLogo({ className = '', height = 16 }) {
  return (
    <svg
      viewBox="0 0 64 24"
      height={height}
      role="img"
      aria-label="UPI"
      className={className}
      style={{ width: 'auto' }}
    >
      {/* saffron arrowhead */}
      <path d="M2 2 L10 12 L2 22 L6.5 22 L14.5 12 L6.5 2 Z" fill="#E97626" />
      {/* green arrowhead, offset */}
      <path d="M10 2 L18 12 L10 22 L14.5 22 L22.5 12 L14.5 2 Z" fill="#5BA829" />
      {/* UPI wordmark */}
      <text
        x="27"
        y="17.5"
        fontFamily="ui-sans-serif, system-ui, sans-serif"
        fontSize="14"
        fontWeight="700"
        letterSpacing="0.5"
        fill="currentColor"
      >
        UPI
      </text>
    </svg>
  )
}
