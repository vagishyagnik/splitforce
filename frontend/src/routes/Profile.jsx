import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { LogOut, ArrowUpRight, Camera, Pencil, Check, X } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth } from '../auth/AuthContext.jsx'
import { useActivities, useUpdateMe, useUploadMyPicture } from '../api/hooks.js'
import { Avatar } from '../components/Avatar.jsx'
import { Button, IconButton } from '../components/Button.jsx'
import { Card } from '../components/Card.jsx'
import { Input } from '../components/Field.jsx'
import { GroupTypeTag } from '../components/GroupTypeTag.jsx'
import { Pill } from '../components/Pill.jsx'
import { Skeleton } from '../components/Skeleton.jsx'
import { EmptyState } from '../components/EmptyState.jsx'

const MAX_PICTURE_BYTES = 4 * 1024 * 1024

export default function Profile() {
  const { user, logout, refresh } = useAuth()
  const { data, isLoading } = useActivities()
  const updateMe = useUpdateMe()
  const uploadPicture = useUploadMyPicture()

  const [editingName, setEditingName] = useState(false)
  const [draftName, setDraftName] = useState('')
  const fileRef = useRef(null)

  const startNameEdit = () => {
    setDraftName(user?.name || '')
    setEditingName(true)
  }

  const cancelNameEdit = () => {
    setEditingName(false)
    setDraftName('')
  }

  const saveName = async () => {
    const trimmed = draftName.trim()
    if (!trimmed) {
      toast.error('Name cannot be blank.')
      return
    }
    if (trimmed === user?.name) {
      cancelNameEdit()
      return
    }
    try {
      await updateMe.mutateAsync({ name: trimmed })
      await refresh()
      toast.success('Name updated')
      cancelNameEdit()
    } catch (e) {
      toast.error(e?.message || 'Could not update name')
    }
  }

  const onPickImage = () => fileRef.current?.click()

  const onFileChange = async (e) => {
    const f = e.target.files?.[0]
    if (!f) return
    if (f.size > MAX_PICTURE_BYTES) {
      toast.error('Picture too large — max 4 MB.')
      e.target.value = ''
      return
    }
    if (!/^image\//.test(f.type)) {
      toast.error('Pick an image (JPG, PNG, WEBP).')
      e.target.value = ''
      return
    }
    try {
      await uploadPicture.mutateAsync(f)
      await refresh()
      toast.success('Picture updated')
    } catch (err) {
      toast.error(err?.message || 'Could not upload picture')
    } finally {
      e.target.value = ''
    }
  }

  const onLogout = async () => {
    try {
      await logout()
      toast.success('Signed out')
    } catch (e) {
      toast.error(e?.message || 'Sign out failed')
    }
  }

  return (
    <div className="mx-auto max-w-2xl animate-fade-up">
      <header>
        <div className="label-caps">Your account</div>
        <h1 className="mt-3 font-editorial text-[40px] sm:text-[56px] leading-[1.02]">
          Profile
        </h1>
      </header>

      <Card className="mt-8 p-6 sm:p-8 flex flex-col sm:flex-row items-center sm:items-start gap-5 text-center sm:text-left">
        <button
          type="button"
          onClick={onPickImage}
          disabled={uploadPicture.isPending}
          className="relative group rounded-full focus:outline-none focus:ring-2 focus:ring-ink/40"
          aria-label="Change profile picture"
          data-testid="profile-picture-button"
        >
          <Avatar name={user?.name} picture={user?.picture} size="xl" />
          <span className="absolute inset-0 rounded-full flex items-center justify-center bg-ink/50 text-cream opacity-0 group-hover:opacity-100 transition-opacity">
            <Camera size={22} />
          </span>
          {uploadPicture.isPending && (
            <span className="absolute inset-0 rounded-full flex items-center justify-center bg-ink/60 text-cream text-[12px]">
              Uploading…
            </span>
          )}
        </button>
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          onChange={onFileChange}
          className="hidden"
          data-testid="profile-picture-input"
        />

        <div className="min-w-0 flex-1">
          {editingName ? (
            <div className="flex items-center gap-2 justify-center sm:justify-start">
              <Input
                value={draftName}
                onChange={(e) => setDraftName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') { e.preventDefault(); saveName() }
                  if (e.key === 'Escape') { e.preventDefault(); cancelNameEdit() }
                }}
                maxLength={60}
                autoFocus
                className="text-[20px] font-editorial"
                data-testid="profile-name-input"
              />
              <IconButton
                onClick={saveName}
                disabled={updateMe.isPending}
                aria-label="Save name"
                data-testid="profile-name-save"
              >
                <Check size={16} />
              </IconButton>
              <IconButton
                onClick={cancelNameEdit}
                aria-label="Cancel"
                data-testid="profile-name-cancel"
              >
                <X size={16} />
              </IconButton>
            </div>
          ) : (
            <div className="flex items-center gap-2 justify-center sm:justify-start">
              <div className="font-editorial text-[28px] leading-tight text-ink">{user?.name}</div>
              <IconButton
                onClick={startNameEdit}
                aria-label="Edit name"
                data-testid="profile-name-edit"
              >
                <Pencil size={14} />
              </IconButton>
            </div>
          )}
          <div className="text-[14px] text-muted mt-1 break-all">{user?.email}</div>
          <div className="mt-3 inline-flex">
            <Pill tone="sage">Signed in with Google</Pill>
          </div>
        </div>
      </Card>

      <section className="mt-8">
        <h2 className="label-caps">Your groups</h2>
        <div className="mt-3 space-y-3">
          {isLoading ? (
            Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="surface-paper p-5 flex items-center justify-between">
                <div className="flex-1">
                  <Skeleton className="h-4 w-1/3" />
                  <Skeleton className="mt-2 h-3 w-1/5" />
                </div>
                <Skeleton className="h-8 w-16 rounded-full" />
              </div>
            ))
          ) : data && data.length > 0 ? (
            data.map((g) => (
              <Link
                key={g.groupId}
                to={`/app/activities/${g.groupId}`}
                className="surface-paper p-5 flex items-center justify-between gap-4 hover-lift group"
              >
                <div className="min-w-0">
                  <div className="font-editorial text-[20px] leading-tight text-ink truncate">
                    {g.name}
                  </div>
                  <div className="mt-1.5 flex items-center gap-2">
                    <GroupTypeTag type={g.type} />
                    <Pill tone="outline">{g.currency}</Pill>
                  </div>
                </div>
                <ArrowUpRight size={18} className="text-muted opacity-0 group-hover:opacity-100 transition-opacity" />
              </Link>
            ))
          ) : (
            <EmptyState
              title="No groups yet."
              description="Start a trip, a home, or something in between."
              action={
                <Button as={Link} to="/app/create">
                  Create a group
                </Button>
              }
            />
          )}
        </div>
      </section>

      <div className="mt-10">
        <Button
          variant="danger"
          size="lg"
          className="w-full"
          onClick={onLogout}
          data-testid="profile-sign-out-button"
        >
          <LogOut size={16} /> Sign out
        </Button>
      </div>
    </div>
  )
}
