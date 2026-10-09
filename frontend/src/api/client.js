const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:9090'

export async function apiGet(path) {
  const res = await fetch(`${API_BASE}${path}`, { credentials: 'include' })
  if (res.status === 401) return { unauthorized: true }
  if (!res.ok) throw new Error(`GET ${path} failed: ${res.status}`)
  return res.json()
}

export async function apiPost(path, body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) throw new Error(`POST ${path} failed: ${res.status}`)
  return res.status === 204 ? null : res.json()
}

export async function apiPatch(path, body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'PATCH',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body || {}),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `PATCH ${path} failed: ${res.status}`)
  }
  return res.json()
}

export async function apiPut(path, body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body || {}),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `PUT ${path} failed: ${res.status}`)
  }
  return res.status === 204 ? null : res.json()
}

export async function apiDelete(path) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
    credentials: 'include',
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `DELETE ${path} failed: ${res.status}`)
  }
  return res.status === 204 ? null : res.json()
}

export async function apiUpload(path, file, field = 'file') {
  const fd = new FormData()
  fd.append(field, file)
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    credentials: 'include',
    body: fd,
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `Upload ${path} failed: ${res.status}`)
  }
  return res.json()
}

export function loginWithGoogleUrl() {
  return `${API_BASE}/oauth2/authorization/google`
}
