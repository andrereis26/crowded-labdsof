import {
  Building2,
  History,
  Link as LinkIcon,
  Settings,
  ShieldCheck,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Avatar } from '../ui/components'
import { Sidebar, TopBar, type NavItem } from './Shell'

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
      <Sidebar
        label="Administração"
        nav={nav}
        user={{ initials: 'RA', name: 'Rita Almeida', role: 'Administradora' }}
        identity={
          <>
            <Avatar initials="CR" size={30} className="bg-primary text-white" />
            <div className="min-w-0">
              <p className="text-xs font-semibold text-white">Administração</p>
              <p className="text-[11px] font-medium text-sidebar-accent">
                Plataforma Crowded
              </p>
            </div>
          </>
        }
      />

      <TopBar>
        <h1 className="text-base font-semibold">{title}</h1>
      </TopBar>

      <main className="mx-auto max-w-[1176px] px-4 py-4 lg:px-8 lg:py-9">
        {children}
      </main>
    </div>
  )
}
