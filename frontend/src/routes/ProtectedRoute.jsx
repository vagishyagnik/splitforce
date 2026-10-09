import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { BrandLockup } from '../components/Logo.jsx'

export default function ProtectedRoute({ children }) {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <div className="min-h-screen bg-cream flex flex-col items-center justify-center gap-6 text-muted">
        <BrandLockup />
        <div className="font-editorial text-[22px] italic text-ink/70">One moment…</div>
      </div>
    )
  }
  if (!user) return <Navigate to="/" replace />
  return children
}
