import { Link } from 'react-router-dom'
import { ArrowRight, Check, Coins, MapPin, Users2, Home, Sparkles, Split, Wallet, ArrowUpRight, ArrowDownRight, ArrowDown } from 'lucide-react'
import { useAuth } from '../auth/AuthContext.jsx'
import { loginWithGoogleUrl } from '../api/client.js'
import { BrandLockup } from '../components/Logo.jsx'
import { Button } from '../components/Button.jsx'
import { Pill } from '../components/Pill.jsx'
import { Money } from '../components/Money.jsx'

const GOOGLE_URL = loginWithGoogleUrl()

function GoogleG({ size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.76h3.56c2.08-1.92 3.28-4.74 3.28-8.09Z" fill="#4285F4" />
      <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.56-2.76c-.99.66-2.25 1.05-3.72 1.05-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84A11 11 0 0 0 12 23Z" fill="#34A853" />
      <path d="M5.84 14.1a6.61 6.61 0 0 1 0-4.2V7.06H2.18a11 11 0 0 0 0 9.88l3.66-2.84Z" fill="#FBBC05" />
      <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1A11 11 0 0 0 2.18 7.06l3.66 2.84C6.71 7.31 9.14 5.38 12 5.38Z" fill="#EA4335" />
    </svg>
  )
}

export default function Landing() {
  const { user } = useAuth()

  const primaryCta = user ? (
    <Button
      as={Link}
      to="/app/activities"
      variant="primary"
      size="lg"
      data-testid="landing-open-activities-button"
      className="group"
    >
      Open My Activities
      <ArrowRight size={18} className="transition-transform group-hover:translate-x-0.5" />
    </Button>
  ) : (
    <Button
      as="a"
      href={GOOGLE_URL}
      variant="primary"
      size="lg"
      data-testid="landing-sign-in-button"
      className="group"
    >
      <GoogleG /> Get started with Google
      <ArrowRight size={18} className="transition-transform group-hover:translate-x-0.5" />
    </Button>
  )

  return (
    <div className="min-h-screen bg-cream text-ink overflow-x-hidden">
      <PublicHeader />
      <main>
        <Hero primaryCta={primaryCta} />
        <ThreeShapes />
        <Features />
        <ThreeSteps />
        <DarkBand primaryCta={primaryCta} />
      </main>
      <Footer />
    </div>
  )
}

function PublicHeader() {
  const { user } = useAuth()
  return (
    <header className="sticky top-0 z-30 backdrop-blur-md bg-cream/70 hairline-b">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 h-16 flex items-center justify-between">
        <Link to="/" aria-label="SplitForce home" className="inline-flex items-center gap-2">
          <BrandLockup />
        </Link>
        {user ? (
          <Button as={Link} to="/app/activities" variant="ink" size="sm">
            Open My Activities
            <ArrowRight size={16} />
          </Button>
        ) : (
          <Button as="a" href={GOOGLE_URL} variant="ink" size="sm" data-testid="landing-header-signin">
            <GoogleG size={16} /> Sign in with Google
          </Button>
        )}
      </div>
    </header>
  )
}

function Hero({ primaryCta }) {
  return (
    <section className="relative">
      {/* Editorial blobs */}
      <div className="blob w-[520px] h-[520px] -top-32 -right-20" style={{ background: '#FFF0E7' }} />
      <div className="blob w-[320px] h-[320px] top-40 -left-24" style={{ background: '#F1ECE7' }} />
      <div className="mx-auto max-w-6xl px-4 sm:px-6 pt-14 sm:pt-24 pb-16 sm:pb-28 relative">
        <div className="grid lg:grid-cols-12 gap-10 lg:gap-16 items-center">
          <div className="lg:col-span-7">
            <div className="inline-flex items-center gap-2 mb-5">
              <Pill tone="coral">
                <Sparkles size={12} /> A quieter way to keep score
              </Pill>
            </div>
            <h1 className="font-editorial text-[52px] leading-[0.98] sm:text-[76px] lg:text-[104px] text-ink">
              Split expenses.
              <br />
              <span className="italic">Not friendships.</span>
            </h1>
            <p className="mt-6 text-[17px] sm:text-[19px] text-muted max-w-xl leading-relaxed">
              A calm, editorial way to keep tabs on trips, homes, and everyday shared money.
              Any currency, any split rule, none of the awkward math.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              {primaryCta}
              <a
                href="#inside"
                className="inline-flex items-center gap-2 h-14 px-5 rounded-full text-[15px] font-medium text-ink/80 hover:text-ink transition-colors"
              >
                See what's inside <ArrowDown size={16} />
              </a>
            </div>
            <div className="mt-8 flex items-center gap-6 text-[13px] text-muted">
              <span className="inline-flex items-center gap-2">
                <Check size={14} className="text-emerald-positive" /> Free & private
              </span>
              <span className="inline-flex items-center gap-2">
                <Check size={14} className="text-emerald-positive" /> No card required
              </span>
            </div>
          </div>

          <div className="lg:col-span-5 relative">
            <HeroVignette />
          </div>
        </div>
      </div>
    </section>
  )
}

function HeroVignette() {
  return (
    <div className="relative">
      <div className="surface-paper p-6 sm:p-7 relative overflow-hidden">
        <div className="flex items-center justify-between">
          <div>
            <div className="label-caps">Bali 2026</div>
            <div className="mt-1 font-editorial text-[26px] leading-tight">Trip · 4 people</div>
          </div>
          <Pill tone="coral">TRIP</Pill>
        </div>
        <div className="mt-5 rounded-input bg-mist p-4 flex items-center justify-between">
          <div>
            <div className="text-[11px] uppercase tracking-[0.14em] text-muted">You are owed</div>
            <div className="mt-1"><Money value={12450} currency="INR" size="lg" tone="positive" /></div>
          </div>
          <div className="text-right">
            <div className="text-[11px] uppercase tracking-[0.14em] text-muted">You owe</div>
            <div className="mt-1"><Money value={0} currency="INR" size="lg" tone="muted" /></div>
          </div>
        </div>
        <div className="mt-5 hairline-t pt-5 space-y-3">
          <ExpenseLine desc="Dinner at Silk Road" who="Aditi paid" amount={1200} />
          <ExpenseLine desc="Villa · night 3" who="Rahul paid" amount={9800} />
          <ExpenseLine desc="Airport taxi" who="You paid" amount={640} />
        </div>
      </div>
      <div className="absolute -bottom-6 -left-5 hidden sm:block">
        <div className="surface-paper px-4 py-3 flex items-center gap-3 shadow-lift">
          <div className="h-9 w-9 rounded-full bg-sage-pale text-emerald-positive inline-flex items-center justify-center">
            <ArrowUpRight size={16} />
          </div>
          <div>
            <div className="label-caps">Suggested</div>
            <div className="text-[13px] mt-0.5">Leo pays you <span className="mono font-semibold">₹3,400</span></div>
          </div>
        </div>
      </div>
    </div>
  )
}

function ExpenseLine({ desc, who, amount }) {
  return (
    <div className="flex items-center justify-between text-[14px]">
      <div>
        <div className="text-ink">{desc}</div>
        <div className="text-muted text-[12px]">{who}</div>
      </div>
      <Money value={amount} currency="INR" size="sm" />
    </div>
  )
}

function ThreeShapes() {
  const items = [
    {
      icon: <MapPin size={22} />,
      title: 'Trips',
      body: 'Villas, meals, taxis. Log it in the moment. Reconcile after last call.',
    },
    {
      icon: <Home size={22} />,
      title: 'Homes',
      body: 'Rent, groceries, the mystery Wi-Fi bill. A month at a glance, without the group chat.',
    },
    {
      icon: <Users2 size={22} />,
      title: 'Just the two of you',
      body: 'For partners, roommates, or the friend you keep buying lunch. Balanced. Kind.',
    },
  ]
  return (
    <section id="inside" className="mx-auto max-w-6xl px-4 sm:px-6 py-16 sm:py-24">
      <div className="max-w-2xl">
        <div className="label-caps">Made for</div>
        <h2 className="mt-3 font-editorial text-[40px] sm:text-[56px] leading-[1.02] text-ink">
          One app, <span className="italic">three shapes.</span>
        </h2>
        <p className="mt-4 text-[16px] text-muted max-w-lg">
          Whatever the shared thing is, it fits in here. Choose the shape at the start; the math finds you later.
        </p>
      </div>
      <div className="mt-10 grid gap-5 md:grid-cols-3">
        {items.map((it, i) => (
          <div
            key={it.title}
            className="surface-paper p-7 hover-lift"
            style={{ animationDelay: `${i * 60}ms` }}
          >
            <div className="inline-flex h-11 w-11 items-center justify-center rounded-full bg-coral-pale text-coral-hover">
              {it.icon}
            </div>
            <h3 className="mt-5 font-editorial text-[26px] leading-tight text-ink">{it.title}</h3>
            <p className="mt-2 text-[15px] text-muted leading-relaxed">{it.body}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

function Features() {
  const cells = [
    { icon: <Split size={18} />, title: 'Fair splits', body: 'Equal or exact. Nothing fancy — until you want it.' },
    { icon: <Coins size={18} />, title: 'Your currencies', body: 'INR, USD, EUR, GBP, JPY, AUD — all balanced back to your base.' },
    { icon: <Sparkles size={18} />, title: 'Categories', body: 'Food, stay, transport, fun. Enough to sort — not enough to sort by.' },
    { icon: <MapPin size={18} />, title: 'Trip-ready', body: 'Offline-friendly. Fast to log while the taxi meter climbs.' },
    { icon: <Users2 size={18} />, title: 'Shared checklists', body: 'Everyone sees the same numbers. No screenshots. No arguments.' },
    { icon: <Wallet size={18} />, title: 'Less mental math', body: 'Balances update in your base currency, live.' },
  ]
  return (
    <section className="bg-mist py-16 sm:py-24 border-y border-hairline">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <div className="flex items-end justify-between gap-6 flex-wrap">
          <div className="max-w-2xl">
            <div className="label-caps">Inside</div>
            <h2 className="mt-3 font-editorial text-[40px] sm:text-[56px] leading-[1.02] text-ink">
              Quiet financial <span className="italic">clarity.</span>
            </h2>
          </div>
          <p className="text-[15px] text-muted max-w-sm">
            The details you actually need — none of the fintech noise you don't.
          </p>
        </div>
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {cells.map((c) => (
            <div key={c.title} className="surface-paper p-6 hover-lift">
              <div className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-cream border border-hairline text-ink">
                {c.icon}
              </div>
              <h3 className="mt-4 font-editorial text-[22px] leading-tight text-ink">{c.title}</h3>
              <p className="mt-1.5 text-[14.5px] text-muted leading-relaxed">{c.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function ThreeSteps() {
  const steps = [
    { n: '01', title: 'Start a space', body: 'Trip, home, or general. Add the people. Pick your base currency.' },
    { n: '02', title: 'Log the moment', body: 'One expense, one tap. Split equal, or set exact shares.' },
    { n: '03', title: 'See the balance', body: 'A minimal-transfer plan. Settle up when you want to.' },
  ]
  return (
    <section className="mx-auto max-w-6xl px-4 sm:px-6 py-16 sm:py-24">
      <div className="max-w-2xl">
        <div className="label-caps">How it works</div>
        <h2 className="mt-3 font-editorial text-[40px] sm:text-[56px] leading-[1.02] text-ink">
          Three steps, <span className="italic">no spreadsheets.</span>
        </h2>
      </div>
      <ol className="mt-10 grid gap-5 md:grid-cols-3">
        {steps.map((s) => (
          <li key={s.n} className="surface-paper p-7 hover-lift">
            <div className="font-editorial text-[44px] italic text-coral leading-none">{s.n}</div>
            <h3 className="mt-4 font-editorial text-[26px] leading-tight">{s.title}</h3>
            <p className="mt-2 text-[15px] text-muted">{s.body}</p>
          </li>
        ))}
      </ol>
    </section>
  )
}

function DarkBand({ primaryCta }) {
  return (
    <section className="mx-auto max-w-6xl px-4 sm:px-6 pb-16 sm:pb-24">
      <div className="surface-ink relative overflow-hidden px-6 sm:px-16 py-14 sm:py-20 text-cream">
        <div className="blob w-[420px] h-[420px] -bottom-24 -right-24 opacity-70" style={{ background: '#FF5A36' }} />
        <div className="relative grid md:grid-cols-12 gap-8 items-center">
          <div className="md:col-span-8">
            <div className="label-caps text-cream/70">Ready when you are</div>
            <h2 className="mt-3 font-editorial text-[44px] sm:text-[64px] leading-[1.02]">
              A lighter way to <span className="italic">keep score.</span>
            </h2>
            <p className="mt-3 text-[16px] text-cream/80 max-w-xl">
              Sign in with Google and start your first space in under a minute.
            </p>
          </div>
          <div className="md:col-span-4 md:justify-self-end">
            {primaryCta}
          </div>
        </div>
      </div>
    </section>
  )
}

function Footer() {
  return (
    <footer className="hairline-t">
      <div className="mx-auto max-w-6xl px-4 sm:px-6 py-10 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <BrandLockup />
        <p className="text-[13px] text-muted">
          © {new Date().getFullYear()} SplitForce · Made with care.
        </p>
      </div>
    </footer>
  )
}
