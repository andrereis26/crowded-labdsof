import { ChevronRight, Code, LogOut, MessageSquare, Settings } from 'lucide-react'
import { Link } from 'react-router'
import { PageHeader, type Icon } from '../../ui/components'
import { PollIcon } from '../../ui/icons'

const items: { label: string; to: string; icon: Icon }[] = [
  { label: 'Sondagens', to: '/organizacao/sondagens', icon: PollIcon },
  { label: 'Pedidos', to: '/organizacao/pedidos', icon: MessageSquare },
  { label: 'Definições', to: '/organizacao/definicoes', icon: Settings },
  { label: 'API', to: '/organizacao/api', icon: Code },
]

/** "Mais" tab on phones: the sections that do not fit in the tab bar. */
export function MorePage() {
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Mais" />
      <ul className="overflow-hidden rounded-[14px] bg-surface">
        {items.map(({ label, to, icon: Icon }) => (
          <li key={to} className="border-b border-border last:border-b-0">
            <Link to={to} className="flex items-center gap-3 px-4 py-3.5 text-[15px] font-medium">
              <Icon size={20} aria-hidden />
              <span className="flex-1">{label}</span>
              <ChevronRight size={18} className="text-ink-secondary" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
      <button
        type="button"
        className="flex items-center gap-3 rounded-[14px] bg-surface px-4 py-3.5 text-[15px] font-medium text-danger"
      >
        <LogOut size={20} aria-hidden />
        Terminar sessão
      </button>
    </div>
  )
}
