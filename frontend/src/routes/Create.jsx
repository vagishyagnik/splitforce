import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowLeft, Plus, Trash2, MapPin, Home, Users2 } from 'lucide-react'
import { useCreateGroup, useContacts } from '../api/hooks.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { CREATION_CURRENCIES, CURRENCY_LABEL } from '../util/currencies.js'
import { Button, IconButton } from '../components/Button.jsx'
import { Card } from '../components/Card.jsx'
import { FieldError, Helper, Input, Label, Select } from '../components/Field.jsx'
import { Avatar } from '../components/Avatar.jsx'
import { EMAIL_RE, MemberEmailField } from '../components/MemberEmailField.jsx'
import { cn } from '../util/cn.js'

const TYPES = [
  {
    key: 'trip',
    label: 'Trip',
    body: 'Villas, meals, taxis. For weekends away and long journeys.',
    icon: <MapPin size={20} />,
  },
  {
    key: 'home',
    label: 'Home',
    body: 'Rent, groceries, bills. For the people you share four walls with.',
    icon: <Home size={20} />,
  },
  {
    key: 'general',
    label: 'General',
    body: 'Everything else. For duos, projects, or the friend group tab.',
    icon: <Users2 size={20} />,
  },
]

export default function Create() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const create = useCreateGroup()
  const contactsQuery = useContacts()

  const [type, setType] = useState('trip')
  const [name, setName] = useState('')
  const [baseCurrency, setBaseCurrency] = useState('INR')
  const [members, setMembers] = useState([{ email: '' }])
  const [errors, setErrors] = useState({})

  const updateMember = (i, patch) =>
    setMembers((ms) => ms.map((m, idx) => (idx === i ? { ...m, ...patch } : m)))
  const removeMember = (i) => setMembers((ms) => ms.filter((_, idx) => idx !== i))
  const addMember = () => setMembers((ms) => [...ms, { email: '' }])

  const usedEmails = useMemo(() => {
    const set = new Set()
    if (user?.email) set.add(user.email.toLowerCase())
    members.forEach((m) => {
      const e = (m.email || '').trim().toLowerCase()
      if (e) set.add(e)
    })
    return set
  }, [members, user?.email])

  const onSubmit = async (e) => {
    e.preventDefault()
    const nextErrors = {}
    if (!name.trim()) nextErrors.name = 'Give this group a name.'
    const cleanMembers = []
    const seen = new Set()
    members.forEach((m, i) => {
      const email = (m.email || '').trim()
      if (!email) return
      if (!EMAIL_RE.test(email)) {
        nextErrors[`member-${i}`] = 'Not a valid email.'
        return
      }
      const key = email.toLowerCase()
      if (key === (user?.email || '').toLowerCase()) {
        nextErrors[`member-${i}`] = "That's you — you're already in."
        return
      }
      if (seen.has(key)) {
        nextErrors[`member-${i}`] = 'Already added above.'
        return
      }
      seen.add(key)
      cleanMembers.push({ email, displayName: '' })
    })
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    try {
      const res = await create.mutateAsync({
        name: name.trim(),
        type,
        baseCurrency,
        members: cleanMembers,
      })
      toast.success('Group created')
      navigate(`/app/activities/${res.groupId}`)
    } catch (err) {
      toast.error(err?.message || 'Could not create group')
    }
  }

  return (
    <div className="mx-auto max-w-3xl animate-fade-up">
      <Link
        to="/app/activities"
        className="inline-flex items-center gap-1.5 text-muted hover:text-ink text-[14px]"
      >
        <ArrowLeft size={16} /> Back to activities
      </Link>

      <header className="mt-4">
        <div className="label-caps">New space</div>
        <h1 className="mt-3 font-editorial text-[40px] sm:text-[56px] leading-[1.02]">
          Make it <span className="italic">yours.</span>
        </h1>
        <p className="mt-3 text-[15px] text-muted max-w-md">
          Choose a shape, name it something you'll remember, and add the people you'll split with.
        </p>
      </header>

      <form onSubmit={onSubmit} className="mt-10 space-y-8">
        <section>
          <Label>Shape</Label>
          <div className="mt-3 grid gap-3 md:grid-cols-3">
            {TYPES.map((t) => {
              const active = type === t.key
              return (
                <button
                  type="button"
                  key={t.key}
                  onClick={() => setType(t.key)}
                  data-testid={`create-type-${t.key}`}
                  className={cn(
                    'text-left p-5 rounded-card border transition-colors',
                    active
                      ? 'border-ink bg-cream shadow-soft'
                      : 'border-hairline bg-cream hover:border-ink/30',
                  )}
                >
                  <div
                    className={cn(
                      'inline-flex h-10 w-10 items-center justify-center rounded-full',
                      active ? 'bg-coral text-cream' : 'bg-mist text-ink',
                    )}
                  >
                    {t.icon}
                  </div>
                  <div className="mt-3 font-editorial text-[22px] leading-tight">{t.label}</div>
                  <div className="mt-1 text-[13.5px] text-muted">{t.body}</div>
                </button>
              )
            })}
          </div>
        </section>

        <section>
          <Label htmlFor="group-name">Name</Label>
          <Input
            id="group-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Bali 2026"
            className="mt-2"
            maxLength={80}
            invalid={Boolean(errors.name)}
            data-testid="create-group-name-input"
          />
          <FieldError>{errors.name}</FieldError>
        </section>

        <section>
          <Label>Base currency</Label>
          <Select
            value={baseCurrency}
            onChange={(e) => setBaseCurrency(e.target.value)}
            className="mt-2"
            data-testid="create-base-currency-select"
          >
            {CREATION_CURRENCIES.map((c) => (
              <option key={c} value={c}>
                {c} — {CURRENCY_LABEL[c]}
              </option>
            ))}
          </Select>
          <Helper>You can add more currencies inside the group later.</Helper>
        </section>

        <section>
          <div className="flex items-center justify-between">
            <Label>Members</Label>
            <button
              type="button"
              onClick={addMember}
              className="text-[13px] font-semibold text-coral hover:text-coral-hover inline-flex items-center gap-1"
              data-testid="create-add-member-button"
            >
              <Plus size={14} /> Add person
            </button>
          </div>

          <div className="mt-3 rounded-card border border-hairline bg-cream divide-y divide-hairline">
            <div className="flex items-center gap-3 px-4 py-3">
              <Avatar name={user?.name} picture={user?.picture} size="sm" />
              <div className="flex-1">
                <div className="text-[15px] font-medium">{user?.name}</div>
                <div className="text-[12px] text-muted">{user?.email}</div>
              </div>
              <span className="label-caps text-emerald-positive">You</span>
            </div>

            {members.map((m, i) => (
              <div key={i} className="p-4 space-y-2">
                <div className="flex gap-2">
                  <MemberEmailField
                    value={m.email}
                    onChange={(v) => updateMember(i, { email: v })}
                    contacts={contactsQuery.data || []}
                    usedEmails={usedEmails}
                    invalid={Boolean(errors[`member-${i}`])}
                    testId={`create-member-email-${i}`}
                  />
                  <IconButton
                    onClick={() => removeMember(i)}
                    label="Remove member"
                    className="h-12 w-12 text-negative hover:bg-negative/10"
                    data-testid={`create-remove-member-${i}`}
                  >
                    <Trash2 size={16} />
                  </IconButton>
                </div>
                <FieldError>{errors[`member-${i}`]}</FieldError>
              </div>
            ))}
            {members.length === 0 && (
              <div className="px-4 py-6 text-center text-[13px] text-muted">
                Just you for now. Add people any time.
              </div>
            )}
          </div>
          <Helper>Add anyone by email — they'll see this group when they sign in with Google.</Helper>
        </section>

        <Card tone="mist" className="p-5 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4">
          <div>
            <div className="label-caps">Ready?</div>
            <div className="font-editorial text-[22px] leading-tight mt-1">Create your space</div>
          </div>
          <div className="flex items-center gap-2">
            <Button as={Link} to="/app/activities" variant="ghost">
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              disabled={create.isPending}
              data-testid="create-group-submit"
            >
              {create.isPending ? 'Creating…' : 'Create group'}
            </Button>
          </div>
        </Card>
      </form>
    </div>
  )
}
