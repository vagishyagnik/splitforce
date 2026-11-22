import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowUpRight, ArrowLeft, Copy, QrCode, Maximize2 } from 'lucide-react'
import { toast } from 'sonner'
import { useUserProfile } from '../api/hooks.js'
import { Avatar } from '../components/Avatar.jsx'
import { Card } from '../components/Card.jsx'
import { Pill } from '../components/Pill.jsx'
import { Modal } from '../components/Modal.jsx'
import { GroupTypeTag } from '../components/GroupTypeTag.jsx'
import { Skeleton } from '../components/Skeleton.jsx'
import { EmptyState } from '../components/EmptyState.jsx'
import { Button, IconButton } from '../components/Button.jsx'

// Deters casual saving of the picture. A browser can never truly prevent it,
// so this is a courtesy signal, not a security control.
function blockSave(e) {
  e.preventDefault()
}

function ProtectedAvatar({ name, picture }) {
  if (!picture) return <Avatar name={name} size="xl" />
  return (
    <span className="relative inline-block select-none" onContextMenu={blockSave}>
      <img
        src={picture}
        alt={name}
        draggable={false}
        onDragStart={blockSave}
        className="h-20 w-20 rounded-full object-cover border border-hairline select-none pointer-events-none"
      />
    </span>
  )
}

export default function UserProfile() {
  const { userId } = useParams()
  const { data, isLoading, isError } = useUserProfile(userId)
  const [qrOpen, setQrOpen] = useState(false)

  const copyUpi = async () => {
    try {
      await navigator.clipboard.writeText(data.upiId)
      toast.success('UPI ID copied')
    } catch {
      toast.error('Could not copy')
    }
  }

  return (
    <div className="mx-auto max-w-2xl animate-fade-up">
      <Link to="/app/activities" className="inline-flex items-center gap-1.5 text-[13px] text-muted hover:text-ink">
        <ArrowLeft size={15} /> Back
      </Link>

      {isLoading ? (
        <Card className="mt-6 p-6 sm:p-8 flex items-center gap-5">
          <Skeleton className="h-20 w-20 rounded-full" />
          <div className="flex-1">
            <Skeleton className="h-6 w-1/2" />
            <Skeleton className="mt-2 h-4 w-2/3" />
          </div>
        </Card>
      ) : isError || !data ? (
        <div className="mt-6">
          <EmptyState
            title="Profile unavailable."
            description="This person isn’t in any of your groups, or the profile doesn’t exist."
            action={<Button as={Link} to="/app/activities">Back to activities</Button>}
          />
        </div>
      ) : (
        <>
          <header className="mt-6">
            <div className="label-caps">Member</div>
          </header>

          <Card className="mt-4 p-6 sm:p-8 flex flex-col sm:flex-row items-center sm:items-start gap-5 text-center sm:text-left">
            <ProtectedAvatar name={data.name} picture={data.picture} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 justify-center sm:justify-start flex-wrap">
                <div className="font-editorial text-[28px] leading-tight text-ink">{data.name}</div>
                {data.isYou && <Pill tone="sage" className="!text-[10px] !py-0.5">You</Pill>}
                {data.placeholder && <Pill tone="neutral" className="!text-[10px] !py-0.5">Not signed in</Pill>}
              </div>
              {data.email && (
                <div className="text-[14px] text-muted mt-1 break-all">{data.email}</div>
              )}

              <div className="mt-4">
                <div className="label-caps">UPI ID</div>
                {data.upiId ? (
                  <div className="mt-1.5 flex items-center gap-2 justify-center sm:justify-start">
                    <span className="text-[15px] text-ink break-all" data-testid="user-upi-value">{data.upiId}</span>
                    <IconButton onClick={copyUpi} aria-label="Copy UPI ID" data-testid="user-upi-copy">
                      <Copy size={14} />
                    </IconButton>
                  </div>
                ) : (
                  <div className="mt-1.5 text-[14px] text-muted">Not shared.</div>
                )}
              </div>
            </div>
          </Card>

          {data.paymentQr && (
            <section className="mt-8">
              <div className="flex items-center gap-2">
                <QrCode size={16} className="text-ink" />
                <h2 className="label-caps !mb-0">Scan to pay {data.name?.split(' ')[0] || ''}</h2>
              </div>
              <Card className="mt-3 p-6 flex flex-col items-center">
                <button
                  type="button"
                  onClick={() => setQrOpen(true)}
                  className="group relative rounded-card focus:outline-none focus:ring-2 focus:ring-ink/40"
                  aria-label="Enlarge QR code"
                  data-testid="user-qr-image"
                >
                  <img
                    src={data.paymentQr}
                    alt={`Payment QR code for ${data.name}`}
                    className="h-56 w-56 rounded-card border border-hairline object-contain bg-white p-2"
                  />
                  <span className="absolute inset-0 rounded-card flex items-center justify-center bg-ink/40 text-cream opacity-0 group-hover:opacity-100 transition-opacity">
                    <Maximize2 size={24} />
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => setQrOpen(true)}
                  className="mt-3 text-[13px] text-muted hover:text-ink inline-flex items-center gap-1.5"
                >
                  <Maximize2 size={14} /> Tap to enlarge for scanning
                </button>
              </Card>
            </section>
          )}

          <Modal
            open={qrOpen}
            onClose={() => setQrOpen(false)}
            title={`Scan to pay ${data.name?.split(' ')[0] || ''}`}
            size="lg"
            testId="user-qr-modal"
          >
            <div className="flex flex-col items-center">
              <img
                src={data.paymentQr}
                alt={`Payment QR code for ${data.name}`}
                className="w-full max-w-[22rem] aspect-square rounded-card border border-hairline object-contain bg-white p-3"
                data-testid="user-qr-image-large"
              />
              <p className="mt-4 text-[13.5px] text-muted text-center">
                Open any UPI app, tap scan, and point it here.
              </p>
            </div>
          </Modal>

          <section className="mt-8">
            <h2 className="label-caps">Groups you share</h2>
            <div className="mt-3 space-y-3">
              {data.commonGroups && data.commonGroups.length > 0 ? (
                data.commonGroups.map((g) => (
                  <Link
                    key={g.groupId}
                    to={`/app/activities/${g.groupId}`}
                    className="surface-paper p-5 flex items-center justify-between gap-4 hover-lift group"
                  >
                    <div className="min-w-0">
                      <div className="font-editorial text-[20px] leading-tight text-ink truncate">{g.name}</div>
                      <div className="mt-1.5 flex items-center gap-2">
                        <GroupTypeTag type={g.type} />
                        <Pill tone="outline">{g.currency}</Pill>
                      </div>
                    </div>
                    <ArrowUpRight size={18} className="text-muted opacity-0 group-hover:opacity-100 transition-opacity" />
                  </Link>
                ))
              ) : (
                <div className="text-[14px] text-muted">No shared groups.</div>
              )}
            </div>
          </section>
        </>
      )}
    </div>
  )
}
