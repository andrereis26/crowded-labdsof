import {
  Calendar,
  ChartNoAxesColumn,
  ChevronDown,
  ExternalLink,
  Megaphone,
  Menu,
  MessageSquare,
  TriangleAlert,
} from 'lucide-react'
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router'
import { config } from '../config'
import { eventSubtitle, organization, statusTag, type OrgEvent } from '../data/organizer'
import { Avatar, EventThumb, Tag } from '../ui/components'
import { buttonClass } from '../ui/styles'
import { Sidebar, TopBar, type NavItem } from './Shell'

/** Base address of the participant web app, for "Ver página pública". */
const participantUrl = config.participantUrl

export type OrganizerScope =
  /** Pages about the whole organization ("Eventos"). */
  | { kind: 'organization'; title: string }
  /** Pages about one event: the top bar shows the event selector. */
  | { kind: 'event'; title: string }

/** Organizer area (O2–O12): dark side menu and top bar on desktop; on phones a
 * compact header and a tab bar. Focused pages (forms) hide the phone chrome
 * and show their own "Cancelar · Guardar" header. */
export function OrganizerLayout({
  scope,
  focused,
  events,
  currentEvent,
  openIncidents,
  now,
  onSelectEvent,
  children,
}: {
  scope: OrganizerScope
  focused: boolean
  events: OrgEvent[]
  /** None while the organization has no events yet. */
  currentEvent?: OrgEvent
  openIncidents: number
  now: Date
  onSelectEvent: (id: string) => void
  children: ReactNode
}) {
  const nav: NavItem[] = [
    { label: 'Eventos', to: '/organization/events', icon: Calendar },
    { label: 'Ao vivo', to: '/organization/live', icon: ChartNoAxesColumn },
    {
      label: 'Incidentes',
      to: '/organization/incidents',
      icon: TriangleAlert,
      count: openIncidents,
    },
    { label: 'Alertas', to: '/organization/alerts', icon: Megaphone },
    { label: 'Pedidos', to: '/organization/requests', icon: MessageSquare },
  ]
  const tag = currentEvent && statusTag[currentEvent.status]

  return (
    <div className="min-h-screen pb-20 lg:pb-0 lg:pl-[248px]">
      <Sidebar
        label="Organização"
        nav={nav}
        user={organization.user}
        identity={
          <>
            <Avatar initials={organization.initials} size={30} />
            <p className="min-w-0 flex-1 text-xs font-semibold text-white">{organization.name}</p>
            <ChevronDown size={16} className="text-sidebar-text" aria-hidden />
          </>
        }
      />

      {scope.kind === 'organization' || !currentEvent || !tag ? (
        <TopBar>
          <h1 className="text-base font-semibold">{scope.title}</h1>
        </TopBar>
      ) : (
        <TopBar
          actions={
            <a
              href={`${participantUrl}/events`}
              target="_blank"
              rel="noreferrer"
              className={buttonClass('secondary', 'sm')}
            >
              <ExternalLink size={16} aria-hidden />
              Ver página pública
            </a>
          }
        >
          <h1 className="sr-only">{scope.title}</h1>
          <EventSelector
            events={events}
            current={currentEvent}
            subtitle={eventSubtitle(currentEvent, now)}
            onSelect={onSelectEvent}
          />
          <Tag tone={tag.tone} dot={tag.dot}>
            {tag.label}
          </Tag>
        </TopBar>
      )}

      {!focused && (
        <header className="flex items-center gap-3 px-4 pt-4 lg:hidden">
          {scope.kind === 'organization' || !currentEvent || !tag ? (
            <div className="flex min-w-0 flex-1 items-center gap-2.5">
              <Avatar initials={organization.initials} size={30} />
              <p className="min-w-0 truncate text-sm font-semibold">{organization.name}</p>
            </div>
          ) : (
            <div className="min-w-0 flex-1">
              <EventSelector
                events={events}
                current={currentEvent}
                subtitle={`${tag.label} · ${eventSubtitle(currentEvent, now).split(' · ')[0]}`}
                onSelect={onSelectEvent}
                block
              />
            </div>
          )}
          <Avatar
            initials={organization.user.initials}
            size={34}
            className="bg-avatar text-primary"
          />
        </header>
      )}

      <main className="mx-auto max-w-[1176px] px-4 py-4 lg:px-8 lg:py-9">{children}</main>

      {!focused && <TabBar openIncidents={openIncidents} />}
    </div>
  )
}

/** Event switcher of the top bar ("Seletor de evento"). */
function EventSelector({
  events,
  current,
  subtitle,
  onSelect,
  block = false,
}: {
  events: OrgEvent[]
  current: OrgEvent
  subtitle: string
  onSelect: (id: string) => void
  block?: boolean
}) {
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const root = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (
        e instanceof KeyboardEvent ? e.key === 'Escape' : !root.current?.contains(e.target as Node)
      ) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', close)
    }
  }, [open])

  return (
    <div ref={root} className={`relative ${block ? 'w-full' : ''}`}>
      <button
        type="button"
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls={menuId}
        aria-label={`Evento: ${current.name}. Mudar de evento`}
        onClick={() => setOpen((o) => !o)}
        className={`flex items-center gap-2.5 rounded-[10px] border border-border bg-surface py-1.5 pr-3 pl-1.5 text-left hover:bg-background ${block ? 'w-full' : ''}`}
      >
        <EventThumb
          image={current.image}
          gradient={current.gradient}
          size={32}
          rounded="rounded-[7px]"
        />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-semibold">{current.name}</span>
          <span className="block truncate text-[11px] text-ink-secondary">{subtitle}</span>
        </span>
        <ChevronDown size={16} aria-hidden />
      </button>
      {open && (
        <ul
          id={menuId}
          className="absolute top-full left-0 z-30 mt-1 w-full min-w-[280px] rounded-xl border border-border bg-surface p-1.5 shadow-[0_12px_40px_rgba(0,0,0,0.12)]"
        >
          {events.map((e) => (
            <li key={e.id}>
              <button
                type="button"
                aria-current={e.id === current.id ? 'true' : undefined}
                onClick={() => {
                  onSelect(e.id)
                  setOpen(false)
                }}
                className={`flex w-full items-center gap-2.5 rounded-lg p-2 text-left hover:bg-background ${e.id === current.id ? 'bg-primary-soft' : ''}`}
              >
                <EventThumb image={e.image} gradient={e.gradient} size={28} rounded="rounded-md" />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{e.name}</span>
                <Tag tone={statusTag[e.status].tone}>{statusTag[e.status].label}</Tag>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

const tabs = [
  { label: 'Eventos', to: '/organization/events', icon: Calendar },
  { label: 'Ao vivo', to: '/organization/live', icon: ChartNoAxesColumn },
  { label: 'Incidentes', to: '/organization/incidents', icon: TriangleAlert },
  { label: 'Alertas', to: '/organization/alerts', icon: Megaphone },
  { label: 'Mais', to: '/organization/more', icon: Menu },
]

/** Pages reached from "Mais" on phones. */
const morePaths = ['/organization/more', '/organization/requests']

function TabBar({ openIncidents }: { openIncidents: number }) {
  const { pathname } = useLocation()
  const inMore = morePaths.some((p) => pathname.startsWith(p))
  return (
    <nav
      aria-label="Organização (telemóvel)"
      className="fixed inset-x-0 bottom-0 z-20 flex border-t border-tab-border bg-tab-bar pb-3 backdrop-blur lg:hidden"
    >
      {tabs.map(({ label, to, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) =>
            `relative flex flex-1 flex-col items-center gap-1 pt-2 text-[11px] font-medium ${
              isActive || (to === '/organization/more' && inMore)
                ? 'text-primary'
                : 'text-ink-secondary'
            }`
          }
        >
          <span className="relative">
            <Icon size={22} aria-hidden />
            {label === 'Incidentes' && openIncidents > 0 && (
              <span
                className="absolute -top-1.5 left-3 inline-flex size-4 items-center justify-center rounded-full bg-counter text-[10px] font-semibold text-white"
                aria-label={`${openIncidents} abertos`}
              >
                {openIncidents}
              </span>
            )}
          </span>
          {label}
        </NavLink>
      ))}
    </nav>
  )
}
