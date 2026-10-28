import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiDelete, apiGet, apiPatch, apiPost, apiPut, apiUpload } from './client.js'

export const qk = {
  me: ['me'],
  activities: ['activities'],
  groups: ['groups'],
  group: (id) => ['groups', id],
  contacts: ['me', 'contacts'],
  apiTokens: ['me', 'tokens'],
}

// ---------- Me ----------
export function useMe() {
  return useQuery({
    queryKey: qk.me,
    queryFn: () => apiGet('/api/me'),
    staleTime: 60_000,
  })
}

export function useContacts() {
  return useQuery({
    queryKey: qk.contacts,
    queryFn: () => apiGet('/api/me/contacts'),
    staleTime: 5 * 60_000,
  })
}

export function useUpdateMe() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => apiPatch('/api/me', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.me })
      qc.invalidateQueries({ queryKey: qk.activities })
    },
  })
}

export function useUploadMyPicture() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file) => apiUpload('/api/me/picture', file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.me })
      qc.invalidateQueries({ queryKey: qk.activities })
    },
  })
}

// ---------- API tokens (for the MCP server) ----------
export function useApiTokens() {
  return useQuery({
    queryKey: qk.apiTokens,
    queryFn: () => apiGet('/api/me/tokens'),
  })
}

export function useCreateApiToken() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => apiPost('/api/me/tokens', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.apiTokens }),
  })
}

export function useRevokeApiToken() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id) => apiDelete(`/api/me/tokens/${encodeURIComponent(id)}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.apiTokens }),
  })
}

// ---------- Activities ----------
export function useActivities() {
  return useQuery({
    queryKey: qk.activities,
    queryFn: () => apiGet('/api/activities'),
  })
}

// ---------- Groups ----------
export function useGroup(groupId) {
  return useQuery({
    queryKey: qk.group(groupId),
    queryFn: () => apiGet(`/api/groups/${groupId}`),
    enabled: Boolean(groupId),
    // Balances change from other people's phones; keep this screen close to live so Settle isn't stale.
    refetchOnWindowFocus: 'always',
    refetchInterval: 15_000,
  })
}

export function useLightGroups() {
  return useQuery({
    queryKey: qk.groups,
    queryFn: () => apiGet('/api/groups'),
  })
}

export function useCreateGroup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => apiPost('/api/groups', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.activities })
      qc.invalidateQueries({ queryKey: qk.groups })
    },
  })
}

export function useRenameGroup(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (name) => apiPatch(`/api/groups/${groupId}`, { name }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
      qc.invalidateQueries({ queryKey: qk.activities })
      qc.invalidateQueries({ queryKey: qk.groups })
    },
  })
}

export function useAddMembers(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (members) => apiPost(`/api/groups/${groupId}/members`, { members }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
      qc.invalidateQueries({ queryKey: qk.activities })
      qc.invalidateQueries({ queryKey: qk.contacts })
    },
  })
}

export function useUpdateGroupNotes(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (notes) => apiPatch(`/api/groups/${groupId}/notes`, { notes }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
    },
  })
}

export function useDeleteGroup(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiDelete(`/api/groups/${groupId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.activities })
      qc.invalidateQueries({ queryKey: qk.groups })
      qc.removeQueries({ queryKey: qk.group(groupId) })
    },
  })
}

// ---------- FX ----------
export function useSetFxRate(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ code, rate }) => apiPut(`/api/groups/${groupId}/fx-rates/${code}`, { rate }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
      qc.invalidateQueries({ queryKey: qk.activities })
    },
  })
}

export function useDeleteFxRate(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (code) => apiDelete(`/api/groups/${groupId}/fx-rates/${code}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
    },
  })
}

// ---------- Expenses ----------
export function useCreateExpense(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => apiPost(`/api/groups/${groupId}/expenses`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
      qc.invalidateQueries({ queryKey: qk.activities })
    },
  })
}

export function useUpdateExpense(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ expenseId, ...body }) =>
      apiPut(`/api/groups/${groupId}/expenses/${encodeURIComponent(expenseId)}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
      qc.invalidateQueries({ queryKey: qk.activities })
    },
  })
}

export function useDeleteExpense(groupId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (expenseId) => apiDelete(`/api/groups/${groupId}/expenses/${encodeURIComponent(expenseId)}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
      qc.invalidateQueries({ queryKey: qk.activities })
    },
  })
}

// ---------- Settlement (as a payment expense) ----------
// The Spring API doesn't have a dedicated settlement endpoint; we record a
// settlement as an expense of category "Payment" that transfers value between
// two members (equal split of amount between just the two participants,
// paid by the sender — resulting in the receiver's net going down by the
// amount and the sender's net going up).
export function useSettleUp(groupId) {
  const qc = useQueryClient()
  return useMutation({
    // requestId makes retries and double-taps of one confirmation record a single payment; the server also
    // rejects it if the balances no longer support it.
    mutationFn: ({ fromUserId, toUserId, amount, note, requestId }) =>
      apiPost(`/api/groups/${groupId}/settlements`, {
        fromUserId,
        toUserId,
        amount,
        note: note || 'Settlement',
        date: new Date().toISOString().slice(0, 10),
        requestId,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.group(groupId) })
      qc.invalidateQueries({ queryKey: qk.activities })
    },
  })
}

// ---------- Receipts ----------
export function useUploadReceipt(groupId) {
  return useMutation({
    mutationFn: (file) => apiUpload(`/api/groups/${groupId}/receipts`, file),
  })
}

// ---------- Logout ----------
export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiPost('/api/logout'),
    onSuccess: () => {
      qc.clear()
    },
  })
}
