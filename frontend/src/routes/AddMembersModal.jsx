import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Plus, Trash2 } from 'lucide-react'
import { useAddMembers, useContacts } from '../api/hooks.js'
import { Button, IconButton } from '../components/Button.jsx'
import { Modal } from '../components/Modal.jsx'
import { FieldError, Helper } from '../components/Field.jsx'
import { EMAIL_RE, MemberEmailField } from '../components/MemberEmailField.jsx'

export default function AddMembersModal({ open, onClose, group }) {
  const addMembers = useAddMembers(group.groupId)
  const contactsQuery = useContacts()
  const [rows, setRows] = useState([{ email: '' }])
  const [errors, setErrors] = useState({})

  useEffect(() => {
    if (open) {
      setRows([{ email: '' }])
      setErrors({})
    }
  }, [open])

  // Only active members count as "already in"; removed ones can be re-added.
  const memberEmails = useMemo(() => {
    const set = new Set()
    group.members.forEach((m) => {
      if (m.active && m.email) set.add(m.email.toLowerCase())
    })
    return set
  }, [group.members])

  const usedEmails = useMemo(() => {
    const set = new Set(memberEmails)
    rows.forEach((r) => {
      const e = (r.email || '').trim().toLowerCase()
      if (e) set.add(e)
    })
    return set
  }, [rows, memberEmails])

  const updateRow = (i, email) =>
    setRows((rs) => rs.map((r, idx) => (idx === i ? { email } : r)))
  const removeRow = (i) => setRows((rs) => rs.filter((_, idx) => idx !== i))
  const addRow = () => setRows((rs) => [...rs, { email: '' }])

  const onSubmit = async () => {
    const nextErrors = {}
    const clean = []
    const seen = new Set()
    rows.forEach((r, i) => {
      const email = (r.email || '').trim()
      if (!email) return
      const key = email.toLowerCase()
      if (!EMAIL_RE.test(email)) nextErrors[`row-${i}`] = 'Not a valid email.'
      else if (memberEmails.has(key)) nextErrors[`row-${i}`] = 'Already in this group.'
      else if (seen.has(key)) nextErrors[`row-${i}`] = 'Already added above.'
      else {
        seen.add(key)
        clean.push({ email, displayName: '' })
      }
    })
    if (clean.length === 0 && Object.keys(nextErrors).length === 0) {
      nextErrors['row-0'] = 'Add at least one email.'
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    try {
      const res = await addMembers.mutateAsync(clean)
      const n = res?.added?.length || 0
      toast.success(n === 1 ? `${res.added[0].displayName} added` : `${n} people added`)
      onClose()
    } catch (e) {
      toast.error(e?.message || 'Could not add members')
    }
  }

  return (
    <Modal
      open={open}
      onClose={addMembers.isPending ? undefined : onClose}
      title="Add people"
      testId="add-members-modal"
      footer={
        <div className="flex items-center justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose} disabled={addMembers.isPending}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={onSubmit}
            disabled={addMembers.isPending}
            data-testid="confirm-add-members-button"
          >
            {addMembers.isPending ? 'Adding…' : 'Add to group'}
          </Button>
        </div>
      }
    >
      <div className="space-y-3">
        <Helper>
          They'll see {group.name} the next time they sign in with Google. Existing expenses
          stay as they are — new members only share in expenses logged from now on.
        </Helper>
        {rows.map((r, i) => (
          <div key={i}>
            <div className="flex items-center gap-2">
              <MemberEmailField
                value={r.email}
                onChange={(v) => updateRow(i, v)}
                contacts={contactsQuery.data || []}
                usedEmails={usedEmails}
                invalid={Boolean(errors[`row-${i}`])}
                testId={`add-member-email-${i}`}
              />
              {rows.length > 1 && (
                <IconButton
                  onClick={() => removeRow(i)}
                  label="Remove"
                  className="h-11 w-11 text-muted hover:text-negative"
                  data-testid={`add-member-remove-${i}`}
                >
                  <Trash2 size={16} />
                </IconButton>
              )}
            </div>
            <FieldError>{errors[`row-${i}`]}</FieldError>
          </div>
        ))}
        <Button variant="ghost" size="sm" onClick={addRow} data-testid="add-member-row-button">
          <Plus size={14} /> Another person
        </Button>
      </div>
    </Modal>
  )
}
