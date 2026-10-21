# SplitForce MCP server

Lets Claude (or any MCP client) work with your SplitForce groups: check balances, search expenses,
log and edit expenses, record settlements, and manage groups. It runs locally over stdio and calls
the SplitForce API with a personal access token.

## Setup

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

Groups can be named by id or (part of) their name, and people by name, email, user id or "me".

| Tool | What it does |
|---|---|
| `whoami` | The account the token belongs to |
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

## Tokens

- The token acts as you, with everything you can do in the web app except managing tokens.
- Only a SHA-256 of it is stored. Revoke it under Profile → API tokens and it stops working immediately.
- Tokens can be created with an expiry of 30 days, 90 days, a year, or none; at most 10 per user.
