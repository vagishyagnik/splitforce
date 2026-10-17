import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import {
  ArrowLeft,
  Plus,
  Settings,
  Search,
  SlidersHorizontal,
  Download,
  Receipt as ReceiptIcon,
  StickyNote,
  X,
  UserPlus,
  ChevronRight,
  Pencil,
  Trash2,
} from 'lucide-react'
import { useDeleteExpense, useGroup, useSettleUp } from '../api/hooks.js'
import { Button, IconButton } from '../components/Button.jsx'
import { Card, CardHeader } from '../components/Card.jsx'
import { Pill } from '../components/Pill.jsx'
import { GroupTypeTag } from '../components/GroupTypeTag.jsx'
import { Avatar, AvatarStack } from '../components/Avatar.jsx'
import { Money } from '../components/Money.jsx'
import { Input, Select } from '../components/Field.jsx'
import { EmptyState } from '../components/EmptyState.jsx'
import { ErrorState } from '../components/ErrorState.jsx'
import { Skeleton, ExpenseRowSkeleton } from '../components/Skeleton.jsx'
import { Modal } from '../components/Modal.jsx'
import { Segmented } from '../components/Segmented.jsx'
import { CATEGORY_GROUPS, categoryEmoji } from '../util/categories.js'
import { formatDate, formatMoney, timeAgo } from '../util/format.js'
import { toCsv, downloadCsv } from '../util/csv.js'
import { cn } from '../util/cn.js'
import { useMediaQuery } from '../util/useMediaQuery.js'
import GroupSettingsPanel from './GroupSettingsPanel.jsx'
import CategoryBreakdownCard from './CategoryBreakdown.jsx'
import AddMembersModal from './AddMembersModal.jsx'

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:9090'

export default function GroupDetail() {
  const { groupId } = useParams()
  const { data: group, isLoading, isError, refetch } = useGroup(groupId)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [settleTarget, setSettleTarget] = useState(null)
  const [category, setCategory] = useState('')
  const [addMembersOpen, setAddMembersOpen] = useState(false)
  const isDesktop = useMediaQuery('(min-width: 1024px)')
  const settle = useSettleUp(groupId)

  if (isLoading) return <LoadingSkeleton />
  if (isError || !group) {
    return (
      <div className="mx-auto max-w-4xl">
        <ErrorState
          title="Couldn't load this group."
          description="It may have been removed, or you might not have access."
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const memberById = Object.fromEntries(group.members.map((m) => [m.userId, m]))

  const confirmSettle = async () => {
    if (!settleTarget) return
    try {
      await settle.mutateAsync({
        fromUserId: settleTarget.fromUserId,
        toUserId: settleTarget.toUserId,
        amount: settleTarget.amount,
        currency: group.baseCurrency,
        note: `Settlement · ${settleTarget.fromName} → ${settleTarget.toName}`,
      })
      toast.success('Settlement recorded')
      setSettleTarget(null)
    } catch (e) {
      toast.error(e?.message || 'Could not record settlement')
    }
  }

  return (
    <div className="animate-fade-up">
      <Link
        to="/app/activities"
        className="inline-flex items-center gap-1.5 text-muted hover:text-ink text-[14px]"
      >
        <ArrowLeft size={16} /> Back to activities
      </Link>

      <header className="mt-4 flex flex-col md:flex-row md:items-end md:justify-between gap-6">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <GroupTypeTag type={group.type} />
            <Pill tone="outline">{group.baseCurrency}</Pill>
          </div>
          <h1 className="mt-3 font-editorial text-[40px] sm:text-[60px] leading-[1.02] text-ink break-words">
            {group.name}
          </h1>
          <div className="mt-3 flex items-center gap-3 text-[13.5px] text-muted">
            <AvatarStack members={group.members} size="sm" />
            <span>
              {group.members.length} {group.members.length === 1 ? 'member' : 'members'}
              {' · '}Started {formatDate(group.createdAt, 'short')}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            as={Link}
            to={`/app/activities/${groupId}/add-expense`}
            variant="primary"
            data-testid="group-add-expense-button"
          >
            <Plus size={16} /> Add expense
          </Button>
          <IconButton
            as={Link}
            to={`/app/activities/${groupId}/notes`}
            label="Group notes"
            className="border border-hairline"
            data-testid="group-open-notes-button"
          >
            <StickyNote size={16} />
          </IconButton>
          <IconButton
            onClick={() => setSettingsOpen(true)}
            label="Group settings"
            className="border border-hairline"
            data-testid="group-open-settings-button"
          >
            <Settings size={16} />
          </IconButton>
        </div>
      </header>

      {isDesktop ? (
        <div className="mt-8 grid gap-6 lg:grid-cols-12">
          <div className="lg:col-span-5 space-y-6">
            <BalancesCard group={group} onAddMembers={() => setAddMembersOpen(true)} />
            <SettlementCard
              group={group}
              onSettle={(s) => setSettleTarget(s)}
            />
            <CategoryBreakdownCard
              group={group}
              selected={category}
              onSelect={setCategory}
            />
          </div>
          <div className="lg:col-span-7">
            <ExpenseHistoryCard
              group={group}
              memberById={memberById}
              category={category}
              setCategory={setCategory}
            />
          </div>
        </div>
      ) : (
        <MobileGroupBody
          group={group}
          memberById={memberById}
          category={category}
          setCategory={setCategory}
          onSettle={(s) => setSettleTarget(s)}
          onAddMembers={() => setAddMembersOpen(true)}
        />
      )}

      <AddMembersModal
        open={addMembersOpen}
        onClose={() => setAddMembersOpen(false)}
        group={group}
      />

      <GroupSettingsPanel
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        group={group}
      />

      <Modal
        open={Boolean(settleTarget)}
        onClose={() => setSettleTarget(null)}
        title="Settle up"
        testId="settle-modal"
        footer={
          <div className="flex items-center justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setSettleTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={confirmSettle}
              disabled={settle.isPending}
              data-testid="confirm-settlement-button"
            >
              {settle.isPending ? 'Recording…' : 'Record payment'}
            </Button>
          </div>
        }
      >
        {settleTarget && (
          <div className="space-y-4">
            <p className="text-[15px] text-muted">
              This adds a payment row to the expense history. Nothing leaves your wallet.
            </p>
            <div className="rounded-card bg-mist p-5 flex items-center justify-between gap-4">
              <div className="flex items-center gap-3 min-w-0">
                <Avatar name={settleTarget.fromName} />
                <div className="min-w-0">
                  <div className="text-[13px] text-muted">Pays</div>
                  <div className="font-medium truncate">{settleTarget.fromName}</div>
                </div>
              </div>
              <div className="mono text-[15px] text-muted">→</div>
              <div className="flex items-center gap-3 min-w-0">
                <Avatar name={settleTarget.toName} />
                <div className="min-w-0">
                  <div className="text-[13px] text-muted">Receives</div>
                  <div className="font-medium truncate">{settleTarget.toName}</div>
                </div>
              </div>
            </div>
            <div className="text-center">
              <div className="label-caps">Amount</div>
              <div className="mt-1">
                <Money value={settleTarget.amount} currency={group.baseCurrency} size="xl" />
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}

const TABS = [
  { value: 'expenses', label: 'Expenses' },
  { value: 'balances', label: 'Balances' },
  { value: 'insights', label: 'Insights' },
]

// Phone layout: the expense list is the home screen, with my own position
// pinned above it. Balances/settle-up and category insights are one tap away.
function MobileGroupBody({ group, memberById, category, setCategory, onSettle, onAddMembers }) {
  const storageKey = `splitforce:group-tab:${group.groupId}`
  const [tab, setTab] = useState(() => {
    const saved = localStorage.getItem(storageKey)
    return TABS.some((t) => t.value === saved) ? saved : 'expenses'
  })
  const tabsRef = useRef(null)

  useEffect(() => {
    localStorage.setItem(storageKey, tab)
  }, [storageKey, tab])

  const switchTab = (next) => {
    setTab(next)
    const el = tabsRef.current
    if (el && el.getBoundingClientRect().top < 0) el.scrollIntoView({ block: 'start' })
  }

  // Tapping a category in Insights jumps back to the filtered expense list.
  const pickCategory = (c) => {
    setCategory(c)
    if (c) switchTab('expenses')
  }

  return (
    <div className="mt-6">
      <MyPositionCard
        group={group}
        onSettle={onSettle}
        onSeeAll={() => switchTab('balances')}
      />
      <div
        ref={tabsRef}
        className="sticky top-14 md:top-[68px] z-20 -mx-4 sm:-mx-6 mt-5 px-4 sm:px-6 py-2 bg-cream/90 backdrop-blur-md scroll-mt-14 md:scroll-mt-[68px]"
      >
        <Segmented
          options={TABS}
          value={tab}
          onChange={switchTab}
          stretch
          testId="group-mobile-tabs"
        />
      </div>
      {/* Panels stay mounted so search/filter state survives tab switches. */}
      <div className="mt-4">
        <div hidden={tab !== 'expenses'}>
          <ExpenseHistoryCard
            group={group}
            memberById={memberById}
            category={category}
            setCategory={setCategory}
          />
        </div>
        <div hidden={tab !== 'balances'} className="space-y-6">
          <BalancesCard group={group} onAddMembers={onAddMembers} />
          <SettlementCard group={group} onSettle={onSettle} />
        </div>
        <div hidden={tab !== 'insights'}>
          <CategoryBreakdownCard group={group} selected={category} onSelect={pickCategory} />
        </div>
      </div>
    </div>
  )
}

const STALE_DAYS = 7
const MAX_POSITION_ROWS = 3

function daysSince(isoDate) {
  const t = new Date(isoDate).getTime()
  return Number.isNaN(t) ? 0 : (Date.now() - t) / 86_400_000
}

function MyPositionCard({ group, onSettle, onSeeAll }) {
  const me = group.members.find((m) => m.you)
  const net = me?.net || 0
  const square = Math.abs(net) < 0.01
  const mine = (group.settlement || []).filter(
    (s) => s.fromUserId === me?.userId || s.toUserId === me?.userId,
  )
  const lastExpenseDate = (group.expenses || [])
    .filter((e) => !e.payment && e.category !== 'Payment' && e.date)
    .reduce((max, e) => (e.date > max ? e.date : max), '')
  // Nothing logged for a week but money still owed: likely time to settle.
  const nudge = mine.length > 0 && lastExpenseDate && daysSince(lastExpenseDate) >= STALE_DAYS
  const shown = mine.slice(0, MAX_POSITION_ROWS)
  const hidden = mine.length - shown.length

  return (
    <Card
      className="p-5"
      tone={nudge ? 'coral' : square ? 'mist' : 'paper'}
      data-testid="my-position-card"
    >
      <div className="label-caps">
        {nudge ? 'All wrapped up?' : 'Your position'}
      </div>
      <div className="mt-1.5 flex items-baseline gap-2 flex-wrap">
        {square ? (
          <span className="font-editorial text-[26px] leading-tight text-ink">
            You're all square.
          </span>
        ) : (
          <>
            <span className="font-editorial text-[26px] leading-tight text-ink">
              {net > 0 ? "You're owed" : 'You owe'}
            </span>
            <Money
              value={Math.abs(net)}
              currency={group.baseCurrency}
              size="lg"
              tone={net > 0 ? 'positive' : 'negative'}
            />
          </>
        )}
      </div>
      {nudge && (
        <p className="mt-1 text-[13px] text-muted">
          Nothing new in {Math.floor(daysSince(lastExpenseDate))} days. Settle up while it's fresh.
        </p>
      )}
      {shown.length > 0 && (
        <ul className="mt-3 space-y-2">
          {shown.map((s, i) => {
            const iPay = s.fromUserId === me?.userId
            const other = iPay ? s.toName : s.fromName
            return (
              <li key={i}>
                <button
                  type="button"
                  onClick={() => onSettle(s)}
                  data-testid={`my-position-settle-${i}`}
                  className="w-full flex items-center gap-3 rounded-input border border-hairline bg-cream px-3 py-2.5 text-left hover:border-ink/30 transition-colors"
                >
                  <Avatar name={other} size="xs" />
                  <span className="min-w-0 flex-1 truncate text-[14px]">
                    {iPay ? (
                      <>You pay <span className="font-medium">{other}</span></>
                    ) : (
                      <><span className="font-medium">{other}</span> pays you</>
                    )}
                  </span>
                  <Money value={s.amount} currency={group.baseCurrency} size="sm" />
                  <span className="text-[12px] font-semibold text-ink underline underline-offset-2">Settle</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
      {hidden > 0 && (
        <button
          type="button"
          onClick={onSeeAll}
          className="mt-2 inline-flex items-center gap-1 text-[13px] font-semibold text-muted hover:text-ink"
        >
          {hidden} more <ChevronRight size={14} />
        </button>
      )}
    </Card>
  )
}

function BalancesCard({ group, onAddMembers }) {
  return (
    <Card className="p-6 sm:p-7">
      <CardHeader
        eyebrow="Where things stand"
        title="Balances"
        right={
          <Button
            variant="outline"
            size="sm"
            onClick={onAddMembers}
            data-testid="group-add-members-button"
          >
            <UserPlus size={14} /> Add people
          </Button>
        }
      />
      <ul className="mt-5 divide-y divide-hairline">
        {group.members.map((m) => {
          const settled = Math.abs(m.net || 0) < 0.01
          const positive = m.net > 0
          return (
            <li key={m.userId} className="flex items-center gap-3 py-3">
              <Avatar name={m.displayName} picture={m.picture} size="sm" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="text-[15px] font-medium truncate">{m.displayName}</span>
                  {m.you && (
                    <Pill tone="sage" className="!text-[10px] !py-0.5">You</Pill>
                  )}
                  {m.placeholder && (
                    <Pill tone="neutral" className="!text-[10px] !py-0.5">Not signed in</Pill>
                  )}
                </div>
                <div className="text-[12px] text-muted mt-0.5">
                  {settled
                    ? 'Settled'
                    : positive
                    ? 'Should receive'
                    : 'Owes the group'}
                </div>
              </div>
              <div className="text-right shrink-0">
                <Money
                  value={Math.abs(m.net)}
                  currency={group.baseCurrency}
                  tone={settled ? 'muted' : positive ? 'positive' : 'negative'}
                />
                <div className="label-caps mt-0.5 !tracking-[0.1em]">
                  {group.baseCurrency}
                </div>
              </div>
            </li>
          )
        })}
      </ul>
    </Card>
  )
}

function SettlementCard({ group, onSettle }) {
  const settlement = group.settlement || []
  const allSquare = settlement.length === 0

  return (
    <Card className="p-6 sm:p-7" tone={allSquare ? 'mist' : 'paper'}>
      <CardHeader
        eyebrow="Suggested transfers"
        title="Settle up"
        subtitle={allSquare ? undefined : 'The smallest set of payments that squares everyone.'}
      />
      {allSquare ? (
        <div className="mt-6 flex flex-col items-center text-center py-6">
          <div className="font-editorial text-[26px] italic text-emerald-positive">
            Everyone's square.
          </div>
          <p className="mt-1 text-[14px] text-muted">
            Nothing owed, nothing owing. Log another expense to change that.
          </p>
        </div>
      ) : (
        <ul className="mt-5 space-y-3">
          {settlement.map((s, i) => (
            <li key={i}>
              <button
                type="button"
                onClick={() => onSettle(s)}
                data-testid={`settle-suggestion-${i}`}
                className="w-full rounded-input border border-hairline bg-cream hover:border-ink/30 hover:shadow-soft transition-all p-4 flex items-center gap-3 text-left"
              >
                <div className="flex items-center gap-2 min-w-0 flex-1">
                  <span className="hidden sm:inline-flex"><Avatar name={s.fromName} size="xs" /></span>
                  <span className="text-[14px] font-medium truncate">{s.fromName}</span>
                  <span className="text-muted text-[12px]">pays</span>
                  <span className="hidden sm:inline-flex"><Avatar name={s.toName} size="xs" /></span>
                  <span className="text-[14px] font-medium truncate">{s.toName}</span>
                </div>
                <Money value={s.amount} currency={group.baseCurrency} tone="ink" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function ExpenseHistoryCard({ group, memberById, category, setCategory }) {
  const [q, setQ] = useState('')
  const [showFilters, setShowFilters] = useState(false)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [deleteTarget, setDeleteTarget] = useState(null)
  const deleteExpense = useDeleteExpense(group.groupId)

  const confirmDelete = async () => {
    const isPayment = deleteTarget.payment || deleteTarget.category === 'Payment'
    try {
      await deleteExpense.mutateAsync(deleteTarget.expenseId)
      toast.success(isPayment ? 'Settlement deleted' : 'Expense deleted')
      setDeleteTarget(null)
    } catch (err) {
      toast.error(err?.message || 'Could not delete')
    }
  }

  const filtered = useMemo(() => {
    return (group.expenses || []).filter((e) => {
      if (q) {
        const ql = q.toLowerCase()
        const paidName = memberById[e.paidByUserId]?.displayName || ''
        const matches =
          (e.description || '').toLowerCase().includes(ql) ||
          (e.category || '').toLowerCase().includes(ql) ||
          paidName.toLowerCase().includes(ql)
        if (!matches) return false
      }
      if (category && e.category !== category) return false
      if (from && e.date < from) return false
      if (to && e.date > to) return false
      return true
    })
  }, [group.expenses, q, category, from, to, memberById])

  const activeFilterCount = [category, from, to].filter(Boolean).length

  const clearFilters = () => {
    setCategory('')
    setFrom('')
    setTo('')
  }

  const exportCsv = () => {
    const rows = filtered.map((e) => ({
      date: e.date,
      description: e.description || '',
      type: e.payment ? 'Payment' : 'Expense',
      category: e.category || '',
      paidBy: memberById[e.paidByUserId]?.displayName || '',
      currency: e.currency,
      amount: e.cost,
      notes: e.notes || '',
    }))
    const csv = toCsv(rows, [
      { key: 'date', label: 'Date' },
      { key: 'description', label: 'Description' },
      { key: 'type', label: 'Type' },
      { key: 'category', label: 'Category' },
      { key: 'paidBy', label: 'Paid by' },
      { key: 'currency', label: 'Currency' },
      { key: 'amount', label: 'Amount' },
      { key: 'notes', label: 'Notes' },
    ])
    const safeName = group.name.replace(/[^a-z0-9-_]+/gi, '-').toLowerCase()
    downloadCsv(`splitforce-${safeName}-${new Date().toISOString().slice(0, 10)}.csv`, csv)
    toast.success('CSV downloaded')
  }

  return (
    <Card className="p-6 sm:p-7">
      <CardHeader
        eyebrow="Everything logged"
        title="Expense history"
        subtitle={`${filtered.length} of ${group.expenses?.length || 0} expense${(group.expenses?.length || 0) === 1 ? '' : 's'}`}
        right={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowFilters((s) => !s)}
              data-testid="toggle-expense-filters-button"
              aria-expanded={showFilters}
            >
              <SlidersHorizontal size={14} />
              Filters
              {activeFilterCount > 0 && (
                <span className="ml-1 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-coral text-cream px-1.5 text-[11px] font-semibold">
                  {activeFilterCount}
                </span>
              )}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={exportCsv}
              data-testid="export-expenses-button"
              disabled={filtered.length === 0}
            >
              <Download size={14} /> CSV
            </Button>
          </div>
        }
      />

      <div className="mt-4 relative">
        <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" />
        <Input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search expenses"
          className="pl-10"
          data-testid="expense-search-input"
        />
      </div>

      {showFilters && (
        <div className="mt-4 rounded-card border border-hairline bg-mist/50 p-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div>
              <div className="label-caps mb-1.5">Category</div>
              <Select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                data-testid="filter-category"
              >
                <option value="">All categories</option>
                {CATEGORY_GROUPS.map((g) => (
                  <optgroup key={g.key} label={g.label}>
                    {g.items.map((c) => (
                      <option key={c.key} value={c.key}>
                        {c.emoji}  {c.label}
                      </option>
                    ))}
                  </optgroup>
                ))}
                <option value="Other">✎  Other</option>
              </Select>
            </div>
            <div>
              <div className="label-caps mb-1.5">From</div>
              <Input
                type="date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
                data-testid="filter-from"
              />
            </div>
            <div>
              <div className="label-caps mb-1.5">To</div>
              <Input
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                data-testid="filter-to"
              />
            </div>
          </div>
          {activeFilterCount > 0 && (
            <div className="mt-3 flex justify-end">
              <button
                type="button"
                onClick={clearFilters}
                className="text-[13px] font-semibold text-negative hover:underline"
                data-testid="filter-clear"
              >
                Clear all filters
              </button>
            </div>
          )}
        </div>
      )}

      <div className="mt-6">
        {filtered.length === 0 ? (
          <div className="rounded-card border border-dashed border-hairline bg-mist/40 py-12 px-6 text-center">
            <div className="label-caps">Nothing to show</div>
            <div className="mt-2 font-editorial text-[22px] leading-tight text-ink">
              {group.expenses?.length ? 'No expenses match those filters.' : 'No expenses yet.'}
            </div>
            <p className="mt-1 text-[14px] text-muted">
              {group.expenses?.length
                ? 'Try widening your dates or clearing the search.'
                : 'Add the first one to see it land here.'}
            </p>
            {!group.expenses?.length && (
              <div className="mt-4">
                <Button as={Link} to={`/app/activities/${group.groupId}/add-expense`} variant="primary" size="sm">
                  <Plus size={14} /> Add expense
                </Button>
              </div>
            )}
          </div>
        ) : (
          <ul className="divide-y divide-hairline">
            {filtered.map((e) => (
              <ExpenseRow
                key={e.expenseId}
                expense={e}
                paidBy={memberById[e.paidByUserId]}
                editHref={`/app/activities/${group.groupId}/expenses/${encodeURIComponent(e.expenseId)}/edit`}
                onDelete={() => setDeleteTarget(e)}
              />
            ))}
          </ul>
        )}
      </div>

      <Modal
        open={Boolean(deleteTarget)}
        onClose={deleteExpense.isPending ? undefined : () => setDeleteTarget(null)}
        title="Delete this expense?"
        testId="delete-expense-modal"
        footer={
          <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setDeleteTarget(null)} disabled={deleteExpense.isPending}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={confirmDelete}
              disabled={deleteExpense.isPending}
              data-testid="delete-expense-confirm"
            >
              {deleteExpense.isPending ? 'Deleting…' : 'Delete'}
            </Button>
          </div>
        }
      >
        {deleteTarget && (
          <p className="text-[14.5px] text-muted">
            <span className="font-medium text-ink">{deleteTarget.description || 'This expense'}</span>
            {' '}({formatMoney(deleteTarget.cost, deleteTarget.currency)}) will be removed and everyone's
            balances recalculated. This can't be undone.
          </p>
        )}
      </Modal>
    </Card>
  )
}

function ExpenseRow({ expense, paidBy, editHref, onDelete }) {
  const isPayment = Boolean(expense.payment) || expense.category === 'Payment'
  const receiptUrl = expense.receiptKey ? `${API_BASE}/receipts/${expense.receiptKey}` : null
  return (
    <li className="relative py-4 flex items-start gap-3 sm:gap-4 rounded-input hover:bg-ink/[0.02] transition-colors">
      <Link
        to={editHref}
        className="absolute inset-0 rounded-input"
        aria-label={`Edit ${expense.description || (isPayment ? 'settlement' : 'expense')}`}
        data-testid={`expense-row-${expense.expenseId}`}
      />
      <div
        className={cn(
          'shrink-0 h-11 w-11 rounded-full inline-flex items-center justify-center text-[16px]',
          isPayment ? 'bg-sage-pale text-emerald-positive' : 'bg-mist text-ink',
        )}
        aria-hidden
      >
        {isPayment ? '↔' : categoryEmoji(expense.category)}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center flex-wrap gap-2">
          <span className="text-[15px] font-medium truncate">
            {expense.description || (isPayment ? 'Settlement' : 'Expense')}
          </span>
          {isPayment && (
            <Pill tone="sage" className="!text-[10px] !py-0.5">
              Payment
            </Pill>
          )}
          {expense.category && !isPayment && (
            <Pill tone="ghost" className="!text-[10px] !py-0.5 !font-medium">
              {expense.category}
            </Pill>
          )}
        </div>
        <div className="mt-1 text-[12.5px] text-muted flex flex-wrap items-center gap-x-2 gap-y-0.5">
          <span>{formatDate(expense.date, 'short')}</span>
          {paidBy && (
            <>
              <span>·</span>
              <span>{paidBy.you ? 'You paid' : `${paidBy.displayName} paid`}</span>
            </>
          )}
        </div>
      </div>
      {receiptUrl && (
        <a
          href={receiptUrl}
          target="_blank"
          rel="noreferrer"
          aria-label="View receipt"
          className="relative z-10 hidden sm:inline-flex h-11 w-11 items-center justify-center rounded-input border border-hairline text-muted hover:text-ink hover:border-ink/30 transition-colors"
        >
          <ReceiptIcon size={16} />
        </a>
      )}
      <div className="text-right shrink-0">
        <Money value={expense.cost} currency={expense.currency} />
        <div className="label-caps mt-0.5 !tracking-[0.1em]">{expense.currency}</div>
      </div>
      <div className="relative z-10 shrink-0 flex items-center gap-0.5 -mr-2">
        <IconButton
          as={Link}
          to={editHref}
          label="Edit expense"
          className="h-9 w-9 text-muted hover:text-ink"
          data-testid={`expense-edit-${expense.expenseId}`}
        >
          <Pencil size={15} />
        </IconButton>
        <IconButton
          type="button"
          onClick={onDelete}
          label="Delete expense"
          className="h-9 w-9 text-muted hover:text-negative hover:bg-negative/10"
          data-testid={`expense-delete-${expense.expenseId}`}
        >
          <Trash2 size={15} />
        </IconButton>
      </div>
    </li>
  )
}

function LoadingSkeleton() {
  return (
    <div>
      <Skeleton className="h-4 w-32" />
      <Skeleton className="mt-4 h-10 w-64" />
      <div className="mt-8 grid gap-6 lg:grid-cols-12">
        <div className="lg:col-span-5 space-y-6">
          <div className="surface-paper p-6">
            <Skeleton className="h-6 w-40" />
            <div className="mt-4 space-y-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                  <Skeleton className="h-9 w-9 rounded-full" />
                  <div className="flex-1">
                    <Skeleton className="h-3 w-1/3" />
                    <Skeleton className="mt-2 h-3 w-1/5" />
                  </div>
                  <Skeleton className="h-4 w-20" />
                </div>
              ))}
            </div>
          </div>
        </div>
        <div className="lg:col-span-7">
          <div className="surface-paper p-6">
            <Skeleton className="h-6 w-48" />
            <div className="mt-4 space-y-3">
              {Array.from({ length: 5 }).map((_, i) => (
                <ExpenseRowSkeleton key={i} />
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
