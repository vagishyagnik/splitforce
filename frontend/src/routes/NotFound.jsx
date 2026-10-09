import { Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { BrandLockup } from '../components/Logo.jsx'
import { Button } from '../components/Button.jsx'

export default function NotFound() {
  return (
    <div className="min-h-screen bg-cream relative overflow-hidden">
      <div className="blob w-[480px] h-[480px] -top-24 -right-24" style={{ background: '#FFF0E7' }} />
      <div className="blob w-[320px] h-[320px] bottom-10 -left-16" style={{ background: '#F1ECE7' }} />
      <header className="relative z-10 mx-auto max-w-6xl px-4 sm:px-6 h-16 flex items-center">
        <Link to="/" aria-label="SplitForce home">
          <BrandLockup />
        </Link>
      </header>
      <main className="relative z-10 mx-auto max-w-3xl px-4 sm:px-6 pt-20 pb-24 text-center">
        <div className="label-caps">404</div>
        <h1 className="mt-4 font-editorial text-[52px] sm:text-[92px] leading-[0.98] text-ink">
          This page <span className="italic">wandered off.</span>
        </h1>
        <p className="mt-5 text-[16px] text-muted max-w-md mx-auto">
          Take the road back — your activities are safe and waiting.
        </p>
        <div className="mt-8 flex justify-center gap-3 flex-wrap">
          <Button as={Link} to="/app/activities" variant="primary">
            <ArrowLeft size={16} /> Back to activities
          </Button>
          <Button as={Link} to="/" variant="ghost">
            Go home
          </Button>
        </div>
      </main>
    </div>
  )
}
