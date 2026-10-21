import { useState } from 'react'
import { Check, Copy, KeyRound, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { useApiTokens, useCreateApiToken, useRevokeApiToken } from '../api/hooks.js'
import { Button, IconButton } from '../components/Button.jsx'
import { Card, CardHeader } from '../components/Card.jsx'
import { Input, Select } from '../components/Field.jsx'
import { Modal } from '../components/Modal.jsx'
import { Skeleton } from '../components/Skeleton.jsx'
import { formatDate, timeAgo } from '../util/format.js'

const EXPIRY_OPTIONS = [
  { value: '30', label: '30 days' },
  { value: '90', label: '90 days' },
  { value: '365', label: '1 year' },
  { value: '', label: 'Never' },
]

// The MCP server needs the API origin; in production that's this site.
const apiOrigin = () => import.meta.env.VITE_API_BASE || window.location.origin

function mcpConfig(token) {
  return JSON.stringify({
    mcpServers: {
      splitforce: {
        command: 'node',
        args: ['/path/to/splitforce/mcp/src/index.js'],
        env: { SPLITFORCE_URL: apiOrigin(), SPLITFORCE_TOKEN: token },
      },
    },
  }, null, 2)
}

function CopyButton({ text, label, testId }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      toast.error('Could not copy; select the text instead.')
    }
  }
  return (
    <Button variant="outline" size="sm" onClick={copy} data-testid={testId}>
      {copied ? <Check size={14} /> : <Copy size={14} />} {copied ? 'Copied' : label}
    </Button>
  )
}

export default function ApiTokensCard() {
  const { data: tokens, isLoading } = useApiTokens()
  const createToken = useCreateApiToken()
  const revokeToken = useRevokeApiToken()

  const [name, setName] = useState('Claude MCP')
  const [expiry, setExpiry] = useState('90')
  const [created, setCreated] = useState(null)
  const [revokeTarget, setRevokeTarget] = useState(null)

  const onCreate = async (e) => {
    e.preventDefault()
    if (!name.trim()) {
      toast.error('Give the token a name.')
      return
    }
    try {
      const res = await createToken.mutateAsync({
        name: name.trim(),
        expiresInDays: expiry ? Number(expiry) : null,
      })
      setCreated(res)
    } catch (err) {
      toast.error(err?.message || 'Could not create token')
    }
  }

  const confirmRevoke = async () => {
    try {
      await revokeToken.mutateAsync(revokeTarget.id)
      toast.success('Token revoked')
      setRevokeTarget(null)
    } catch (err) {
      toast.error(err?.message || 'Could not revoke token')
    }
  }

  return (
    <Card className="mt-8 p-6 sm:p-7">
      <CardHeader
        eyebrow="For AI assistants"
        title="API tokens"
        subtitle="Let Claude or another MCP client read and edit your groups as you. Treat a token like a password."
      />

      <form onSubmit={onCreate} className="mt-5 grid gap-3 sm:grid-cols-[1fr_160px_auto] sm:items-end">
        <div>
          <div className="label-caps mb-1.5">Name</div>
          <Input
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={60}
            placeholder="e.g. Claude on my laptop"
            data-testid="api-token-name"
          />
        </div>
        <div>
          <div className="label-caps mb-1.5">Expires</div>
          <Select value={expiry} onChange={(e) => setExpiry(e.target.value)} data-testid="api-token-expiry">
            {EXPIRY_OPTIONS.map((o) => (
              <option key={o.label} value={o.value}>{o.label}</option>
            ))}
          </Select>
        </div>
        <Button type="submit" disabled={createToken.isPending} className="min-h-[48px]" data-testid="api-token-create">
          <KeyRound size={16} /> Create token
        </Button>
      </form>

      <div className="mt-6">
        {isLoading ? (
          <Skeleton className="h-14 w-full" />
        ) : tokens && tokens.length > 0 ? (
          <ul className="divide-y divide-hairline">
            {tokens.map((t) => (
              <li key={t.id} className="py-3 flex items-center gap-3" data-testid={`api-token-${t.id}`}>
                <div className="min-w-0 flex-1">
                  <div className="text-[15px] font-medium truncate">{t.name}</div>
                  <div className="mt-0.5 text-[12.5px] text-muted flex flex-wrap gap-x-2">
                    <span className="font-mono">sf_…{t.hint}</span>
                    <span>·</span>
                    <span>{t.lastUsedAt ? `Used ${timeAgo(t.lastUsedAt)}` : 'Never used'}</span>
                    <span>·</span>
                    <span>{t.expiresAt ? `Expires ${formatDate(t.expiresAt)}` : 'No expiry'}</span>
                  </div>
                </div>
                <IconButton
                  onClick={() => setRevokeTarget(t)}
                  label={`Revoke ${t.name}`}
                  className="h-9 w-9 text-muted hover:text-negative"
                  data-testid={`api-token-revoke-${t.id}`}
                >
                  <Trash2 size={16} />
                </IconButton>
              </li>
            ))}
          </ul>
        ) : (
          <div className="text-[14px] text-muted">No tokens yet.</div>
        )}
      </div>

      <Modal
        open={Boolean(created)}
        onClose={() => setCreated(null)}
        title="Copy your token now"
        size="lg"
        testId="api-token-created-modal"
        footer={
          <div className="flex justify-end pt-2">
            <Button onClick={() => setCreated(null)}>Done</Button>
          </div>
        }
      >
        {created && (
          <div className="space-y-4">
            <p className="text-[14px] text-muted">
              This is the only time it's shown. If you lose it, revoke it and create another.
            </p>
            <div className="rounded-input border border-hairline bg-mist/50 p-3 font-mono text-[13px] break-all select-all" data-testid="api-token-value">
              {created.token}
            </div>
            <CopyButton text={created.token} label="Copy token" testId="api-token-copy" />
            <div>
              <div className="label-caps mb-1.5">MCP client config</div>
              <pre className="rounded-input border border-hairline bg-mist/50 p-3 font-mono text-[12px] overflow-x-auto">
                {mcpConfig(created.token)}
              </pre>
              <div className="mt-2 flex flex-wrap items-center gap-3">
                <CopyButton text={mcpConfig(created.token)} label="Copy config" testId="api-token-copy-config" />
                <span className="text-[12.5px] text-muted">Point the path at your checkout of splitforce/mcp.</span>
              </div>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={Boolean(revokeTarget)}
        onClose={() => setRevokeTarget(null)}
        title="Revoke token?"
        size="sm"
        footer={
          <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={() => setRevokeTarget(null)}>Cancel</Button>
            <Button variant="danger" onClick={confirmRevoke} disabled={revokeToken.isPending} data-testid="api-token-revoke-confirm">
              Revoke
            </Button>
          </div>
        }
      >
        <p className="text-[14px] text-muted">
          Anything using <span className="font-medium text-ink">{revokeTarget?.name}</span> stops working immediately.
        </p>
      </Modal>
    </Card>
  )
}
