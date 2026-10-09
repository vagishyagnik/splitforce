import { useEffect, useMemo, useRef, useState } from 'react'
import { Input } from './Field.jsx'
import { Avatar } from './Avatar.jsx'
import { cn } from '../util/cn.js'

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function MemberEmailField({ value, onChange, contacts, usedEmails, invalid, testId }) {
  const [open, setOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  const wrapRef = useRef(null)

  const query = (value || '').trim().toLowerCase()
  const matches = useMemo(() => {
    if (!contacts?.length) return []
    return contacts
      .filter((c) => {
        const email = (c.email || '').toLowerCase()
        if (!email || usedEmails.has(email)) return false
        if (!query) return true
        const name = (c.name || '').toLowerCase()
        return email.includes(query) || name.includes(query)
      })
      .slice(0, 6)
  }, [contacts, query, usedEmails])

  useEffect(() => {
    if (!open) return
    const onDoc = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [open])

  useEffect(() => setHighlight(0), [query, open])

  const pick = (c) => {
    onChange(c.email)
    setOpen(false)
  }

  const onKeyDown = (e) => {
    if (!open || matches.length === 0) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setHighlight((h) => (h + 1) % matches.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight((h) => (h - 1 + matches.length) % matches.length)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      pick(matches[highlight])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div className="relative flex-1" ref={wrapRef}>
      <Input
        value={value}
        onChange={(e) => {
          onChange(e.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
        placeholder="Email"
        inputMode="email"
        type="email"
        aria-label="Member email"
        autoComplete="off"
        invalid={invalid}
        data-testid={testId}
      />
      {open && matches.length > 0 && (
        <div
          role="listbox"
          className="absolute z-20 mt-1 w-full rounded-input border border-hairline bg-cream shadow-soft overflow-hidden"
        >
          {matches.map((c, idx) => (
            <button
              type="button"
              key={c.userId || c.email}
              role="option"
              aria-selected={idx === highlight}
              onMouseDown={(e) => e.preventDefault()}
              onMouseEnter={() => setHighlight(idx)}
              onClick={() => pick(c)}
              className={cn(
                'w-full flex items-center gap-3 px-3 py-2 text-left transition-colors',
                idx === highlight ? 'bg-mist' : 'hover:bg-mist/60',
              )}
            >
              <Avatar name={c.name} picture={c.picture} size="sm" />
              <div className="min-w-0 flex-1">
                <div className="text-[14px] font-medium truncate">{c.name || c.email}</div>
                {c.name && (
                  <div className="text-[12px] text-muted truncate">{c.email}</div>
                )}
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
