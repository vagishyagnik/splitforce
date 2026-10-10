import { NavLink, Link, Outlet, matchPath, useLocation, useNavigate } from 'react-router-dom'
import { Plus, User, ListTodo, LogOut } from 'lucide-react'
import { useAuth } from '../auth/AuthContext.jsx'
import { BrandLockup, Logo } from '../components/Logo.jsx'
import { Avatar } from '../components/Avatar.jsx'
import { Button } from '../components/Button.jsx'
import { ScrollToTop } from '../components/ScrollToTop.jsx'
import { cn } from '../util/cn.js'

export default function AppLayout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const isCreate = location.pathname.startsWith('/app/create') || location.pathname.endsWith('/add-expense')
  const isProfile = location.pathname.startsWith('/app/profile')
  const isActivities = !isCreate && !isProfile
  // On a group page the + adds an expense to that group instead of creating a group.
  const groupMatch = matchPath('/app/activities/:groupId', location.pathname)

  return (
    <div className="min-h-screen bg-cream text-ink">
      <ScrollToTop />
      <DesktopHeader user={user} onLogout={logout} />
      <MobileTopBar user={user} />
      <main className="mx-auto max-w-6xl px-4 sm:px-6 pt-4 sm:pt-8 pb-8 md:pb-16 pad-bottom-nav">
        <Outlet />
      </main>
      <MobileBottomNav
        onAdd={() =>
          navigate(groupMatch
            ? `/app/activities/${groupMatch.params.groupId}/add-expense`
            : '/app/create')
        }
        addLabel={groupMatch ? 'Add expense' : 'Add'}
        active={isCreate ? 'add' : isProfile ? 'profile' : 'activities'}
      />
    </div>
  )
}

function DesktopHeader({ user, onLogout }) {
  return (
    <header className="hidden md:block sticky top-0 z-30 backdrop-blur-md bg-cream/70 hairline-b">
      <div className="mx-auto max-w-6xl px-6 h-[68px] flex items-center justify-between gap-6">
        <Link to="/" aria-label="SplitForce home" className="inline-flex items-center gap-2">
          <BrandLockup />
        </Link>
        <nav className="flex items-center gap-1">
          <HeaderLink to="/app/activities" data-testid="nav-activities">
            <ListTodo size={16} />
            My Activities
          </HeaderLink>
          <HeaderLink to="/app/create" data-testid="nav-create">
            <Plus size={16} />
            Create
          </HeaderLink>
        </nav>
        <div className="flex items-center gap-2">
          <NavLink
            to="/app/profile"
            data-testid="nav-profile"
            className="inline-flex items-center gap-2 rounded-full pl-1 pr-4 py-1 h-11 border border-hairline bg-cream hover:border-ink/30 transition-colors"
          >
            <Avatar name={user?.name} picture={user?.picture} size="sm" />
            <span className="text-[14px] font-medium max-w-[10ch] truncate">
              {user?.name?.split(' ')[0] || 'You'}
            </span>
          </NavLink>
          <Button
            variant="ghost"
            size="sm"
            onClick={onLogout}
            data-testid="header-signout"
          >
            <LogOut size={14} /> Sign out
          </Button>
        </div>
      </div>
    </header>
  )
}

function HeaderLink({ to, children, ...rest }) {
  return (
    <NavLink
      to={to}
      end={to === '/app/activities'}
      className={({ isActive }) =>
        cn(
          'inline-flex items-center gap-1.5 rounded-full h-11 px-4 text-[14px] font-medium transition-colors',
          isActive ? 'bg-ink/[0.06] text-ink' : 'text-muted hover:text-ink hover:bg-ink/[0.04]',
        )
      }
      {...rest}
    >
      {children}
    </NavLink>
  )
}

function MobileTopBar({ user }) {
  return (
    <header className="md:hidden sticky top-0 z-30 backdrop-blur-md bg-cream/75 hairline-b">
      <div className="px-4 h-14 flex items-center justify-between">
        <Link to="/" aria-label="SplitForce home" className="inline-flex items-center gap-2">
          <Logo size={26} />
          <span className="font-editorial text-[19px] leading-none">
            Split<span className="italic">force</span>
          </span>
        </Link>
        <Link to="/app/profile" aria-label="Open profile" data-testid="mobile-open-profile">
          <Avatar name={user?.name} picture={user?.picture} size="sm" />
        </Link>
      </div>
    </header>
  )
}

function MobileBottomNav({ onAdd, addLabel, active }) {
  return (
    <nav
      aria-label="Primary"
      className="md:hidden fixed bottom-0 inset-x-0 z-30 pb-[calc(env(safe-area-inset-bottom,0px))]"
    >
      <div className="mx-3 mb-3 rounded-full bg-cream border border-hairline shadow-lift px-2 py-2 flex items-center justify-around gap-1">
        <BottomTab
          to="/app/activities"
          icon={<ListTodo size={20} />}
          label="Activities"
          testId="mobile-nav-activities"
          active={active === 'activities'}
        />
        <button
          type="button"
          onClick={onAdd}
          aria-label={addLabel}
          data-testid="mobile-nav-add"
          className={cn(
            'relative -mt-6 inline-flex h-14 w-14 items-center justify-center rounded-full bg-coral text-cream shadow-lift transition-transform hover:scale-[1.03] active:scale-[0.98]',
          )}
        >
          <Plus size={22} />
        </button>
        <BottomTab
          to="/app/profile"
          icon={<User size={20} />}
          label="Profile"
          testId="mobile-nav-profile"
          active={active === 'profile'}
        />
      </div>
    </nav>
  )
}

function BottomTab({ to, icon, label, active, testId }) {
  return (
    <NavLink
      to={to}
      end={to === '/app/activities'}
      data-testid={testId}
      className={cn(
        'flex-1 flex flex-col items-center justify-center gap-0.5 min-h-[52px] rounded-full text-[11px] font-semibold uppercase tracking-[0.1em] transition-colors',
        active ? 'text-ink' : 'text-muted',
      )}
    >
      <span className={cn(active && 'text-coral')}>{icon}</span>
      {label}
    </NavLink>
  )
}
