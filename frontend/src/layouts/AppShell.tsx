import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/authContext'
import { BackendStatus } from '../components/BackendStatus'
import { DataBadge } from '../components/StatusChip'
import type { Role } from '../types/api'

type NavItem = { to: string; label: string; end?: boolean }

const NAV: Record<Role, NavItem[]> = {
  DRIVER: [
    { to: '/driver/stations', label: 'Find a station' },
    { to: '/driver/bookings', label: 'My requests' },
    { to: '/driver/garage', label: 'Garage' },
    { to: '/driver/history', label: 'Session history' },
  ],
  OPERATOR: [
    { to: '/operator', label: 'Overview', end: true },
    { to: '/operator/allocation', label: 'Allocation' },
    { to: '/operator/requests', label: 'Request queue' },
    { to: '/operator/sessions', label: 'Sessions' },
    { to: '/operator/stations', label: 'Stations' },
    { to: '/operator/analytics', label: 'Analytics' },
    { to: '/operator/lab', label: 'Algorithm Lab' },
    { to: '/operator/simulations', label: 'Simulations' },
    { to: '/operator/benchmarks', label: 'Benchmarks' },
  ],
}

const AREA: Record<Role, string> = { DRIVER: 'Driver portal', OPERATOR: 'Operator console' }

function navClass({ isActive }: { isActive: boolean }) {
  return `flex h-9 items-center rounded-md px-3 text-sm transition-colors duration-150 ${
    isActive ? 'bg-surface-raised font-medium text-text' : 'text-muted hover:bg-surface hover:text-text'
  }`
}

function Brand() {
  return (
    <span className="text-lg font-semibold tracking-tight">
      Charge<span className="text-accent">Wise</span>
    </span>
  )
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
    <nav aria-label={AREA[user.role]} className="flex flex-col gap-0.5">
      {NAV[user.role].map((item) => (
        <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  )

  const account = (
    <div className="flex flex-col gap-3 border-t border-border pt-4 text-sm">
      <DataBadge kind="synthetic" />
      <BackendStatus />
      <div className="min-w-0">
        <p className="truncate font-medium">{user.name}</p>
        <p className="truncate text-xs text-muted">{user.email}</p>
      </div>
      <button type="button" onClick={signOut} className="self-start text-sm text-muted underline hover:text-text">
        Sign out
      </button>
    </div>
  )

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[1000] focus:rounded-md focus:bg-accent focus:px-3 focus:py-2 focus:text-accent-ink">
        Skip to content
      </a>

      <aside className="sticky top-0 hidden h-dvh flex-col gap-6 border-r border-border bg-bg px-4 py-5 lg:flex">
        <div>
          <Brand />
          <p className="mt-0.5 text-xs text-muted">{AREA[user.role]}</p>
        </div>
        <div className="flex-1 overflow-y-auto">{nav}</div>
        {account}
      </aside>

      <header className="sticky top-0 z-[500] border-b border-border bg-bg lg:hidden">
        <div className="flex h-14 items-center justify-between px-4">
          <div className="flex items-baseline gap-2">
            <Brand />
            <span className="text-xs text-muted">{AREA[user.role]}</span>
          </div>
          <button
            type="button"
            aria-expanded={menuOpen}
            aria-controls="mobile-nav"
            onClick={() => setMenuOpenAt(menuOpen ? null : location.pathname)}
            className="h-9 rounded-md border border-border-strong px-3 text-sm"
          >
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
