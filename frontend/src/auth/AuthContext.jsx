import { createContext, useContext, useEffect, useState } from 'react'
import { apiGet, apiPost } from '../api/client.js'

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
