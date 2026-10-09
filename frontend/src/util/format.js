const CURRENCY_LOCALE = {
  INR: 'en-IN',
  USD: 'en-US',
  EUR: 'en-DE',
  GBP: 'en-GB',
  JPY: 'ja-JP',
  AUD: 'en-AU',
}

export function formatMoney(amount, currency = 'INR', opts = {}) {
  if (amount === null || amount === undefined || Number.isNaN(Number(amount))) {
    return '—'
  }
  const abs = Math.abs(Number(amount))
  const locale = CURRENCY_LOCALE[currency] || 'en-US'
  try {
    const s = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      minimumFractionDigits: currency === 'JPY' ? 0 : 2,
      maximumFractionDigits: currency === 'JPY' ? 0 : 2,
    }).format(abs)
    if (opts.signed && Number(amount) < 0) return `- ${s}`
    return s
  } catch {
    return `${currency} ${abs.toFixed(2)}`
  }
}

export function formatDate(iso, style = 'medium') {
  if (!iso) return ''
  const d = typeof iso === 'string' ? new Date(iso) : iso
  if (Number.isNaN(d.getTime())) return String(iso)
  if (style === 'short') {
    return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
  }
  if (style === 'long') {
    return d.toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })
  }
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

export function timeAgo(iso) {
  if (!iso) return '—'
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return String(iso)
  const now = Date.now()
  const diff = Math.round((now - then) / 1000)
  if (diff < 60) return 'just now'
  if (diff < 3600) return `${Math.round(diff / 60)} min ago`
  if (diff < 86400) return `${Math.round(diff / 3600)} hr ago`
  const days = Math.round(diff / 86400)
  if (days < 30) return `${days} day${days === 1 ? '' : 's'} ago`
  const months = Math.round(days / 30)
  if (months < 12) return `${months} mo ago`
  return `${Math.round(months / 12)} yr ago`
}

export function initialsFor(name = '') {
  const parts = String(name).trim().split(/\s+/)
  const a = parts[0]?.[0] || ''
  const b = parts[parts.length - 1]?.[0] || ''
  return (parts.length > 1 ? (a + b) : a).toUpperCase() || '·'
}

export function truncate(s, n = 40) {
  if (!s) return ''
  return s.length > n ? s.slice(0, n - 1) + '…' : s
}
