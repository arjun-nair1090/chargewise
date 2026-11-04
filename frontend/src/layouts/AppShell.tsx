import {
  BatteryCharging, Car, ChartColumn, ClipboardList, Dices, FlaskConical, Gauge, History, ListOrdered, LogOut, MapPin,
  Menu, Network, Timer, X, type LucideIcon,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/authContext'
import { BackendStatus } from '../components/BackendStatus'
import { Brand } from '../components/Brand'
import type { Role } from '../types/api'

type NavItem = { to: string; label: string; icon: LucideIcon; end?: boolean }
type NavGroup = { label?: string; items: NavItem[] }

const NAV: Record<Role, NavGroup[]> = {
  DRIVER: [{
    items: [
      { to: '/driver/stations', label: 'Find a station', icon: MapPin },
      { to: '/driver/bookings', label: 'My requests', icon: ClipboardList },
      { to: '/driver/garage', label: 'Garage', icon: Car },
      { to: '/driver/history', label: 'Session history', icon: History },
    ],
  }],
  OPERATOR: [
    {
      label: 'Network',
      items: [
        { to: '/operator', label: 'Overview', icon: Gauge, end: true },
        { to: '/operator/allocation', label: 'Allocation', icon: Network },
        { to: '/operator/requests', label: 'Request queue', icon: ListOrdered },
        { to: '/operator/sessions', label: 'Sessions', icon: BatteryCharging },
        { to: '/operator/stations', label: 'Stations', icon: MapPin },
      ],
    },
    {
      label: 'Evidence',
      items: [
        { to: '/operator/analytics', label: 'Analytics', icon: ChartColumn },
        { to: '/operator/lab', label: 'Algorithm Lab', icon: FlaskConical },
        { to: '/operator/simulations', label: 'Simulations', icon: Dices },
        { to: '/operator/benchmarks', label: 'Benchmarks', icon: Timer },
      ],
    },
  ],
}

const AREA: Record<Role, string> = { DRIVER: 'Driver portal', OPERATOR: 'Operator console' }

function navClass({ isActive }: { isActive: boolean }) {
  return `flex h-9 items-center gap-3 rounded-md px-3 text-sm transition-colors duration-150 ${
    isActive ? 'bg-surface-raised font-medium text-text' : 'text-muted hover:bg-surface hover:text-text'
  }`
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  const letters = (parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')
  return letters.toUpperCase() || '?'
}

export function AppShell() {
  const { user, signOut } = useAuth()
  const location = useLocation()
  const [menuOpenAt, setMenuOpenAt] = useState<string | null>(null)
  // The mobile menu closes itself on navigation because it is tied to the path it was opened on.
  const menuOpen = menuOpenAt === location.pathname
  if (user === null) {
    return null
  }

  const nav = (
    <nav aria-label={AREA[user.role]} className="flex flex-col gap-6">
      {NAV[user.role].map((group, index) => (
        <div key={group.label ?? index} className="flex flex-col gap-0.5">
          {group.label && <p className="px-3 pb-1.5 text-xs font-medium text-muted">{group.label}</p>}
          {group.items.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={navClass}>
              <Icon aria-hidden="true" className="size-4 shrink-0" strokeWidth={1.75} />
              {label}
            </NavLink>
          ))}
        </div>
      ))}
    </nav>
  )

  const account = (
    <div className="flex flex-col gap-3 border-t border-border pt-4 text-sm">
      <BackendStatus />
      <div className="flex items-center gap-3">
        <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-md bg-surface-raised text-xs font-semibold">
          {initials(user.name)}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{user.name}</p>
          <p className="truncate text-xs text-muted">{user.email}</p>
        </div>
        <button
          type="button"
          onClick={signOut}
          aria-label="Sign out"
          title="Sign out"
          className="grid size-10 shrink-0 place-items-center rounded-md text-muted transition-colors duration-150 hover:bg-surface-raised hover:text-text"
        >
          <LogOut aria-hidden="true" className="size-4" strokeWidth={1.75} />
        </button>
      </div>
    </div>
  )

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[1000] focus:rounded-md focus:bg-accent focus:px-3 focus:py-2 focus:text-accent-ink">
        Skip to content
      </a>

      <aside className="sticky top-0 hidden h-dvh flex-col gap-8 border-r border-border bg-bg px-4 py-6 lg:flex">
        <div className="px-1">
          <Brand />
          <p className="mt-1 pl-[2.375rem] text-xs text-muted">{AREA[user.role]}</p>
        </div>
        <div className="flex-1 overflow-y-auto">{nav}</div>
        {account}
      </aside>

      <header className="sticky top-0 z-[500] border-b border-border bg-bg lg:hidden">
        <div className="flex h-14 items-center justify-between px-4">
          <div className="flex min-w-0 items-center gap-3">
            <Brand />
            <span className="truncate text-xs text-muted">{AREA[user.role]}</span>
          </div>
          <button
            type="button"
            aria-expanded={menuOpen}
            aria-controls="mobile-nav"
            onClick={() => setMenuOpenAt(menuOpen ? null : location.pathname)}
            className="inline-flex h-10 items-center gap-2 rounded-md border border-border-strong px-3 text-sm"
          >
            {menuOpen ? <X aria-hidden="true" className="size-4" /> : <Menu aria-hidden="true" className="size-4" />}
            {menuOpen ? 'Close' : 'Menu'}
          </button>
        </div>
        {menuOpen && (
          <div id="mobile-nav" className="flex flex-col gap-4 border-t border-border px-4 py-4">
            {nav}
            {account}
          </div>
        )}
      </header>

      <main id="main" className="mx-auto w-full max-w-[90rem] min-w-0 px-4 py-6 sm:px-8 sm:py-8">
        <Outlet />
      </main>
    </div>
  )
}
