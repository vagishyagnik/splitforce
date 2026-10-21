#!/usr/bin/env node
// SplitForce MCP server (stdio). Talks to the SplitForce API with a personal access token.
//   SPLITFORCE_TOKEN  required; create one under Profile → API tokens
//   SPLITFORCE_URL    defaults to the production CloudFront URL
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { z } from 'zod'

const BASE_URL = (process.env.SPLITFORCE_URL || 'https://d3o02dydsxmyrd.cloudfront.net').replace(/\/+$/, '')
const TOKEN = process.env.SPLITFORCE_TOKEN

if (!TOKEN) {
  console.error('SPLITFORCE_TOKEN is not set. Create a token in SplitForce under Profile → API tokens.')
  process.exit(1)
}

// ---------- HTTP ----------

async function api(method, path, body) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 401) throw new Error('SplitForce rejected the token (expired or revoked). Create a new one under Profile → API tokens.')
  const text = await res.text()
  if (!res.ok) {
    let message = text
    // Spring omits the reason from error bodies; the status text is the best it gives.
    try { const err = JSON.parse(text); message = err.message || err.error || message } catch { /* not JSON */ }
    throw new Error(`${method} ${path} failed (${res.status})${message ? `: ${message}` : ''}`)
  }
  if (!text) return null
  return (res.headers.get('content-type') || '').includes('json') ? JSON.parse(text) : text
}

const enc = encodeURIComponent

// ---------- Lookups: let the model refer to groups and people by name ----------

async function resolveGroupId(ref) {
  const groups = await api('GET', '/api/groups')
  const exact = groups.find((g) => g.groupId === ref)
  if (exact) return exact.groupId
  const needle = ref.trim().toLowerCase()
  const byName = groups.filter((g) => g.name.toLowerCase() === needle)
  const matches = byName.length ? byName : groups.filter((g) => g.name.toLowerCase().includes(needle))
  if (matches.length === 1) return matches[0].groupId
  const names = groups.map((g) => `"${g.name}"`).join(', ')
  throw new Error(matches.length
    ? `"${ref}" matches several groups: ${matches.map((g) => `"${g.name}"`).join(', ')}. Be more specific.`
    : `No group matches "${ref}". Your groups: ${names || 'none'}.`)
}

async function loadGroup(ref) {
  return api('GET', `/api/groups/${enc(await resolveGroupId(ref))}`)
}

function resolveMember(group, ref, { activeOnly = false } = {}) {
  const members = activeOnly ? group.members.filter((m) => m.active) : group.members
  const needle = ref.trim().toLowerCase()
  if (['me', 'i', 'you', 'myself'].includes(needle)) {
    const me = members.find((m) => m.you)
    if (me) return me
  }
  const exact = members.filter((m) =>
    m.userId === ref || m.displayName.toLowerCase() === needle || (m.email || '').toLowerCase() === needle)
  const matches = exact.length ? exact : members.filter((m) => m.displayName.toLowerCase().includes(needle))
  if (matches.length === 1) return matches[0]
  const names = members.map((m) => m.displayName).join(', ')
  throw new Error(matches.length
    ? `"${ref}" matches several members of ${group.name}: ${matches.map((m) => m.displayName).join(', ')}.`
    : `No ${activeOnly ? 'active ' : ''}member of ${group.name} matches "${ref}". Members: ${names}.`)
}

const nameOf = (group, userId) => group.members.find((m) => m.userId === userId)?.displayName || userId

const isSettlement = (e) => Boolean(e.payment) || e.category === 'Payment'

function expenseView(group, e) {
  // shares are net per person: what they paid minus what they owe.
  const owed = {}
  for (const [id, net] of Object.entries(e.shares || {})) {
    const o = (id === e.paidByUserId ? Number(e.cost) : 0) - Number(net)
    if (Math.abs(o) > 0.004) owed[nameOf(group, id)] = Number(o.toFixed(2))
  }
  if (e.paidByUserId && !(e.paidByUserId in (e.shares || {}))) {
    // Payer with a zero net isn't in shares but still owes their full payment back to themselves.
    owed[nameOf(group, e.paidByUserId)] = Number(Number(e.cost).toFixed(2))
  }
  return {
    expenseId: e.expenseId,
    date: e.date,
    description: e.description,
    category: e.category,
    settlement: isSettlement(e),
    cost: e.cost,
    currency: e.currency,
    paidBy: nameOf(group, e.paidByUserId),
    owedBy: owed,
    splitMode: e.splitMode,
    notes: e.notes || undefined,
    hasReceipt: Boolean(e.receiptKey),
  }
}

function groupSummary(group) {
  return {
    groupId: group.groupId,
    name: group.name,
    type: group.type,
    baseCurrency: group.baseCurrency,
    fxRates: group.fxRates,
    members: group.members.map((m) => ({
      userId: m.userId,
      name: m.displayName,
      email: m.placeholder ? undefined : m.email,
      you: m.you,
      active: m.active,
      net: m.net,
    })),
    balanceNote: `net is in ${group.baseCurrency}: positive = the group owes them, negative = they owe the group.`,
    settleUpPlan: group.settlement.map((s) => ({ from: s.fromName, to: s.toName, amount: s.amount })),
    expenseCount: group.expenses.length,
    notes: group.notes || undefined,
  }
}

// Turns tool split input into the API's participants list.
function buildSplit(group, split, { allowedIds } = {}) {
  const mode = split?.mode || 'equal'
  let entries = split?.participants
  if (!entries || entries.length === 0) {
    if (mode !== 'equal') throw new Error('participants are required for percent and amount splits.')
    entries = group.members.filter((m) => m.active).map((m) => ({ member: m.userId }))
  }
  const participants = entries.map((p) => {
    const m = allowedIds?.has(p.member)
      ? group.members.find((x) => x.userId === p.member)
      : resolveMember(group, p.member, { activeOnly: !allowedIds })
    if (mode !== 'equal' && (p.value === undefined || p.value === null)) {
      throw new Error(`${m.displayName} needs a value for a ${mode} split.`)
    }
    return { userId: m.userId, value: mode === 'equal' ? undefined : p.value }
  })
  return { splitMode: mode, participants }
}

// ---------- Tool plumbing ----------

const server = new McpServer({ name: 'splitforce', version: '0.0.1' })

const json = (value) => ({ content: [{ type: 'text', text: typeof value === 'string' ? value : JSON.stringify(value, null, 2) }] })

function tool(name, { title, description, input = {}, readOnly = false, destructive = false }, handler) {
  server.registerTool(name, {
    title,
    description,
    inputSchema: input,
    annotations: { title, readOnlyHint: readOnly, destructiveHint: destructive, openWorldHint: false },
  }, async (args) => {
    try {
      return json(await handler(args))
    } catch (err) {
      return { isError: true, content: [{ type: 'text', text: err.message }] }
    }
  })
}

const groupRef = z.string().describe('Group id or (part of) the group name')
const memberRef = z.string().describe('Member name, email, user id, or "me"')
const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'YYYY-MM-DD')
const currency = z.string().length(3).describe('ISO currency code; defaults to the group base currency. Other currencies need an FX rate on the group.')
const splitSchema = z.object({
  mode: z.enum(['equal', 'percent', 'amount']).default('equal'),
  participants: z.array(z.object({
    member: memberRef,
    value: z.number().nonnegative().optional().describe('Percent (percent mode) or amount owed (amount mode); omit for equal'),
  })).optional().describe('Who shares the cost. Omit for an equal split across all active members.'),
}).describe('How the cost is shared. Percentages must sum to 100; amounts must sum to the cost.')

// ---------- Read tools ----------

tool('whoami', {
  title: 'Current user',
  description: 'The SplitForce account this token belongs to.',
  readOnly: true,
}, () => api('GET', '/api/me'))

tool('list_groups', {
  title: 'List groups',
  description: 'All groups you belong to, with member/expense counts, last activity and your net balance in each (base currency; positive = you are owed).',
  readOnly: true,
}, () => api('GET', '/api/activities'))

tool('get_group', {
  title: 'Group details',
  description: 'Members with net balances, the minimum-payments settle-up plan, FX rates and notes for one group. Use list_expenses for the expenses themselves.',
  input: { group: groupRef },
  readOnly: true,
}, async ({ group }) => groupSummary(await loadGroup(group)))

tool('list_expenses', {
  title: 'List expenses',
  description: 'Expenses and settlements in a group, newest first, with who paid and who owes what. All filters are optional and combine.',
  input: {
    group: groupRef,
    person: memberRef.optional().describe('Only expenses this member paid for or has a share of'),
    paidBy: memberRef.optional().describe('Only expenses this member paid'),
    category: z.string().optional().describe('Exact category, e.g. "Dining out" or "Payment" for settlements'),
    from: date.optional().describe('On or after this date'),
    to: date.optional().describe('On or before this date'),
    search: z.string().optional().describe('Case-insensitive match on description, category or notes'),
    includeSettlements: z.boolean().default(true),
    limit: z.number().int().min(1).max(500).default(50),
  },
  readOnly: true,
}, async ({ group: ref, person, paidBy, category, from, to, search, includeSettlements, limit }) => {
  const group = await loadGroup(ref)
  const personId = person && resolveMember(group, person).userId
  const payerId = paidBy && resolveMember(group, paidBy).userId
  const q = search?.toLowerCase()
  const matched = group.expenses.filter((e) => {
    if (!includeSettlements && isSettlement(e)) return false
    // Zero shares are dropped server-side, so being in shares means paying or owing part of it.
    if (personId && e.paidByUserId !== personId && !(personId in (e.shares || {}))) return false
    if (payerId && e.paidByUserId !== payerId) return false
    if (category && (e.category || '').toLowerCase() !== category.toLowerCase()) return false
    if (from && e.date < from) return false
    if (to && e.date > to) return false
    if (q && ![e.description, e.category, e.notes].some((s) => (s || '').toLowerCase().includes(q))) return false
    return true
  })
  return {
    group: group.name,
    baseCurrency: group.baseCurrency,
    total: matched.length,
    returned: Math.min(limit, matched.length),
    expenses: matched.slice(0, limit).map((e) => expenseView(group, e)),
  }
})

tool('list_contacts', {
  title: 'List contacts',
  description: 'People you share groups with (useful for adding them to a new group by email).',
  readOnly: true,
}, () => api('GET', '/api/me/contacts'))

tool('export_group_csv', {
  title: 'Export group CSV',
  description: 'The whole group as CSV: one row per expense with each member\'s net in the base currency.',
  input: { group: groupRef },
  readOnly: true,
}, async ({ group }) => api('GET', `/api/groups/${enc(await resolveGroupId(group))}/export`))

// ---------- Expenses ----------

tool('add_expense', {
  title: 'Add expense',
  description: 'Log an expense in a group. Defaults: paid by you, today, base currency, split equally across all active members.',
  input: {
    group: groupRef,
    description: z.string().max(140),
    cost: z.number().positive(),
    currency: currency.optional(),
    date: date.optional().describe('Defaults to today'),
    category: z.string().max(40).optional().describe('e.g. "Dining out", "Groceries", "Rideshare / Taxis", "Hotel"'),
    notes: z.string().max(1000).optional(),
    paidBy: memberRef.optional(),
    split: splitSchema.optional(),
  },
}, async ({ group: ref, description, cost, currency: cur, date: d, category, notes, paidBy, split }) => {
  const group = await loadGroup(ref)
  const payer = paidBy ? resolveMember(group, paidBy, { activeOnly: true }) : group.members.find((m) => m.you)
  const body = {
    description,
    cost,
    currency: (cur || group.baseCurrency).toUpperCase(),
    date: d,
    category,
    notes,
    paidByUserId: payer.userId,
    ...buildSplit(group, split),
  }
  const created = await api('POST', `/api/groups/${enc(group.groupId)}/expenses`, body)
  const fresh = await api('GET', `/api/groups/${enc(group.groupId)}`)
  const e = fresh.expenses.find((x) => x.expenseId === created.expenseId)
  return { added: e ? expenseView(fresh, e) : created }
})

tool('update_expense', {
  title: 'Edit expense',
  description: 'Change any fields of an existing expense; omitted fields keep their current value. Giving split replaces the whole split.',
  input: {
    group: groupRef,
    expenseId: z.string(),
    description: z.string().max(140).optional(),
    cost: z.number().positive().optional(),
    currency: currency.optional(),
    date: date.optional(),
    category: z.string().max(40).optional().describe('Empty string clears it'),
    notes: z.string().max(1000).optional().describe('Empty string clears it'),
    paidBy: memberRef.optional(),
    split: splitSchema.optional(),
  },
}, async ({ group: ref, expenseId, split, paidBy, ...changes }) => {
  const group = await loadGroup(ref)
  const old = group.expenses.find((e) => e.expenseId === expenseId)
  if (!old) throw new Error(`No expense ${expenseId} in ${group.name}. Use list_expenses to find the id.`)
  // People already on the expense stay valid even if they've left the group.
  const onExpense = new Set([...Object.keys(old.shares || {}), ...Object.keys(old.splitValues || {}), old.paidByUserId])

  const costChanged = changes.cost !== undefined && Number(changes.cost) !== Number(old.cost)
  let splitBody
  if (split) {
    splitBody = buildSplit(group, split, { allowedIds: onExpense })
  } else if (old.splitMode && old.splitValues) {
    if (old.splitMode === 'amount' && costChanged) {
      throw new Error('This expense is split by exact amounts, so a new cost needs a new split.')
    }
    splitBody = {
      splitMode: old.splitMode,
      participants: Object.entries(old.splitValues).map(([userId, value]) =>
        ({ userId, value: old.splitMode === 'equal' ? undefined : Number(value) })),
    }
  } else {
    // Older expenses only have net shares; rebuild the split as the exact amounts owed.
    if (costChanged) throw new Error('This expense predates saved splits, so a new cost needs a new split.')
    const participants = []
    for (const id of onExpense) {
      const owed = (id === old.paidByUserId ? Number(old.cost) : 0) - Number(old.shares?.[id] || 0)
      if (owed > 0.004) participants.push({ userId: id, value: Number(owed.toFixed(2)) })
    }
    splitBody = { splitMode: 'amount', participants }
  }

  let paidByUserId = old.paidByUserId
  if (paidBy) {
    const m = onExpense.has(paidBy) ? { userId: paidBy } : resolveMember(group, paidBy)
    paidByUserId = m.userId
  }
  const body = {
    description: changes.description ?? old.description,
    cost: changes.cost ?? old.cost,
    currency: (changes.currency ?? old.currency).toUpperCase(),
    date: changes.date ?? old.date,
    category: changes.category ?? old.category,
    notes: changes.notes ?? old.notes,
    receiptKey: old.receiptKey,
    paidByUserId,
    ...splitBody,
  }
  const saved = await api('PUT', `/api/groups/${enc(group.groupId)}/expenses/${enc(expenseId)}`, body)
  const fresh = await api('GET', `/api/groups/${enc(group.groupId)}`)
  const e = fresh.expenses.find((x) => x.expenseId === saved.expenseId)
  return { updated: e ? expenseView(fresh, e) : saved }
})

tool('delete_expense', {
  title: 'Delete expense',
  description: 'Permanently delete an expense or settlement. Confirm with the user first.',
  input: { group: groupRef, expenseId: z.string() },
  destructive: true,
}, async ({ group, expenseId }) => {
  const groupId = await resolveGroupId(group)
  await api('DELETE', `/api/groups/${enc(groupId)}/expenses/${enc(expenseId)}`)
  return { deleted: expenseId }
})

tool('record_settlement', {
  title: 'Record settlement',
  description: 'Record that one member paid another back (e.g. a step from the settle-up plan in get_group).',
  input: {
    group: groupRef,
    from: memberRef.describe('Who paid'),
    to: memberRef.describe('Who received the money'),
    amount: z.number().positive(),
    currency: currency.optional(),
    date: date.optional().describe('Defaults to today'),
    note: z.string().max(140).optional(),
  },
}, async ({ group: ref, from, to, amount, currency: cur, date: d, note }) => {
  const group = await loadGroup(ref)
  const payer = resolveMember(group, from, { activeOnly: true })
  const receiver = resolveMember(group, to, { activeOnly: true })
  if (payer.userId === receiver.userId) throw new Error('from and to must be different people.')
  // Same shape the web app uses: a "Payment" expense paid by the sender, owed entirely by the receiver.
  const created = await api('POST', `/api/groups/${enc(group.groupId)}/expenses`, {
    description: note || 'Settlement',
    date: d,
    category: 'Payment',
    cost: amount,
    currency: (cur || group.baseCurrency).toUpperCase(),
    paidByUserId: payer.userId,
    splitMode: 'amount',
    participants: [{ userId: receiver.userId, value: amount }],
  })
  const fresh = await api('GET', `/api/groups/${enc(group.groupId)}`)
  return {
    recorded: { expenseId: created.expenseId, from: payer.displayName, to: receiver.displayName, amount, currency: cur || group.baseCurrency },
    settleUpPlan: fresh.settlement.map((s) => ({ from: s.fromName, to: s.toName, amount: s.amount })),
  }
})

// ---------- Group admin ----------

const memberInput = z.object({
  name: z.string().max(60).optional(),
  email: z.string().email().optional().describe('Links the member to their Google account so they see the group'),
}).refine((m) => m.name || m.email, 'name or email required')

tool('create_group', {
  title: 'Create group',
  description: 'Create a group. You are added automatically; list the other members.',
  input: {
    name: z.string().min(1).max(80),
    type: z.enum(['trip', 'home', 'general']),
    baseCurrency: z.string().length(3),
    members: z.array(memberInput).default([]),
  },
}, ({ name, type, baseCurrency, members }) => api('POST', '/api/groups', {
  name,
  type,
  baseCurrency: baseCurrency.toUpperCase(),
  members: members.map((m) => ({ displayName: m.name || '', email: m.email || '' })),
}))

tool('rename_group', {
  title: 'Rename group',
  description: 'Change a group\'s name.',
  input: { group: groupRef, name: z.string().min(1).max(80) },
}, async ({ group, name }) => api('PATCH', `/api/groups/${enc(await resolveGroupId(group))}`, { name }))

tool('add_members', {
  title: 'Add members',
  description: 'Add people to a group. Members already in it are skipped.',
  input: { group: groupRef, members: z.array(memberInput).min(1) },
}, async ({ group, members }) => api('POST', `/api/groups/${enc(await resolveGroupId(group))}/members`, {
  members: members.map((m) => ({ displayName: m.name || '', email: m.email || '' })),
}))

tool('update_group_notes', {
  title: 'Update group notes',
  description: 'Replace the group\'s shared notes (plain text or markdown, up to 10,000 characters). Read the current notes with get_group first if appending.',
  input: { group: groupRef, notes: z.string().max(10000) },
}, async ({ group, notes }) => api('PATCH', `/api/groups/${enc(await resolveGroupId(group))}/notes`, { notes }))

tool('set_fx_rate', {
  title: 'Set FX rate',
  description: 'Set how many units of the group\'s base currency one unit of another currency is worth (e.g. USD → 84.5 in an INR group). Needed before logging expenses in that currency.',
  input: { group: groupRef, currency: z.string().length(3), rate: z.number().positive() },
}, async ({ group, currency: code, rate }) =>
  api('PUT', `/api/groups/${enc(await resolveGroupId(group))}/fx-rates/${enc(code.toUpperCase())}`, { rate }))

tool('remove_fx_rate', {
  title: 'Remove FX rate',
  description: 'Remove a currency from a group. Fails if any expense still uses it.',
  input: { group: groupRef, currency: z.string().length(3) },
  destructive: true,
}, async ({ group, currency: code }) =>
  api('DELETE', `/api/groups/${enc(await resolveGroupId(group))}/fx-rates/${enc(code.toUpperCase())}`))

tool('delete_group', {
  title: 'Delete group',
  description: 'Permanently delete a group with all its expenses. Only the owner can, and only once everyone is settled up. Confirm with the user first.',
  input: { group: groupRef },
  destructive: true,
}, async ({ group }) => {
  const groupId = await resolveGroupId(group)
  await api('DELETE', `/api/groups/${enc(groupId)}`)
  return { deleted: groupId }
})

await server.connect(new StdioServerTransport())
