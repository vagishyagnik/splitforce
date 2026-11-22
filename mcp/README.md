# SplitForce MCP

Lets Claude (or any MCP client) work with your SplitForce groups: check balances, search expenses,
log and edit expenses, record settlements, and manage groups.

There are two ways to connect:

- **Remote HTTP (recommended)** — the MCP server is built into the SplitForce backend at
  `POST /api/mcp`. Clients authenticate with OAuth 2.1 (Google login in the browser), so there is no
  token to copy or store. This is the easiest option and works from any MCP client that supports
  remote/HTTP servers.
- **Local stdio** — the Node server in `src/index.js`, authenticated with a personal access token.
  Still supported; useful for scripting or clients without remote-server support.

## Remote HTTP (OAuth)

Point your client at the endpoint and let it run the OAuth flow — it will open a browser for Google
login the first time and cache the resulting token.

**Claude Code**

```sh
claude mcp add --transport http splitforce https://d3o02dydsxmyrd.cloudfront.net/api/mcp
```

**Claude Desktop / other clients** — add a remote (HTTP) MCP server with URL
`https://d3o02dydsxmyrd.cloudfront.net/api/mcp`. When the client connects it receives a `401` with a
`WWW-Authenticate` challenge pointing at the discovery document, registers itself dynamically, and
walks you through Google login. No manual client id or secret.

Under the hood the backend implements:

- `GET /.well-known/oauth-protected-resource` (RFC 9728) and
  `GET /.well-known/oauth-authorization-server` (RFC 8414) — discovery.
- `POST /oauth2/register` (RFC 7591) — dynamic client registration (public PKCE clients).
- `GET /oauth2/authorize` + `POST /oauth2/token` — Authorization Code + PKCE (S256 required).
- `GET /oauth2/jwks` — the RSA public key used to verify access tokens.

Access tokens are short-lived RS256 JWTs validated locally by the backend; the MCP endpoint is the
token audience. The transport runs in stateless JSON mode (one JSON-RPC request/response per POST),
because the Lambda runtime buffers responses and cannot stream SSE.

## Local stdio (personal access token)

1. `cd mcp && npm install`
2. In SplitForce, open **Profile → API tokens** and create a token. It's shown once.
3. Register the server with your client.

**Claude Code**

```sh
claude mcp add splitforce \
  -e SPLITFORCE_TOKEN=sf_... \
  -e SPLITFORCE_URL=https://d3o02dydsxmyrd.cloudfront.net \
  -- node /absolute/path/to/splitforce/mcp/src/index.js
```

**Claude Desktop** (`~/Library/Application Support/Claude/claude_desktop_config.json`)

```json
{
  "mcpServers": {
    "splitforce": {
      "command": "node",
      "args": ["/absolute/path/to/splitforce/mcp/src/index.js"],
      "env": {
        "SPLITFORCE_TOKEN": "sf_...",
        "SPLITFORCE_URL": "https://d3o02dydsxmyrd.cloudfront.net"
      }
    }
  }
}
```

`SPLITFORCE_URL` defaults to production; use `http://localhost:9090` against a local backend.

## Tools

Both transports expose the same tools. Groups can be named by id or (part of) their name, and people
by name, email, user id or "me".

| Tool | What it does |
|---|---|
| `whoami` | The account you're connected as |
| `list_groups` | Your groups with your net balance in each |
| `get_group` | Members, balances, settle-up plan, FX rates, notes |
| `list_expenses` | Expenses, filterable by person, payer, category, dates, text |
| `list_contacts` | People you share groups with |
| `export_group_csv` | The group as CSV |
| `add_expense` | Log an expense (equal, percent or exact-amount split) |
| `update_expense` | Change any fields of an expense |
| `delete_expense` | Delete an expense or settlement |
| `record_settlement` | Record that one member paid another back |
| `create_group`, `rename_group`, `add_members`, `update_group_notes` | Group admin |
| `set_fx_rate`, `remove_fx_rate` | Manage a group's currencies |
| `delete_group` | Delete a settled group (owner only) |

## Authorization

- Whether over OAuth or a personal access token, the connection acts as you, with everything you can
  do in the web app except managing tokens/clients.
- **OAuth:** access is a short-lived JWT; revoke a client's access by rotating the signing key.
- **Personal access tokens:** only a SHA-256 of the token is stored. Revoke it under
  Profile → API tokens and it stops working immediately. At most 10 per user, with an expiry of
  30 days, 90 days, a year, or none.
