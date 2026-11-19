import { Building2, ChevronDown } from 'lucide-react'
import type { ReactNode } from 'react'
import { admin } from '../data/admin'
import { Avatar } from '../ui/components'
import { Sidebar, TopBar, type NavItem } from './Shell'

const nav: NavItem[] = [{ label: 'Organizações', to: '/organizations', icon: Building2 }]

/** Platform administration (A2): dark side menu and a white top bar on
 * desktop. On small screens the menu is hidden and the page shows its own
 * header. */
export function AdminLayout({
  onSignOut,
  children,
}: {
  onSignOut: () => void
  children: ReactNode
}) {
  return (
    <div className="min-h-screen lg:pl-[248px]">
      <Sidebar
        label="Administração"
        nav={nav}
        user={admin}
        onSignOut={onSignOut}
        identity={
          <>
            <Avatar initials="CR" size={30} className="bg-primary text-white" />
            <p className="min-w-0 flex-1 text-xs font-semibold text-white">Administração</p>
            <ChevronDown size={16} className="text-sidebar-text" aria-hidden />
          </>
        }
      />

      <TopBar>
        <p className="text-base font-semibold">Administração</p>
      </TopBar>

      <main className="mx-auto max-w-[1176px] px-4 py-4 lg:px-8 lg:py-9">{children}</main>
    </div>
  )
}
