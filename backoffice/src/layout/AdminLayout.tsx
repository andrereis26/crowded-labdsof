import {
  Bell,
  Building2,
  History,
  Link as LinkIcon,
  LogOut,
  Settings,
  ShieldCheck,
  Users,
  type LucideIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { Avatar } from '../ui/components'

interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  count?: number
}

/** Administrator area: dark side menu on desktop and a white top bar with the
 * page title (A1 desktop). On small screens the menu is hidden and pages show
 * their own back link (A1 mobile). */
export function AdminLayout({
  title,
  pendingVerifications,
  pendingManagement,
  children,
}: {
  title: string
  pendingVerifications: number
  pendingManagement: number
  children: ReactNode
}) {
  const nav: NavItem[] = [
    {
      label: 'Verificações',
      to: '/verificacoes',
      icon: ShieldCheck,
      count: pendingVerifications,
    },
    { label: 'Organizações', to: '/organizacoes', icon: Building2 },
    {
      label: 'Pedidos de gestão',
      to: '/pedidos-de-gestao',
      icon: LinkIcon,
      count: pendingManagement,
    },
    { label: 'Auditoria', to: '/auditoria', icon: History },
    { label: 'Definições', to: '/definicoes', icon: Settings },
  ]

  return (
    <div className="min-h-screen lg:pl-[248px]">
      <aside className="fixed inset-y-0 left-0 hidden w-[248px] flex-col gap-0.5 bg-sidebar px-3.5 py-5 lg:flex">
        <div className="flex items-center gap-2">
          <span className="inline-flex rounded-lg bg-white p-1.5">
            <Users size={18} aria-hidden />
          </span>
          <span className="text-lg font-bold tracking-[-0.36px] text-white">
            Crowded
          </span>
        </div>
        <div className="mt-[18px] mb-3.5 flex items-center gap-2.5 rounded-[10px] bg-sidebar-card p-2.5">
          <Avatar initials="CR" size={30} className="bg-primary text-white" />
          <div className="min-w-0">
            <p className="text-xs font-semibold text-white">Administração</p>
            <p className="text-[11px] font-medium text-sidebar-accent">
              Plataforma Crowded
            </p>
          </div>
        </div>
        <nav aria-label="Administração" className="flex flex-col gap-0.5">
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
              {count ? (
                <span
                  className="rounded-full bg-danger-soft px-2 py-[3px] text-[11px] font-semibold text-danger"
                  aria-label={`${count} pendentes`}
                >
                  {count}
                </span>
              ) : null}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto flex items-center gap-2.5 p-2">
          <Avatar initials="RA" size={30} className="bg-avatar text-primary" />
          <div className="min-w-0 flex-1">
            <p className="text-[13px] font-semibold text-white">Rita Almeida</p>
            <p className="text-[11px] text-sidebar-muted">Administradora</p>
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

      <header className="sticky top-0 z-10 hidden h-16 items-center gap-3.5 border-b border-border bg-surface px-8 lg:flex">
        <h1 className="text-base font-semibold">{title}</h1>
        <span className="flex-1" />
        <button type="button" aria-label="Notificações" className="p-1">
          <Bell size={20} aria-hidden />
        </button>
      </header>

      <main className="mx-auto max-w-[1176px] px-4 py-4 lg:px-8 lg:py-9">
        {children}
      </main>
    </div>
  )
}
