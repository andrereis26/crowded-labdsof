import { Bell, LogOut, Users } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { Avatar, type Icon } from '../ui/components'

/** Pieces shared by the administrator and organizer areas: the dark side
 * menu and the white top bar of the desktop mockups. */

export interface NavItem {
  label: string
  to: string
  icon: Icon
  count?: number
}

export function Logo({ dark = false }: { dark?: boolean }) {
  return (
    <div className="flex items-center gap-2">
      <span
        className={`inline-flex rounded-lg p-1.5 ${dark ? 'bg-ink text-white' : 'bg-white text-ink'}`}
      >
        <Users size={18} aria-hidden />
      </span>
      <span className={`text-lg font-bold tracking-[-0.36px] ${dark ? 'text-ink' : 'text-white'}`}>
        Crowded
      </span>
    </div>
  )
}

export function CountBadge({ count }: { count: number }) {
  return (
    <span
      className="rounded-full bg-danger-soft px-2 py-[3px] text-[11px] font-semibold text-danger"
      aria-label={`${count} pendentes`}
    >
      {count}
    </span>
  )
}

export function Sidebar({
  label,
  identity,
  nav,
  user,
}: {
  label: string
  /** Card under the logo: the platform or the organization. */
  identity: ReactNode
  nav: NavItem[]
  user: { initials: string; name: string; role: string }
}) {
  return (
    <aside className="fixed inset-y-0 left-0 z-20 hidden w-[248px] flex-col gap-0.5 bg-sidebar px-3.5 py-5 lg:flex">
      <Logo />
      <div className="mt-[18px] mb-3.5 flex items-center gap-2.5 rounded-[10px] bg-sidebar-card p-2.5">
        {identity}
      </div>
      <nav aria-label={label} className="flex flex-col gap-0.5">
        {nav.map(({ label, to, icon: Icon, count }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-2.5 rounded-lg px-2.5 py-[9px] text-sm focus-visible:outline-2 focus-visible:outline-primary ${
                isActive
                  ? 'bg-sidebar-active font-semibold text-white'
                  : 'font-medium text-sidebar-text hover:bg-sidebar-card'
              }`
            }
          >
            <Icon size={18} aria-hidden />
            <span className="flex-1">{label}</span>
            {count ? <CountBadge count={count} /> : null}
          </NavLink>
        ))}
      </nav>
      <div className="mt-auto flex items-center gap-2.5 p-2">
        <Avatar initials={user.initials} size={30} className="bg-avatar text-primary" />
        <div className="min-w-0 flex-1">
          <p className="text-[13px] font-semibold text-white">{user.name}</p>
          <p className="truncate text-[11px] text-sidebar-muted">{user.role}</p>
        </div>
        <button
          type="button"
          aria-label="Terminar sessão"
          className="rounded-md p-1 text-sidebar-text hover:text-white"
        >
          <LogOut size={18} aria-hidden />
        </button>
      </div>
    </aside>
  )
}

export function TopBar({ children, actions }: { children: ReactNode; actions?: ReactNode }) {
  return (
    <header className="sticky top-0 z-10 hidden h-16 items-center gap-3.5 border-b border-border bg-surface px-8 lg:flex">
      {children}
      <span className="flex-1" />
      {actions}
      <button
        type="button"
        aria-label="Notificações"
        className="rounded-[10px] p-2 hover:bg-background"
      >
        <Bell size={20} aria-hidden />
      </button>
    </header>
  )
}
