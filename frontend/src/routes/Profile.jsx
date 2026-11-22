import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { LogOut, ArrowUpRight, Camera, Pencil, Check, X, QrCode, Upload, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth } from '../auth/AuthContext.jsx'
import { useActivities, useUpdateMe, useUploadMyPicture, useUploadMyPaymentQr, useDeleteMyPaymentQr } from '../api/hooks.js'
import { detectQr } from '../util/qr.js'
import { Avatar } from '../components/Avatar.jsx'
import { Button, IconButton } from '../components/Button.jsx'
import { Card } from '../components/Card.jsx'
import { Input } from '../components/Field.jsx'
import { GroupTypeTag } from '../components/GroupTypeTag.jsx'
import { Pill } from '../components/Pill.jsx'
import upiLogo from '../assets/upi-logo.svg'
import { Skeleton } from '../components/Skeleton.jsx'
import { EmptyState } from '../components/EmptyState.jsx'
import McpConnectCard from './McpConnectCard.jsx'

const MAX_PICTURE_BYTES = 4 * 1024 * 1024
// UPI VPA: handle@psp, e.g. name@okhdfcbank
const UPI_RE = /^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$/

export default function Profile() {
  const { user, logout, refresh } = useAuth()
  const { data, isLoading } = useActivities()
  const updateMe = useUpdateMe()
  const uploadPicture = useUploadMyPicture()
  const uploadQr = useUploadMyPaymentQr()
  const deleteQr = useDeleteMyPaymentQr()

  const [editingName, setEditingName] = useState(false)
  const [draftName, setDraftName] = useState('')
  const [editingUpi, setEditingUpi] = useState(false)
  const [draftUpi, setDraftUpi] = useState('')
  const [scanningQr, setScanningQr] = useState(false)
  const fileRef = useRef(null)
  const qrRef = useRef(null)

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

  const startUpiEdit = () => {
    setDraftUpi(user?.upiId || '')
    setEditingUpi(true)
  }

  const cancelUpiEdit = () => {
    setEditingUpi(false)
    setDraftUpi('')
  }

  const saveUpi = async () => {
    const trimmed = draftUpi.trim()
    if (trimmed && !UPI_RE.test(trimmed)) {
      toast.error('That doesn’t look like a UPI ID (e.g. name@okhdfcbank).')
      return
    }
    if (trimmed === (user?.upiId || '')) {
      cancelUpiEdit()
      return
    }
    try {
      await updateMe.mutateAsync({ upiId: trimmed })
      await refresh()
      toast.success(trimmed ? 'UPI ID saved' : 'UPI ID removed')
      cancelUpiEdit()
    } catch (e) {
      toast.error(e?.message || 'Could not save UPI ID')
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

  const onPickQr = () => qrRef.current?.click()

  const onQrFileChange = async (e) => {
    const f = e.target.files?.[0]
    if (!f) return
    if (f.size > MAX_PICTURE_BYTES) {
      toast.error('QR image too large — max 4 MB.')
      e.target.value = ''
      return
    }
    if (!/^image\//.test(f.type)) {
      toast.error('Pick an image of your QR (JPG, PNG, WEBP).')
      e.target.value = ''
      return
    }
    // Make sure it actually contains a scannable QR before uploading.
    try {
      setScanningQr(true)
      const hasQr = await detectQr(f)
      if (hasQr === false) {
        toast.error("That image doesn't contain a QR code. Upload your payment QR.")
        e.target.value = ''
        return
      }
    } finally {
      setScanningQr(false)
    }
    try {
      await uploadQr.mutateAsync(f)
      await refresh()
      toast.success('Payment QR updated')
    } catch (err) {
      toast.error(err?.message || 'Could not upload QR')
    } finally {
      e.target.value = ''
    }
  }

  const onRemoveQr = async () => {
    try {
      await deleteQr.mutateAsync()
      await refresh()
      toast.success('Payment QR removed')
    } catch (err) {
      toast.error(err?.message || 'Could not remove QR')
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

          <div className="mt-5 border-t border-hairline pt-4">
            <div className="flex justify-center sm:justify-start">
              <img src={upiLogo} alt="UPI ID" className="h-5 w-auto" />
            </div>
            {editingUpi ? (
              <div className="mt-2 flex items-center gap-2 justify-center sm:justify-start">
                <Input
                  value={draftUpi}
                  onChange={(e) => setDraftUpi(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') { e.preventDefault(); saveUpi() }
                    if (e.key === 'Escape') { e.preventDefault(); cancelUpiEdit() }
                  }}
                  placeholder="name@okhdfcbank"
                  maxLength={257}
                  autoFocus
                  className="text-[15px]"
                  data-testid="profile-upi-input"
                />
                <IconButton onClick={saveUpi} disabled={updateMe.isPending} aria-label="Save UPI ID" data-testid="profile-upi-save">
                  <Check size={16} />
                </IconButton>
                <IconButton onClick={cancelUpiEdit} aria-label="Cancel" data-testid="profile-upi-cancel">
                  <X size={16} />
                </IconButton>
              </div>
            ) : (
              <div className="mt-2 flex items-center gap-2 justify-center sm:justify-start">
                {user?.upiId ? (
                  <span className="text-[15px] text-ink break-all" data-testid="profile-upi-value">{user.upiId}</span>
                ) : (
                  <span className="text-[14px] text-muted">Add a UPI ID so group-mates can pay you back.</span>
                )}
                <IconButton onClick={startUpiEdit} aria-label="Edit UPI ID" data-testid="profile-upi-edit">
                  <Pencil size={14} />
                </IconButton>
              </div>
            )}
          </div>
        </div>
      </Card>

      <Card className="mt-6 p-6 sm:p-8">
        <div className="flex items-center gap-2">
          <QrCode size={18} className="text-ink" />
          <h2 className="font-editorial text-[22px] leading-tight">Payment QR code</h2>
        </div>
        <p className="mt-1 text-[13.5px] text-muted">
          Upload your UPI / payment QR so group-mates can scan and pay you back in one tap.
          It shows on your profile for people who share a group with you.
        </p>

        <div className="mt-5 flex flex-col sm:flex-row items-center sm:items-start gap-5">
          {user?.paymentQr ? (
            <img
              src={user.paymentQr}
              alt="Your payment QR code"
              className="h-44 w-44 rounded-card border border-hairline object-contain bg-white p-2"
              data-testid="profile-qr-image"
            />
          ) : (
            <div className="h-44 w-44 rounded-card border border-dashed border-hairline bg-mist/50 flex flex-col items-center justify-center text-muted">
              <QrCode size={40} strokeWidth={1.25} />
              <span className="mt-2 text-[12px]">No QR yet</span>
            </div>
          )}

          <div className="flex flex-col gap-2">
            <Button
              variant="ink"
              onClick={onPickQr}
              disabled={uploadQr.isPending || scanningQr}
              data-testid="profile-qr-upload"
            >
              <Upload size={15} /> {scanningQr ? 'Checking…' : uploadQr.isPending ? 'Uploading…' : user?.paymentQr ? 'Replace QR' : 'Upload QR'}
            </Button>
            {user?.paymentQr && (
              <Button
                variant="ghost"
                onClick={onRemoveQr}
                disabled={deleteQr.isPending}
                data-testid="profile-qr-remove"
              >
                <Trash2 size={15} /> {deleteQr.isPending ? 'Removing…' : 'Remove'}
              </Button>
            )}
          </div>
          <input
            ref={qrRef}
            type="file"
            accept="image/*"
            onChange={onQrFileChange}
            className="hidden"
            data-testid="profile-qr-input"
          />
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

      <McpConnectCard />

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
