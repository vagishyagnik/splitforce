import { useState } from 'react'
import { Check, Copy } from 'lucide-react'
import { toast } from 'sonner'
import { Card, CardHeader } from '../components/Card.jsx'

// The MCP server lives on the API origin; in production that's this site.
const apiOrigin = () => import.meta.env.VITE_API_BASE || window.location.origin
const mcpUrl = () => `${apiOrigin()}/api/mcp`

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
    <button
      type="button"
      onClick={copy}
      data-testid={testId}
      className="inline-flex items-center gap-1.5 rounded-input border border-hairline px-3 py-2 text-[13px] font-medium text-ink hover:bg-mist/60"
    >
      {copied ? <Check size={14} /> : <Copy size={14} />} {copied ? 'Copied' : label}
    </button>
  )
}

export default function McpConnectCard() {
  const url = mcpUrl()
  return (
    <Card className="mt-8 p-6 sm:p-7">
      <CardHeader
        eyebrow="For AI assistants"
        title="Connect an AI assistant"
        subtitle="Let Claude, Gemini or any MCP client read and edit your groups as you. Add the server URL below — you'll sign in with Google in the browser, so there's nothing to copy or store."
      />

      <div className="mt-5">
        <div className="label-caps mb-1.5">MCP server URL</div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div
            className="min-w-0 flex-1 rounded-input border border-hairline bg-mist/50 p-3 font-mono text-[13px] break-all select-all"
            data-testid="mcp-url"
          >
            {url}
          </div>
          <CopyButton text={url} label="Copy URL" testId="mcp-url-copy" />
        </div>
      </div>

      <div className="mt-6 space-y-5">
        <div>
          <div className="text-[15px] font-medium text-ink">Claude Code</div>
          <p className="mt-1 text-[14px] text-muted">Run this, then approve the browser sign-in:</p>
          <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
            <div className="min-w-0 flex-1 rounded-input border border-hairline bg-mist/50 p-3 font-mono text-[12.5px] break-all">
              {`claude mcp add --transport http splitforce ${url}`}
            </div>
            <CopyButton
              text={`claude mcp add --transport http splitforce ${url}`}
              label="Copy command"
              testId="mcp-cmd-copy"
            />
          </div>
        </div>

        <div>
          <div className="text-[15px] font-medium text-ink">Claude Desktop, Gemini & other clients</div>
          <ol className="mt-1 list-decimal space-y-1 pl-5 text-[14px] text-muted">
            <li>Add a new remote (HTTP) MCP server / connector.</li>
            <li>Paste the server URL above. Leave client ID and secret blank.</li>
            <li>Continue — a browser window opens for Google sign-in.</li>
            <li>Approve access, and the assistant is connected as you.</li>
          </ol>
        </div>
      </div>

      <p className="mt-6 text-[12.5px] text-muted">
        The assistant can do everything you can in the app except manage this connection. Access is a
        short-lived token refreshed automatically; sign out below to end your web session.
      </p>
    </Card>
  )
}
