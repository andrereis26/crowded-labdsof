import { Bell, DoorClosed, Info, Megaphone, Pencil, Sparkles, TriangleAlert, Users } from 'lucide-react'
import { Link } from 'react-router'
import {
  ago,
  connected,
  formatNumber,
  incidentTag,
  isStale,
  reportsLabel,
  type Incident,
  type LiveState,
  type OrgEvent,
  type QueuePoint,
} from '../../data/organizer'
import {
  ButtonLink,
  ConfidenceBadge,
  CrowdMeter,
  IconTile,
  Notice,
  PageHeader,
  Panel,
  ProgressBar,
  StatCard,
  Tag,
  TextLink,
} from '../../ui/components'
import { crowdLevels } from '../../ui/styles'
import { categoryTile, incidentIcon } from '../../ui/categories'

/** Wait time that fills a queue bar. */
const FULL_QUEUE_MINUTES = 40

/** O7 · Dashboard ao vivo. */
export function LivePage({
  event,
  live,
  incidents,
  now,
}: {
  event: OrgEvent
  live?: LiveState
  incidents: Incident[]
  now: Date
}) {
  const actions = (
    <>
      <ButtonLink to="/organizacao/ao-vivo/lotacao" variant="secondary" icon={Pencil}>
        <span className="max-sm:hidden">Atualizar lotação e filas</span>
        <span className="sm:hidden">Lotação e filas</span>
      </ButtonLink>
      <ButtonLink to="/organizacao/alertas" icon={Megaphone}>
        Enviar alerta
      </ButtonLink>
    </>
  )

  if (!live) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeader title="Ao vivo" description={event.name} />
        <Notice icon={Info} title="Este evento não está a decorrer">
          O painel ao vivo mostra lotação, filas e incidentes de {event.name} durante o evento.
        </Notice>
      </div>
    )
  }

  const open = incidents.filter((i) => i.status !== 'resolved')
  const urgent = open.filter((i) => i.urgent).length
  const openQueues = live.queues.filter((q) => q.open && q.minutes > 0)
  const longest = openQueues.reduce<QueuePoint | undefined>(
    (max, q) => (!max || q.minutes > max.minutes ? q : max),
    undefined,
  )
  const ratio = Math.round((live.entries / event.capacity) * 100)
  const recent = [...incidents].sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
  const slowAi = live.services.find((s) => s.name === 'Rascunhos com IA' && !s.ok)

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title="Ao vivo"
        description="Atualizado há 30 s"
        actions={<div className="flex gap-2.5 max-sm:hidden">{actions}</div>}
      />

      <div className="grid grid-cols-2 gap-2.5 lg:grid-cols-4 lg:gap-4">
        <StatCard
          icon={Users}
          label="Lotação"
          value={`${crowdLevels[live.level].label} · ${ratio}%`}
          note={`${formatNumber(live.entries)} de ${formatNumber(event.capacity)}`}
        >
          <CrowdMeter level={live.level} stretch />
        </StatCard>
        <StatCard
          icon={DoorClosed}
          label="Fila mais longa"
          value={longest ? `${longest.name} · ${longest.minutes} min` : '—'}
          note={longest?.rising ? 'A aumentar' : undefined}
          noteClassName="font-medium text-warning"
        />
        <StatCard
          icon={TriangleAlert}
          iconClassName="text-danger"
          label="Incidentes abertos"
          value={open.length}
          note={urgent ? `${urgent} urgente${urgent > 1 ? 's' : ''}` : 'Nenhum urgente'}
          noteClassName={urgent ? 'font-medium text-danger' : 'text-ink-secondary'}
        />
        <StatCard
          icon={Bell}
          label="Pessoas ligadas"
          value={formatNumber(connected(event))}
          note={`+${live.connectedLastHour} na última hora`}
        />
      </div>

      <div className="grid grid-cols-2 gap-2.5 sm:hidden">{actions}</div>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
        <Panel
          title="Entradas por hora"
          titleClassName="text-base"
          className="min-w-0 flex-1 max-lg:hidden"
          action={
            <Tag tone="success" dot>
              há 1 min
            </Tag>
          }
        >
          <HourlyChart data={live.hourly} />
        </Panel>
        <Panel
          title="Filas por ponto"
          titleClassName="text-base"
          className="gap-3.5 max-lg:hidden lg:w-[400px] lg:shrink-0"
          action={<TextLink to="/organizacao/ao-vivo/lotacao">Editar</TextLink>}
        >
          {live.queues
            .filter((q) => q.open)
            .map((q) => (
              <QueueBar key={q.id} queue={q} stale={isStale(q, now)} />
            ))}
        </Panel>
      </div>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
        <Panel
          title="Incidentes recentes"
          titleClassName="text-base"
          className="min-w-0 flex-1 gap-0.5"
          action={<TextLink to="/organizacao/incidentes">Ver todos ({open.length})</TextLink>}
        >
          <ul>
            {recent.slice(0, 3).map((i) => (
              <IncidentRow key={i.id} incident={i} now={now} />
            ))}
          </ul>
        </Panel>
        <Panel title="Estado dos serviços" className="max-lg:hidden lg:w-[400px] lg:shrink-0">
          <ul className="flex flex-col gap-2.5">
            {live.services.map((s) => (
              <li key={s.name} className="flex items-center gap-2.5 text-sm">
                <span
                  className={`size-2 shrink-0 rounded-full ${s.ok ? 'bg-crowd-low' : 'bg-crowd-moderate'}`}
                  aria-hidden
                />
                <span className="flex-1 font-medium">{s.name}</span>
                <span className={`text-xs ${s.ok ? 'text-ink-secondary' : 'text-warning'}`}>{s.detail}</span>
              </li>
            ))}
          </ul>
        </Panel>
        {slowAi && (
          <p className="flex items-center gap-2 rounded-[14px] bg-warning-soft px-3.5 py-3 text-[13px] font-medium text-warning lg:hidden">
            <Sparkles size={16} aria-hidden />
            Rascunhos com IA lentos
          </p>
        )}
      </div>
    </div>
  )
}

/** Bar chart of entries per hour; the busiest hour is highlighted. */
export function HourlyChart({ data }: { data: { hour: string; count: number }[] }) {
  const max = Math.max(...data.map((d) => d.count), 1)
  return (
    <figure className="m-0">
      <div className="flex h-[174px] items-end gap-3.5" aria-hidden>
        {data.map((d) => (
          <div key={d.hour} className="flex min-w-0 flex-1 flex-col items-center gap-1.5">
            <div
              className={`w-full rounded ${d.count === max ? 'bg-primary' : 'bg-chart-soft'}`}
              style={{ height: Math.max(4, (d.count / max) * 143) }}
            />
            <span className="text-[11px] text-ink-secondary">{d.hour}</span>
          </div>
        ))}
      </div>
      <figcaption className="sr-only">
        Entradas por hora: {data.map((d) => `${d.hour}, ${d.count}`).join('; ')}.
      </figcaption>
    </figure>
  )
}

function QueueBar({ queue: q, stale }: { queue: QueuePoint; stale: boolean }) {
  const color = stale ? 'bg-muted-bar' : q.rising ? 'bg-crowd-moderate' : 'bg-crowd-low'
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <p className="flex items-center gap-1.5 text-sm font-medium">
          {q.name}
          {q.rising && <Tag tone="warning">A aumentar</Tag>}
          {stale && <Tag tone="warning">Desatualizado</Tag>}
        </p>
        <p className={`text-sm font-semibold ${q.rising ? 'text-warning' : ''}`}>~{q.minutes} min</p>
      </div>
      <ProgressBar value={q.minutes / FULL_QUEUE_MINUTES} className={color} />
    </div>
  )
}

export function IncidentRow({ incident: i, now, note }: { incident: Incident; now: Date; note?: string }) {
  const tag = incidentTag(i)
  const when =
    i.status === 'resolved' && i.resolvedAt ? `resolvido ${ago(i.resolvedAt, now)}` : ago(i.createdAt, now)
  return (
    <li className="border-b border-border last:border-b-0">
      <Link to={`/organizacao/incidentes/${i.id}`} className="flex items-center gap-3 py-3 hover:opacity-80">
        <IconTile icon={incidentIcon(i)} className={categoryTile[i.category]} />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold">{i.title}</p>
          <div className="mt-[3px] flex flex-wrap items-center gap-1.5 text-xs text-ink-secondary">
            {note ?? `${reportsLabel(i.reports.length)} · ${when}`}
            <ConfidenceBadge level={i.confidence} />
          </div>
        </div>
        <Tag tone={tag.tone}>{tag.label}</Tag>
      </Link>
    </li>
  )
}
