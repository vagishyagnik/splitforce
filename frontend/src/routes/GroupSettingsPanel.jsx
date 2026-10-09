import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Plus, X, AlertCircle, Download, Trash2 } from 'lucide-react'
import { SlideOver } from '../components/SlideOver.jsx'
import { Button, IconButton } from '../components/Button.jsx'
import { Modal } from '../components/Modal.jsx'
import { FieldError, Helper, Input, Label, Select } from '../components/Field.jsx'
import { Pill } from '../components/Pill.jsx'
import { ADDABLE_CURRENCIES, CURRENCY_LABEL } from '../util/currencies.js'
import { useDeleteGroup, useMe, useRenameGroup, useSetFxRate, useDeleteFxRate } from '../api/hooks.js'

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:9090'

export default function GroupSettingsPanel({ open, onClose, group }) {
  return (
    <SlideOver
      open={open}
      onClose={onClose}
      title="Group settings"
      subtitle={group?.name}
      testId="group-settings-panel"
    >
      {group && (
        <>
          <RenameSection group={group} />
          <div className="mt-8" />
          <CurrenciesSection group={group} />
          <div className="mt-8" />
          <DangerZoneSection group={group} onClose={onClose} />
        </>
      )}
    </SlideOver>
  )
}

function DangerZoneSection({ group, onClose }) {
  const { data: me } = useMe()
  const navigate = useNavigate()
  const deleteGroup = useDeleteGroup(group.groupId)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [typed, setTyped] = useState('')
  const [serverError, setServerError] = useState('')

  const isOwner = me && !me.unauthorized && me.userId === group.ownerUserId
  if (!isOwner) return null

  const anyoneUnsettled = (group.members || []).some((m) => Math.abs(m.net || 0) >= 0.01)
  const typedOk = typed.trim().toLowerCase() === group.name.trim().toLowerCase()

  const closeConfirm = () => {
    setConfirmOpen(false)
    setTyped('')
    setServerError('')
  }

  const doDelete = async () => {
    setServerError('')
    try {
      await deleteGroup.mutateAsync()
      toast.success('Group deleted')
      closeConfirm()
      onClose?.()
      navigate('/app/activities', { replace: true })
    } catch (e) {
      const msg = e?.message || 'Could not delete group'
      if (/settle|409/i.test(msg)) {
        setServerError('Some members still have a balance. Settle up first, then try again.')
      } else {
        setServerError(msg)
      }
    }
  }

  return (
    <section>
      <h3 className="font-editorial text-[22px] leading-tight text-negative">Danger zone</h3>
      <p className="mt-1 text-[13.5px] text-muted">
        Deleting removes the group and every expense in it. Only you (the owner) can do this,
        and only when everyone's balance is zero.
      </p>
      {anyoneUnsettled && (
        <div className="mt-3 flex items-start gap-2 rounded-input border border-hairline bg-mist/60 px-3 py-2 text-[12.5px] text-muted">
          <AlertCircle size={14} className="mt-0.5 shrink-0" />
          <span>
            Some balances aren't zero yet. You can still download the data, but deletion will
            be blocked until everyone's settled.
          </span>
        </div>
      )}
      <div className="mt-3">
        <Button
          variant="danger"
          onClick={() => setConfirmOpen(true)}
          data-testid="delete-group-button"
        >
          <Trash2 size={14} /> Delete this group
        </Button>
      </div>

      <Modal
        open={confirmOpen}
        onClose={deleteGroup.isPending ? undefined : closeConfirm}
        title="Delete this group?"
        testId="delete-group-modal"
        footer={
          <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-2 pt-2">
            <Button
              variant="ghost"
              onClick={closeConfirm}
              disabled={deleteGroup.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={doDelete}
              disabled={!typedOk || deleteGroup.isPending}
              data-testid="confirm-delete-group-button"
            >
              {deleteGroup.isPending ? 'Deleting…' : 'Delete permanently'}
            </Button>
          </div>
        }
      >
        <div className="space-y-4">
          <p className="text-[14.5px] text-ink">
            This wipes <span className="font-semibold">{group.name}</span> and all
            {' '}{group.expenses?.length || 0} expense{group.expenses?.length === 1 ? '' : 's'} for
            everyone. It can't be undone.
          </p>
          <div className="rounded-card bg-mist p-4">
            <div className="label-caps">Grab your data first</div>
            <p className="mt-1 text-[13px] text-muted">
              Download a CSV of every expense before you delete. You won't be able to get it back.
            </p>
            <div className="mt-3">
              <Button
                as="a"
                href={`${API_BASE}/api/groups/${group.groupId}/export`}
                variant="outline"
                size="sm"
                data-testid="download-group-data-button"
              >
                <Download size={14} /> Download CSV
              </Button>
            </div>
          </div>
          <div>
            <Label htmlFor="delete-confirm">
              Type <span className="mono">{group.name}</span> to confirm
            </Label>
            <Input
              id="delete-confirm"
              value={typed}
              onChange={(e) => setTyped(e.target.value)}
              autoComplete="off"
              className="mt-2"
              data-testid="delete-group-confirm-input"
            />
          </div>
          {serverError && (
            <div className="flex items-start gap-2 rounded-input border border-negative/25 bg-negative/[0.06] text-negative px-3 py-2 text-[13px]">
              <AlertCircle size={14} className="mt-0.5 shrink-0" />
              <span>{serverError}</span>
            </div>
          )}
        </div>
      </Modal>
    </section>
  )
}

function RenameSection({ group }) {
  const [name, setName] = useState(group.name)
  const rename = useRenameGroup(group.groupId)

  useEffect(() => setName(group.name), [group.name])

  const dirty = name.trim() !== group.name && name.trim().length > 0

  const onSave = async () => {
    try {
      await rename.mutateAsync(name.trim())
      toast.success('Group renamed')
    } catch (e) {
      toast.error(e?.message || 'Rename failed')
    }
  }

  return (
    <section>
      <h3 className="font-editorial text-[22px] leading-tight">Rename group</h3>
      <p className="mt-1 text-[13.5px] text-muted">
        A crisp, memorable name reads better than a long one.
      </p>
      <div className="mt-3">
        <Label htmlFor="group-rename">Group name</Label>
        <div className="mt-2 flex items-center gap-2">
          <Input
            id="group-rename"
            value={name}
            maxLength={80}
            onChange={(e) => setName(e.target.value)}
            data-testid="settings-rename-input"
          />
          <Button
            variant="ink"
            onClick={onSave}
            disabled={!dirty || rename.isPending}
            data-testid="settings-rename-save"
          >
            {rename.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </div>
    </section>
  )
}

function CurrenciesSection({ group }) {
  const setRate = useSetFxRate(group.groupId)
  const deleteRate = useDeleteFxRate(group.groupId)
  const [rates, setRates] = useState({})
  const [banner, setBanner] = useState('')
  const [addOpen, setAddOpen] = useState(false)
  const [newCode, setNewCode] = useState('USD')
  const [newRate, setNewRate] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    const initial = {}
    Object.entries(group.fxRates || {}).forEach(([k, v]) => {
      initial[k] = String(v)
    })
    setRates(initial)
  }, [group.fxRates])

  const existing = useMemo(() => Object.keys(group.fxRates || {}), [group.fxRates])
  const addable = ADDABLE_CURRENCIES.filter(
    (c) => c !== group.baseCurrency && !existing.includes(c),
  )

  useEffect(() => {
    if (!addable.includes(newCode)) setNewCode(addable[0] || '')
  }, [addable, newCode])

  const saveRate = async (code) => {
    setError('')
    const value = Number(rates[code])
    if (!(value > 0)) {
      setError(`Rate for ${code} must be a positive number.`)
      return
    }
    try {
      await setRate.mutateAsync({ code, rate: value })
      setBanner('Balances updated for the new rate.')
    } catch (e) {
      setError(e?.message || 'Could not update rate')
    }
  }

  const remove = async (code) => {
    try {
      await deleteRate.mutateAsync(code)
      toast.success(`${code} removed`)
    } catch (e) {
      toast.error(e?.message || 'Cannot remove currency')
    }
  }

  const addCurrency = async () => {
    setError('')
    const value = Number(newRate)
    if (!(value > 0)) {
      setError('Enter a positive rate.')
      return
    }
    try {
      await setRate.mutateAsync({ code: newCode, rate: value })
      setBanner(`Added ${newCode}.`)
      setAddOpen(false)
      setNewRate('')
    } catch (e) {
      setError(e?.message || 'Could not add currency')
    }
  }

  return (
    <section>
      <h3 className="font-editorial text-[22px] leading-tight">Currencies</h3>
      <p className="mt-1 text-[13.5px] text-muted">
        The base currency stays fixed. Add rates for anything else you'll log.
      </p>

      {banner && (
        <div
          className="mt-4 flex items-center gap-2 rounded-input bg-sage-pale text-emerald-positive px-3 py-2 text-[13px]"
          data-testid="rate-update-banner"
        >
          <span className="flex-1">{banner}</span>
          <IconButton
            onClick={() => setBanner('')}
            label="Dismiss"
            className="h-8 w-8 text-emerald-positive hover:bg-emerald-positive/10"
          >
            <X size={14} />
          </IconButton>
        </div>
      )}

      <div className="mt-4 rounded-card border border-hairline divide-y divide-hairline">
        <div className="flex items-center gap-3 px-4 py-3">
          <Pill tone="ink">{group.baseCurrency}</Pill>
          <div className="flex-1 min-w-0">
            <div className="text-[14.5px] font-medium">
              {CURRENCY_LABEL[group.baseCurrency] || group.baseCurrency}
            </div>
            <div className="text-[12px] text-muted">Base currency · locked</div>
          </div>
        </div>

        {existing.map((code) => (
          <div key={code} className="px-4 py-3 space-y-2">
            <div className="flex items-center gap-3">
              <Pill tone="coral">{code}</Pill>
              <div className="flex-1 min-w-0">
                <div className="text-[14.5px] font-medium">
                  {CURRENCY_LABEL[code] || code}
                </div>
                <div className="text-[12px] text-muted">
                  1 {code} = <span className="mono">{group.fxRates[code]}</span> {group.baseCurrency}
                </div>
              </div>
              <IconButton
                onClick={() => remove(code)}
                label={`Remove ${code}`}
                className="h-9 w-9 text-negative hover:bg-negative/10"
              >
                <X size={16} />
              </IconButton>
            </div>
            <div className="flex items-center gap-2">
              <Input
                value={rates[code] ?? ''}
                inputMode="decimal"
                onChange={(e) =>
                  setRates((r) => ({ ...r, [code]: e.target.value.replace(/[^0-9.]/g, '') }))
                }
                aria-label={`Rate for ${code}`}
                data-testid={`currency-rate-input-${code.toLowerCase()}`}
              />
              <Button
                variant="outline"
                size="sm"
                onClick={() => saveRate(code)}
                disabled={setRate.isPending}
                data-testid={`save-currency-rate-${code.toLowerCase()}`}
              >
                Save
              </Button>
            </div>
          </div>
        ))}

        {addOpen ? (
          <div className="px-4 py-3 space-y-3">
            <div className="grid grid-cols-2 gap-2">
              <Select
                value={newCode}
                onChange={(e) => setNewCode(e.target.value)}
                aria-label="Currency to add"
              >
                {addable.map((c) => (
                  <option key={c} value={c}>
                    {c} — {CURRENCY_LABEL[c]}
                  </option>
                ))}
              </Select>
              <Input
                value={newRate}
                inputMode="decimal"
                onChange={(e) => setNewRate(e.target.value.replace(/[^0-9.]/g, ''))}
                placeholder={`1 ${newCode || '???'} = ? ${group.baseCurrency}`}
                aria-label="Rate"
              />
            </div>
            <Helper>Rate is how many {group.baseCurrency} you pay for 1 {newCode}.</Helper>
            <div className="flex items-center gap-2">
              <Button
                variant="ink"
                size="sm"
                onClick={addCurrency}
                disabled={setRate.isPending || !newCode}
                data-testid="save-currency-rates-button"
              >
                {setRate.isPending ? 'Saving…' : 'Add currency'}
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setAddOpen(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          addable.length > 0 && (
            <div className="p-3">
              <button
                type="button"
                onClick={() => setAddOpen(true)}
                data-testid="add-currency-button"
                className="w-full flex items-center justify-center gap-2 rounded-full h-11 px-4 text-[13px] font-semibold text-coral hover:bg-coral-pale transition-colors"
              >
                <Plus size={14} /> Add currency
              </button>
            </div>
          )
        )}
      </div>

      {error && <FieldError>{error}</FieldError>}
    </section>
  )
}
