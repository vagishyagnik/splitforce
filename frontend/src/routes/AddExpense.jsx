import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowLeft, Camera, ImagePlus, X, Check, AlertCircle } from 'lucide-react'
import { useCreateExpense, useGroup, useUploadReceipt } from '../api/hooks.js'
import { CATEGORIES, CATEGORY_GROUPS, categoryEmoji } from '../util/categories.js'
import { Button, IconButton } from '../components/Button.jsx'
import { Card } from '../components/Card.jsx'
import { FieldError, Helper, Input, Label, Select, Textarea } from '../components/Field.jsx'
import { Avatar } from '../components/Avatar.jsx'
import { Segmented } from '../components/Segmented.jsx'
import { cn } from '../util/cn.js'

const MAX_RECEIPT_BYTES = 4 * 1024 * 1024

export default function AddExpense() {
  const { groupId } = useParams()
  const navigate = useNavigate()
  const { data: group, isLoading } = useGroup(groupId)
  const createExpense = useCreateExpense(groupId)
  const uploadReceipt = useUploadReceipt(groupId)

  const currencies = useMemo(() => {
    if (!group) return []
    return [group.baseCurrency, ...Object.keys(group.fxRates || {})]
  }, [group])

  const members = group?.members || []

  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('')
  const [paidBy, setPaidBy] = useState('')
  const [splitMode, setSplitMode] = useState('equal')
  const [participants, setParticipants] = useState({}) // { userId: { on: bool, value: string } }
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('Dining Out')
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [notes, setNotes] = useState('')
  const [receiptFile, setReceiptFile] = useState(null)
  const [receiptPreview, setReceiptPreview] = useState('')
  const [receiptError, setReceiptError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const cameraRef = useRef(null)
  const galleryRef = useRef(null)

  useEffect(() => {
    if (!group) return
    if (!currency) setCurrency(group.baseCurrency)
    if (!paidBy) {
      const me = members.find((m) => m.you) || members[0]
      if (me) setPaidBy(me.userId)
    }
    if (Object.keys(participants).length === 0 && members.length > 0) {
      const next = {}
      members.forEach((m) => (next[m.userId] = { on: true, value: '' }))
      setParticipants(next)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [group])

  const parsedAmount = Number(amount || 0)
  const activeParticipantIds = Object.entries(participants)
    .filter(([, v]) => v.on)
    .map(([k]) => k)

  const equalShare = activeParticipantIds.length > 0
    ? parsedAmount / activeParticipantIds.length
    : 0

  const exactSum = activeParticipantIds.reduce(
    (acc, id) => acc + Number(participants[id]?.value || 0),
    0,
  )
  const exactValid = splitMode !== 'exact'
    ? true
    : parsedAmount > 0 && Math.abs(exactSum - parsedAmount) < 0.01

  const percentSum = activeParticipantIds.reduce(
    (acc, id) => acc + Number(participants[id]?.percent || 0),
    0,
  )
  const percentValid = splitMode !== 'percent'
    ? true
    : parsedAmount > 0 && Math.abs(percentSum - 100) < 0.01

  const onFilePicked = (e) => {
    setReceiptError('')
    const f = e.target.files?.[0]
    if (!f) return
    if (f.size > MAX_RECEIPT_BYTES) {
      setReceiptError('Receipt too large — max 4 MB.')
      return
    }
    if (!/^image\//.test(f.type) && !/heic|heif/i.test(f.name)) {
      setReceiptError('Please pick an image (JPG, PNG, WEBP, HEIC).')
      return
    }
    setReceiptFile(f)
    const reader = new FileReader()
    reader.onload = () => setReceiptPreview(String(reader.result))
    reader.readAsDataURL(f)
  }

  const clearReceipt = () => {
    setReceiptFile(null)
    setReceiptPreview('')
    setReceiptError('')
    if (cameraRef.current) cameraRef.current.value = ''
    if (galleryRef.current) galleryRef.current.value = ''
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    setSubmitError('')
    if (!(parsedAmount > 0)) {
      setSubmitError('Amount must be greater than zero.')
      return
    }
    if (activeParticipantIds.length === 0) {
      setSubmitError('Pick at least one person to split with.')
      return
    }
    if (splitMode === 'exact' && !exactValid) {
      setSubmitError('Exact shares must add up to the total.')
      return
    }
    if (splitMode === 'percent' && !percentValid) {
      setSubmitError('Percentages must add up to 100.')
      return
    }

    try {
      let receiptKey
      if (receiptFile) {
        const up = await uploadReceipt.mutateAsync(receiptFile)
        receiptKey = up.receiptKey
      }

      const partList = activeParticipantIds.map((id) => ({
        userId: id,
        value:
          splitMode === 'exact'
            ? Number(participants[id]?.value || 0)
            : splitMode === 'percent'
              ? Number(participants[id]?.percent || 0)
              : 0,
      }))

      const wireSplitMode =
        splitMode === 'exact' ? 'amount' : splitMode === 'percent' ? 'percent' : 'equal'

      await createExpense.mutateAsync({
        description: description.trim() || category,
        date,
        category,
        notes: notes.trim() || null,
        cost: parsedAmount,
        currency,
        paidByUserId: paidBy,
        splitMode: wireSplitMode,
        participants: partList,
        receiptKey,
      })
      toast.success('Expense added')
      navigate(`/app/activities/${groupId}`)
    } catch (err) {
      const msg = err?.message || 'Something went wrong'
      setSubmitError(msg)
      toast.error(msg)
    }
  }

  if (isLoading || !group) {
    return (
      <div className="mx-auto max-w-2xl">
        <div className="skeleton h-8 w-40" />
        <div className="skeleton mt-6 h-48 w-full" />
      </div>
    )
  }

  const backHref = `/app/activities/${groupId}`

  return (
    <form onSubmit={onSubmit} className="mx-auto max-w-2xl animate-fade-up pb-32 md:pb-8">
      <Link
        to={backHref}
        className="inline-flex items-center gap-1.5 text-muted hover:text-ink text-[14px]"
      >
        <ArrowLeft size={16} /> Back to {group.name}
      </Link>

      <header className="mt-4">
        <div className="label-caps">New expense</div>
        <h1 className="mt-3 font-editorial text-[40px] sm:text-[52px] leading-[1.02]">
          Log the <span className="italic">moment.</span>
        </h1>
      </header>

      {/* Amount */}
      <Card className="mt-8 p-6 sm:p-8" tone="paper">
        <Label>Amount</Label>
        <div className="mt-3 flex items-baseline gap-3">
          <div className="flex-1">
            <input
              value={amount}
              onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ''))}
              inputMode="decimal"
              placeholder="0.00"
              data-testid="expense-amount-input"
              aria-label="Amount"
              className="mono w-full bg-transparent text-[52px] sm:text-[68px] font-semibold tracking-tight text-ink placeholder:text-muted/40 focus:outline-none"
            />
          </div>
          <Select
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
            className="w-[8.5rem]"
            data-testid="expense-currency-select"
          >
            {currencies.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
        </div>
        <Helper>Permitted: {currencies.join(', ')}. Add more in Settings.</Helper>
      </Card>

      {/* Split */}
      <section className="mt-8">
        <div className="flex items-center justify-between gap-4 mb-4">
          <h2 className="font-editorial text-[24px] leading-tight">Split</h2>
          <Segmented
            options={[
              { value: 'equal', label: 'Equally' },
              { value: 'exact', label: 'Exact amount' },
              { value: 'percent', label: 'Percentage' },
            ]}
            value={splitMode}
            onChange={setSplitMode}
            testId="split-mode-segment"
          />
        </div>

        <Card className="p-5 sm:p-6" tone="paper">
          <div className="grid gap-4 sm:grid-cols-2 sm:items-end">
            <div>
              <Label htmlFor="paid-by">Paid by</Label>
              <Select
                id="paid-by"
                value={paidBy}
                onChange={(e) => setPaidBy(e.target.value)}
                data-testid="expense-paid-by-select"
              >
                {members.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.you ? `${m.displayName} (You)` : m.displayName}
                  </option>
                ))}
              </Select>
            </div>
            {splitMode === 'equal' && parsedAmount > 0 && activeParticipantIds.length > 0 && (
              <div className="text-right text-[13px] text-muted">
                Each pays{' '}
                <span className="mono font-semibold text-ink">
                  {equalShare.toFixed(2)} {currency}
                </span>
              </div>
            )}
          </div>

          <div className="mt-4 hairline-t pt-4 divide-y divide-hairline">
            {members.map((m) => {
              const on = participants[m.userId]?.on
              return (
                <div key={m.userId} className="flex items-center gap-3 py-2.5">
                  <input
                    type="checkbox"
                    className="check"
                    checked={Boolean(on)}
                    onChange={(e) =>
                      setParticipants((p) => ({
                        ...p,
                        [m.userId]: { ...(p[m.userId] || {}), on: e.target.checked },
                      }))
                    }
                    aria-label={`Include ${m.displayName}`}
                    data-testid={`expense-participant-${m.userId}`}
                  />
                  <Avatar name={m.displayName} size="sm" />
                  <div className="flex-1 min-w-0">
                    <div className="text-[14.5px] truncate">
                      {m.displayName}
                      {m.you && <span className="ml-2 label-caps text-emerald-positive">You</span>}
                      {m.placeholder && <span className="ml-2 label-caps text-muted">Not signed in</span>}
                    </div>
                  </div>
                  {splitMode === 'exact' && on && (
                    <input
                      value={participants[m.userId]?.value || ''}
                      onChange={(e) =>
                        setParticipants((p) => ({
                          ...p,
                          [m.userId]: {
                            ...(p[m.userId] || {}),
                            value: e.target.value.replace(/[^0-9.]/g, ''),
                          },
                        }))
                      }
                      inputMode="decimal"
                      placeholder="0.00"
                      aria-label={`${m.displayName} share`}
                      data-testid={`expense-exact-${m.userId}`}
                      className="mono w-28 h-10 rounded-input border border-hairline bg-cream px-3 text-right text-[14px] focus:border-ink/50"
                    />
                  )}
                  {splitMode === 'percent' && on && (
                    <div className="flex items-center gap-1">
                      <input
                        value={participants[m.userId]?.percent || ''}
                        onChange={(e) =>
                          setParticipants((p) => ({
                            ...p,
                            [m.userId]: {
                              ...(p[m.userId] || {}),
                              percent: e.target.value.replace(/[^0-9.]/g, ''),
                            },
                          }))
                        }
                        inputMode="decimal"
                        placeholder="0"
                        aria-label={`${m.displayName} percent`}
                        data-testid={`expense-percent-${m.userId}`}
                        className="mono w-20 h-10 rounded-input border border-hairline bg-cream px-3 text-right text-[14px] focus:border-ink/50"
                      />
                      <span className="text-[13px] text-muted">%</span>
                    </div>
                  )}
                  {splitMode === 'equal' && on && parsedAmount > 0 && (
                    <div className="mono text-[13px] text-muted">
                      {equalShare.toFixed(2)}
                    </div>
                  )}
                </div>
              )
            })}
          </div>

          {splitMode === 'exact' && parsedAmount > 0 && activeParticipantIds.length > 0 && (
            <div
              className={cn(
                'mt-4 flex items-center gap-2 rounded-input px-3 py-2 text-[13px]',
                exactValid
                  ? 'bg-sage-pale text-emerald-positive'
                  : 'bg-coral-pale text-negative',
              )}
            >
              {exactValid ? <Check size={14} /> : <AlertCircle size={14} />}
              <span className="mono">
                {exactSum.toFixed(2)} / {parsedAmount.toFixed(2)} {currency}
              </span>
              <span className="ml-auto">
                {exactValid ? 'Shares add up.' : `Off by ${(parsedAmount - exactSum).toFixed(2)}`}
              </span>
            </div>
          )}

          {splitMode === 'percent' && parsedAmount > 0 && activeParticipantIds.length > 0 && (
            <div
              className={cn(
                'mt-4 flex items-center gap-2 rounded-input px-3 py-2 text-[13px]',
                percentValid
                  ? 'bg-sage-pale text-emerald-positive'
                  : 'bg-coral-pale text-negative',
              )}
            >
              {percentValid ? <Check size={14} /> : <AlertCircle size={14} />}
              <span className="mono">
                {percentSum.toFixed(2)} / 100 %
              </span>
              <span className="ml-auto">
                {percentValid ? 'Percentages add up.' : `Off by ${(100 - percentSum).toFixed(2)}%`}
              </span>
            </div>
          )}
        </Card>
      </section>

      {/* Details */}
      <section className="mt-8">
        <h2 className="font-editorial text-[24px] leading-tight mb-4">Details</h2>
        <Card className="p-5 sm:p-6 space-y-5" tone="paper">
          <div>
            <Label htmlFor="desc">Description</Label>
            <Input
              id="desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Dinner at Silk Road"
              className="mt-2"
              maxLength={140}
              data-testid="expense-description-input"
            />
          </div>
          <div>
            <Label htmlFor="date">Date</Label>
            <Input
              id="date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="mt-2 sm:max-w-xs"
              data-testid="expense-date-input"
            />
          </div>
          <div>
            <Label>Category</Label>
            <div className="mt-3 space-y-4">
              {CATEGORY_GROUPS.map((group) => (
                <div key={group.key}>
                  <div className="text-[11px] uppercase tracking-[0.14em] text-ink/60 mb-2">
                    {group.label}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {group.items.map((c) => (
                      <button
                        type="button"
                        key={c.key}
                        onClick={() => setCategory(c.key)}
                        className={cn(
                          'inline-flex items-center gap-1.5 h-10 rounded-full px-3.5 text-[13px] font-medium transition-colors border',
                          category === c.key
                            ? 'bg-ink text-cream border-ink'
                            : 'bg-cream text-ink border-hairline hover:border-ink/40',
                        )}
                        data-testid={`expense-category-${c.key}`}
                      >
                        <span>{c.emoji}</span> {c.label}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
              <div>
                <div className="text-[11px] uppercase tracking-[0.14em] text-ink/60 mb-2">
                  Other
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => setCategory('Other')}
                    className={cn(
                      'inline-flex items-center gap-1.5 h-10 rounded-full px-3.5 text-[13px] font-medium transition-colors border',
                      category === 'Other'
                        ? 'bg-ink text-cream border-ink'
                        : 'bg-cream text-ink border-hairline hover:border-ink/40',
                    )}
                    data-testid="expense-category-Other"
                  >
                    <span>✎</span> Other
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div>
            <Label htmlFor="notes" hint={`${notes.length}/1000`}>
              Notes
            </Label>
            <Textarea
              id="notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value.slice(0, 1000))}
              placeholder="Anything the group should remember."
              className="mt-2"
              data-testid="expense-notes-input"
            />
          </div>
        </Card>
      </section>

      {/* Receipt */}
      <section className="mt-8">
        <h2 className="font-editorial text-[24px] leading-tight mb-4">Receipt</h2>
        <Card className="p-5 sm:p-6" tone="paper">
          <input
            ref={cameraRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
            capture="environment"
            onChange={onFilePicked}
            className="sr-only"
            data-testid="receipt-camera-input"
            id="receipt-camera-input"
          />
          <input
            ref={galleryRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,image/heic,image/heif"
            onChange={onFilePicked}
            className="sr-only"
            data-testid="receipt-gallery-input"
            id="receipt-gallery-input"
          />
          {receiptFile ? (
            <div className="flex items-center gap-4" data-testid="receipt-preview">
              <img
                src={receiptPreview}
                alt="Receipt preview"
                className="h-24 w-24 rounded-input object-cover border border-hairline"
              />
              <div className="flex-1 min-w-0">
                <div className="text-[14px] font-medium truncate">{receiptFile.name}</div>
                <div className="text-[12px] text-muted mt-0.5">
                  {(receiptFile.size / 1024).toFixed(1)} KB
                </div>
                <div className="mt-2 flex flex-wrap gap-2">
                  <Button as="label" htmlFor="receipt-camera-input" variant="outline" size="sm">
                    <Camera size={14} /> Retake
                  </Button>
                  <Button as="label" htmlFor="receipt-gallery-input" variant="outline" size="sm">
                    <ImagePlus size={14} /> Choose from gallery
                  </Button>
                  <Button variant="ghost" size="sm" onClick={clearReceipt} type="button">
                    <X size={14} /> Remove
                  </Button>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center gap-3 rounded-input border border-dashed border-hairline bg-mist/50 px-4 py-8 text-center">
              <span className="inline-flex h-11 w-11 items-center justify-center rounded-full bg-cream text-ink border border-hairline">
                <Camera size={18} />
              </span>
              <span className="text-[14.5px] font-medium">Add a receipt</span>
              <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 w-full sm:w-auto">
                <Button
                  as="label"
                  htmlFor="receipt-camera-input"
                  variant="outline"
                  size="sm"
                  data-testid="receipt-take-photo-button"
                >
                  <Camera size={14} /> Take photo
                </Button>
                <Button
                  as="label"
                  htmlFor="receipt-gallery-input"
                  variant="outline"
                  size="sm"
                  data-testid="receipt-choose-gallery-button"
                >
                  <ImagePlus size={14} /> Choose from gallery
                </Button>
              </div>
              <Helper>Optional. JPG, PNG, WEBP, or HEIC · up to 4 MB.</Helper>
            </div>
          )}
          {receiptError && <FieldError>{receiptError}</FieldError>}
        </Card>
      </section>

      {submitError && (
        <div className="mt-6 rounded-input border border-negative/25 bg-negative/[0.06] text-negative px-4 py-3 text-[14px] flex items-center gap-2">
          <AlertCircle size={16} /> {submitError}
        </div>
      )}

      {/* Sticky action bar (mobile) */}
      <div className="md:hidden fixed bottom-0 inset-x-0 z-20 bg-cream/95 backdrop-blur-md hairline-t px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom,0px))]">
        <div className="mx-auto max-w-2xl flex items-center gap-2">
          <Button as={Link} to={backHref} variant="ghost" className="flex-1">
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            className="flex-1"
            disabled={createExpense.isPending || uploadReceipt.isPending}
            data-testid="expense-submit-button"
          >
            {createExpense.isPending || uploadReceipt.isPending ? 'Adding…' : 'Add expense'}
          </Button>
        </div>
      </div>

      {/* Desktop actions */}
      <div className="hidden md:flex mt-10 items-center gap-2 justify-end">
        <Button as={Link} to={backHref} variant="ghost">
          Cancel
        </Button>
        <Button
          type="submit"
          variant="primary"
          disabled={createExpense.isPending || uploadReceipt.isPending}
        >
          {createExpense.isPending || uploadReceipt.isPending ? 'Adding…' : 'Add expense'}
        </Button>
      </div>
    </form>
  )
}
