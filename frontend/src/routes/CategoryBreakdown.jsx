import { useMemo, useState } from 'react'
import { Card, CardHeader } from '../components/Card.jsx'
import { Segmented } from '../components/Segmented.jsx'
import { Money } from '../components/Money.jsx'
import { categoryEmoji } from '../util/categories.js'
import { formatMoney } from '../util/format.js'
import { cn } from '../util/cn.js'

const UNCATEGORIZED = 'Uncategorized'

// Mirrors GroupController.rateToBase: unknown currencies fall back to 1.
function rateToBase(group, currency) {
  if (!currency || currency.toUpperCase() === group.baseCurrency) return 1
  const r = group.fxRates?.[currency.toUpperCase()]
  return r == null ? 1 : Number(r)
}

function myId(group) {
  return group.members.find((m) => m.you)?.userId
}

// Per-category totals in base currency. `mine` is what I consumed, not what I
// paid: shares are net (paid - owed), so owed = paid - net.
export function categoryBreakdown(group) {
  const me = myId(group)
  const byCat = new Map()
  for (const e of group.expenses || []) {
    if (e.payment || e.category === 'Payment') continue
    const rate = rateToBase(group, e.currency)
    const cost = Number(e.cost || 0) * rate
    const paidByMe = e.paidByUserId === me ? cost : 0
    const mine = paidByMe - Number(e.myNet || 0) * rate
    const key = e.category || UNCATEGORIZED
    const row = byCat.get(key) || { category: key, total: 0, mine: 0, count: 0 }
    row.total += cost
    row.mine += mine
    row.count += 1
    byCat.set(key, row)
  }
  return [...byCat.values()]
}

export default function CategoryBreakdownCard({ group, selected, onSelect }) {
  const [view, setView] = useState('total')
  const rows = useMemo(() => categoryBreakdown(group), [group])

  const visible = useMemo(
    () =>
      rows
        .filter((r) => Math.abs(r[view]) >= 0.005)
        .sort((a, b) => b[view] - a[view]),
    [rows, view],
  )
  const sum = visible.reduce((acc, r) => acc + r[view], 0)
  const max = visible.reduce((acc, r) => Math.max(acc, r[view]), 0)
  const currency = group.baseCurrency

  return (
    <Card className="p-6 sm:p-7" data-testid="category-breakdown-card">
      <CardHeader
        eyebrow="Where the money went"
        title="By category"
        right={
          <Segmented
            value={view}
            onChange={setView}
            testId="category-breakdown-view"
            options={[
              { value: 'total', label: 'Group' },
              { value: 'mine', label: 'My share' },
            ]}
          />
        }
      />

      {visible.length === 0 ? (
        <p className="mt-6 text-[14px] text-muted">
          {view === 'mine' ? 'Nothing on your tab yet.' : 'No expenses yet.'}
        </p>
      ) : (
        <>
          <div className="mt-5 flex items-baseline justify-between gap-3">
            <span className="label-caps">{view === 'mine' ? 'Your share' : group.type === 'trip' ? 'Trip total' : 'Group total'}</span>
            <Money value={sum} currency={currency} size="lg" />
          </div>
          <ul className="mt-4 space-y-1">
            {visible.map((r) => {
              const value = r[view]
              const pct = sum > 0 ? (value / sum) * 100 : 0
              const width = max > 0 ? Math.max((value / max) * 100, 1) : 0
              const active = selected === r.category
              const filterable = r.category !== UNCATEGORIZED
              return (
                <li key={r.category}>
                  <button
                    type="button"
                    disabled={!filterable}
                    onClick={() => onSelect?.(active ? '' : r.category)}
                    aria-pressed={active}
                    title={`${r.category} · ${r.count} expense${r.count === 1 ? '' : 's'} · ${formatMoney(value, currency)} · ${pct.toFixed(1)}%`}
                    data-testid={`category-breakdown-row-${r.category}`}
                    className={cn(
                      'w-full rounded-input px-3 py-2.5 text-left transition-colors',
                      filterable && 'hover:bg-mist',
                      active && 'bg-mist ring-1 ring-ink/20',
                    )}
                  >
                    <div className="flex items-center gap-2.5">
                      <span aria-hidden className="w-5 text-center text-[15px]">
                        {categoryEmoji(r.category)}
                      </span>
                      <span className="min-w-0 flex-1 truncate text-[14px] font-medium text-ink">
                        {r.category}
                      </span>
                      <span className="text-[12px] text-muted">{pct.toFixed(0)}%</span>
                      <Money value={value} currency={currency} size="sm" />
                    </div>
                    <div className="mt-1.5 ml-[30px] flex items-center gap-2">
                      <div className="h-1.5 flex-1 rounded-full bg-hairline">
                        <div
                          className="h-full rounded-full bg-coral-hover"
                          style={{ width: `${width}%` }}
                        />
                      </div>
                      <span className="w-14 text-right text-[11.5px] text-muted">
                        {r.count} exp
                      </span>
                    </div>
                  </button>
                </li>
              )
            })}
          </ul>
        </>
      )}
    </Card>
  )
}
