import { useEffect, useState } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowLeft, AlertCircle } from 'lucide-react'
import { useGroup, useUpdateGroupNotes } from '../api/hooks.js'
import { Card } from '../components/Card.jsx'
import { Button } from '../components/Button.jsx'
import { Textarea, Label, Helper, FieldError } from '../components/Field.jsx'
import { ErrorState } from '../components/ErrorState.jsx'
import { Skeleton } from '../components/Skeleton.jsx'
import { formatDate, timeAgo } from '../util/format.js'

const NOTES_MAX = 10_000

export default function Notes() {
  const { groupId } = useParams()
  const navigate = useNavigate()
  const { data: group, isLoading, isError, refetch } = useGroup(groupId)
  const updateNotes = useUpdateGroupNotes(groupId)
  const [value, setValue] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (group) setValue(group.notes || '')
  }, [group?.notes])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="mt-4 h-10 w-64" />
        <Skeleton className="mt-8 h-64 w-full" />
      </div>
    )
  }
  if (isError || !group) {
    return (
      <div className="mx-auto max-w-3xl">
        <ErrorState
          title="Couldn't load notes."
          description="The group may have moved or you may not have access."
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const original = group.notes || ''
  const dirty = value !== original
  const tooLong = value.length > NOTES_MAX
  const authorName =
    group.members?.find((m) => m.userId === group.notesUpdatedByUserId)?.displayName

  const onSave = async () => {
    setError('')
    if (tooLong) {
      setError(`Notes must be ${NOTES_MAX.toLocaleString()} characters or fewer.`)
      return
    }
    try {
      await updateNotes.mutateAsync(value)
      toast.success('Notes saved')
    } catch (e) {
      const msg = e?.message || 'Could not save notes'
      setError(msg)
      toast.error(msg)
    }
  }

  const onCancel = () => {
    setValue(original)
    setError('')
  }

  const backHref = `/app/activities/${groupId}`

  return (
    <div className="mx-auto max-w-3xl animate-fade-up pb-32 md:pb-8">
      <Link
        to={backHref}
        className="inline-flex items-center gap-1.5 text-muted hover:text-ink text-[14px]"
      >
        <ArrowLeft size={16} /> Back to {group.name}
      </Link>

      <header className="mt-4">
        <div className="label-caps">Group notes</div>
        <h1 className="mt-3 font-editorial text-[36px] sm:text-[48px] leading-[1.05]">
          Keep the shared context, in one place.
        </h1>
      </header>

      <Card className="mt-8 p-5 sm:p-7" tone="paper">
        <Label htmlFor="notes" hint={`${value.length.toLocaleString()}/${NOTES_MAX.toLocaleString()}`}>
          Notes
        </Label>
        <Textarea
          id="notes"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="Trip plans, addresses, passwords for the WiFi… whatever the group needs."
          className="mt-2 min-h-[280px] sm:min-h-[360px]"
          invalid={tooLong}
          data-testid="group-notes-textarea"
        />
        <Helper>
          Everyone in this group can read and edit these notes. Plain text works best.
        </Helper>
        {group.notesUpdatedAt && (
          <p className="mt-3 text-[12.5px] text-muted">
            Last updated {timeAgo(group.notesUpdatedAt)}
            {authorName ? ` by ${authorName}` : ''} · {formatDate(group.notesUpdatedAt, 'short')}
          </p>
        )}
        {error && (
          <div className="mt-4 flex items-center gap-2 rounded-input border border-negative/25 bg-negative/[0.06] text-negative px-3 py-2 text-[13px]">
            <AlertCircle size={14} />
            <span>{error}</span>
          </div>
        )}
      </Card>

      <div className="hidden md:flex mt-6 items-center gap-2 justify-end">
        <Button variant="ghost" onClick={onCancel} disabled={!dirty || updateNotes.isPending}>
          Cancel
        </Button>
        <Button
          variant="primary"
          onClick={onSave}
          disabled={!dirty || tooLong || updateNotes.isPending}
          data-testid="group-notes-save"
        >
          {updateNotes.isPending ? 'Saving…' : 'Save notes'}
        </Button>
      </div>

      <div className="md:hidden fixed bottom-0 inset-x-0 z-20 bg-cream/95 backdrop-blur-md hairline-t px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom,0px))]">
        <div className="mx-auto max-w-3xl flex items-center gap-2">
          <Button
            variant="ghost"
            onClick={onCancel}
            disabled={!dirty || updateNotes.isPending}
            className="flex-1"
          >
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={onSave}
            disabled={!dirty || tooLong || updateNotes.isPending}
            className="flex-1"
            data-testid="group-notes-save-mobile"
          >
            {updateNotes.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </div>
    </div>
  )
}
