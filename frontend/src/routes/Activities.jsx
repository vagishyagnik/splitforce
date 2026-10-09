import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Search, Plus, ArrowUpRight } from 'lucide-react'
import { useActivities } from '../api/hooks.js'
import { Button } from '../components/Button.jsx'
import { Card } from '../components/Card.jsx'
import { Input } from '../components/Field.jsx'
import { FilterPill, Pill } from '../components/Pill.jsx'
import { EmptyState } from '../components/EmptyState.jsx'
import { ErrorState } from '../components/ErrorState.jsx'
import { GroupCardSkeleton } from '../components/Skeleton.jsx'
import { GroupTypeTag } from '../components/GroupTypeTag.jsx'
import { Money } from '../components/Money.jsx'
import { timeAgo } from '../util/format.js'

const FILTERS = [
  { key: 'all', label: 'All' },
  { key: 'trip', label: 'Trips' },
  { key: 'home', label: 'Homes' },
  { key: 'general', label: 'General' },
]

export default function Activities() {
  const { data, isLoading, isError, refetch } = useActivities()
  const [filter, setFilter] = useState('all')
  const [q, setQ] = useState('')

  const filtered = useMemo(() => {
    const arr = Array.isArray(data) ? data : []
    return arr.filter((g) => {
      if (filter !== 'all' && g.type !== filter) return false
      if (q && !g.name.toLowerCase().includes(q.toLowerCase())) return false
      return true
    })
  }, [data, filter, q])

  return (
    <div className="animate-fade-up">
      <ActivitiesHeader />

      <div className="mt-8 flex flex-col-reverse md:flex-row md:items-center md:justify-between gap-4">
        <div className="flex items-center gap-2 overflow-x-auto hide-scrollbar -mx-4 px-4 md:mx-0 md:px-0">
          {FILTERS.map((f) => (
            <FilterPill
              key={f.key}
              active={filter === f.key}
              onClick={() => setFilter(f.key)}
              testId={`activities-filter-${f.key}`}
            >
              {f.label}
            </FilterPill>
          ))}
        </div>
        <div className="relative md:w-72">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search groups"
            className="pl-10"
            data-testid="activities-search-input"
            aria-label="Search groups"
          />
        </div>
      </div>

      <div className="mt-8">
        {isLoading ? (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <GroupCardSkeleton key={i} />
            ))}
          </div>
        ) : isError ? (
          <ErrorState
            title="We couldn't load your activities."
            description="Please check your connection and try again."
            onRetry={() => refetch()}
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            eyebrow={data?.length ? 'Nothing here' : 'A blank page'}
            title={
              data?.length
                ? 'Nothing in this view yet.'
                : 'Your first shared space is one click away.'
            }
            description={
              data?.length
                ? 'Try another filter, or clear your search.'
                : 'Add a trip, a home, or something in between. You can invite people later.'
            }
            action={
              <Button as={Link} to="/app/create" data-testid="activities-empty-create-group-button">
                <Plus size={16} /> Create a group
              </Button>
            }
          />
        ) : (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {filtered.map((g) => (
              <GroupCard key={g.groupId} g={g} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function ActivitiesHeader() {
  return (
    <header className="flex flex-col md:flex-row md:items-end md:justify-between gap-6">
      <div className="max-w-2xl">
        <div className="label-caps">Your spaces</div>
        <h1
          data-testid="activities-title"
          className="mt-3 font-editorial text-[44px] sm:text-[64px] leading-[1.02] text-ink"
        >
          My <span className="italic">Activities</span>
        </h1>
        <p className="mt-3 text-[16px] text-muted max-w-md">
          Trips, homes, and shared wallets. A quiet ledger for the moments you split with your people.
        </p>
      </div>
      <div>
        <Button
          as={Link}
          to="/app/create"
          variant="primary"
          size="lg"
          data-testid="activities-create-group-button"
        >
          <Plus size={16} /> Create a group
        </Button>
      </div>
    </header>
  )
}

function GroupCard({ g }) {
  const positive = g.myNet > 0
  const settled = Math.abs(g.myNet || 0) < 0.01
  return (
    <Card
      as={Link}
      to={`/app/activities/${g.groupId}`}
      className="block p-6 sm:p-7 hover-lift group"
      data-testid={`activities-group-card-${g.groupId}`}
    >
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <GroupTypeTag type={g.type} />
          <Pill tone="outline">{g.currency}</Pill>
        </div>
        <ArrowUpRight size={18} className="text-muted opacity-0 group-hover:opacity-100 transition-opacity" />
      </div>
      <h3 className="mt-4 font-editorial text-[26px] leading-[1.1] text-ink line-clamp-2">
        {g.name}
      </h3>
      <div className="mt-2 text-[13px] text-muted">
        {g.memberCount} {g.memberCount === 1 ? 'member' : 'members'} · {g.expenseCount} {g.expenseCount === 1 ? 'expense' : 'expenses'} · Last activity {timeAgo(g.lastActivityAt)}
      </div>
      <div className="mt-5 hairline-t pt-5">
        {settled ? (
          <div className="flex items-center justify-between">
            <div className="label-caps text-emerald-positive">Settled</div>
            <Money value={0} currency={g.currency} tone="muted" />
          </div>
        ) : (
          <div className="flex items-center justify-between">
            <div className="label-caps">{positive ? 'You are owed' : 'You owe'}</div>
            <Money
              value={Math.abs(g.myNet)}
              currency={g.currency}
              size="lg"
              tone={positive ? 'positive' : 'negative'}
            />
          </div>
        )}
      </div>
    </Card>
  )
}
