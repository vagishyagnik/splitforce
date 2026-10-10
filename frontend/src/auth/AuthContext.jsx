import { createContext, useContext, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { apiGet, apiPost, getLastRequestAt, SESSION_EXPIRED_EVENT } from '../api/client.js'

// A little under the backend's 60-minute idle timeout, so the UI logs out before requests start failing.
const IDLE_LIMIT_MS = 59 * 60 * 1000

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    apiGet('/api/me')
      .then((data) => {
        if (cancelled) return
        if (data?.unauthorized) setUser(null)
        else setUser(data)
      })
      .catch(() => setUser(null))
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!user) return
    const expire = () => {
      apiPost('/api/logout').catch(() => {})
      setUser(null)
      toast('Signed out after an hour of inactivity', { id: 'session-expired' })
    }
    const timer = setInterval(() => {
      if (Date.now() - getLastRequestAt() > IDLE_LIMIT_MS) expire()
    }, 30_000)
    window.addEventListener(SESSION_EXPIRED_EVENT, expire)
    return () => {
      clearInterval(timer)
      window.removeEventListener(SESSION_EXPIRED_EVENT, expire)
    }
  }, [user])

  const logout = async () => {
    try {
      await apiPost('/api/logout')
    } finally {
      setUser(null)
      window.location.href = '/'
    }
  }

  const refresh = async () => {
    const data = await apiGet('/api/me')
    if (data?.unauthorized) setUser(null)
    else setUser(data)
  }

  return (
    <AuthContext.Provider value={{ user, loading, logout, refresh, setUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
