import { Calendar, ChartNoAxesColumn, ChevronRight, Megaphone, Plus, Users } from 'lucide-react'
import { Link } from 'react-router'
import {
  ago,
  connected,
  eventDateLabel,
  eventKinds,
  formatNumber,
  organization,
  statusTag,
  type OrgEvent,
  type SentAlert,
} from '../../data/organizer'
import { ButtonLink, EventThumb, PageHeader, StatCard, Tag } from '../../ui/components'

const DAY = 24 * 3600000

/** O2 · Lista de eventos (início do backoffice). */
export function EventsPage({
  events,
  alerts,
  now,
}: {
  events: OrgEvent[]
  alerts: SentAlert[]
  now: Date
}) {
  const liveEvents = events.filter((e) => e.status === 'live')
  const upcoming = events.filter((e) => e.status === 'published')
  const active = events.filter((e) => e.status !== 'ended' && e.status !== 'cancelled')
  const recentAlerts = alerts.filter((a) => now.getTime() - a.sentAt.getTime() < 7 * DAY)
  const lastAlert = recentAlerts.reduce<SentAlert | undefined>(
    (last, a) => (!last || a.sentAt > last.sentAt ? a : last),
    undefined,
  )

  const linkTo = (e: OrgEvent) => `/organization/events/${e.id}`

  return (
    <div className="flex flex-col gap-4 lg:gap-[22px]">
      <PageHeader
        title="Eventos"
        description={`${organization.name} · ${events.length} eventos`}
        actions={
          <>
            <ButtonLink
              to="/organization/events/new"
              size="lg"
              icon={Plus}
              className="max-sm:hidden"
            >
              Criar evento
            </ButtonLink>
            <Link
              to="/organization/events/new"
              aria-label="Criar evento"
              className="inline-flex size-9 items-center justify-center rounded-full bg-ink text-white sm:hidden"
            >
              <Plus size={20} aria-hidden />
            </Link>
          </>
        }
      />

      <div className="grid grid-cols-2 gap-2.5 lg:grid-cols-4 lg:gap-4">
        <StatCard
          icon={ChartNoAxesColumn}
          iconClassName="text-success"
          label="A decorrer agora"
          value={liveEvents.length}
          note={
            <span className="max-lg:hidden">
              {liveEvents.map((e) => e.name).join(' · ') || '—'}
            </span>
          }
        />
        <StatCard
          icon={Calendar}
          label="Próximos"
          value={upcoming.length}
          note={upcoming.map((e) => e.name).join(' · ') || '—'}
          className="max-lg:hidden"
        />
        <StatCard
          icon={Users}
          label="Pessoas ligadas"
          value={formatNumber(active.reduce((sum, e) => sum + connected(e), 0))}
          note={<span className="max-lg:hidden">Bilhetes e códigos de acesso</span>}
        />
        <StatCard
          icon={Megaphone}
          label="Alertas (7 dias)"
          value={recentAlerts.length}
          note={lastAlert ? `Último ${ago(lastAlert.sentAt, now)}` : 'Nenhum alerta'}
          className="max-lg:hidden"
        />
      </div>

      {/* Desktop: table. Phones: one card per event. */}
      <div className="overflow-hidden rounded-[14px] bg-surface py-1 max-lg:hidden">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-border text-[11px] font-semibold tracking-[0.44px] text-ink-secondary uppercase">
              <th className="px-5 py-2.5 font-semibold">Evento</th>
              <th className="w-[170px] py-2.5 font-semibold">Data</th>
              <th className="w-[130px] py-2.5 font-semibold">Estado</th>
              <th className="w-[90px] py-2.5 font-semibold">Ligados</th>
              <th className="w-[220px] py-2.5 font-semibold">Tipo</th>
              <th className="w-[60px] py-2.5">
                <span className="sr-only">Abrir</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {events.map((e) => {
              const tag = statusTag[e.status]
              return (
                <tr
                  key={e.id}
                  className={`relative border-b border-border hover:bg-row-highlight ${e.status === 'live' ? 'bg-row-highlight' : ''}`}
                >
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-2">
                      <EventThumb image={e.image} gradient={e.gradient} />
                      <Link
                        to={linkTo(e)}
                        className="font-semibold after:absolute after:inset-0 focus-visible:outline-2 focus-visible:outline-primary"
                      >
                        {e.name}
                      </Link>
                    </div>
                  </td>
                  <td className="py-3.5">{eventDateLabel(e, now)}</td>
                  <td className="py-3.5">
                    <Tag tone={tag.tone} dot={tag.dot}>
                      {tag.label}
                    </Tag>
                  </td>
                  <td className="py-3.5">
                    {e.status === 'ended' ? '—' : formatNumber(connected(e))}
                  </td>
                  <td className="py-3.5">{eventKinds[e.kind].listLabel}</td>
                  <td className="py-3.5 pr-5 text-right">
                    <ChevronRight size={18} className="inline text-ink-secondary" aria-hidden />
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <ul className="flex flex-col gap-2.5 lg:hidden" aria-label="Eventos">
        {events.map((e) => {
          const tag = statusTag[e.status]
          return (
            <li key={e.id}>
              <Link
                to={linkTo(e)}
                className="flex items-center gap-3 rounded-[14px] bg-surface p-2.5 pr-3.5"
              >
                <EventThumb
                  image={e.image}
                  gradient={e.gradient}
                  size={56}
                  rounded="rounded-[10px]"
                />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[15px] font-semibold">{e.name}</span>
                  <span className="block text-xs text-ink-secondary">{eventDateLabel(e, now)}</span>
                  <span className="block text-xs text-ink-secondary">
                    {e.status === 'ended' ? 'Terminado' : `${formatNumber(connected(e))} ligados`}
                    {` · ${eventKinds[e.kind].label}`}
                  </span>
                </span>
                <Tag tone={tag.tone} dot={tag.dot}>
                  {tag.label}
                </Tag>
              </Link>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
