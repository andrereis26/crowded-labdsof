import { Bell, DoorClosed, Info, TriangleAlert, Users } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { formatNumber, reportsLabel, summaries, type OrgEvent } from '../../data/organizer'
import {
  Button,
  ConfidenceBadge,
  IconTile,
  Notice,
  PageHeader,
  Panel,
  ProgressBar,
  StatCard,
  SuccessBanner,
  Tag,
} from '../../ui/components'
import { categoryTile, incidentIcon } from '../../ui/categories'
import { HourlyChart } from './LivePage'

const FULL_QUEUE_MINUTES = 56

const queueColor = { ok: 'bg-crowd-low', warning: 'bg-crowd-moderate', muted: 'bg-muted-bar' }

/** O14 · Resumo pós-evento. */
export function SummaryPage({ events }: { events: OrgEvent[] }) {
  const { id } = useParams()
  const [message, setMessage] = useState('')
  const event = events.find((e) => e.id === id)
  const summary = id ? summaries[id] : undefined

  if (!event) {
    return (
      <p className="text-sm text-ink-secondary">
        Não encontrámos este evento.{' '}
        <Link to="/organizacao/eventos" className="font-semibold text-primary">
          Voltar aos eventos
        </Link>
      </p>
    )
  }

  if (!summary) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeader title="Resumo pós-evento" description={event.name} />
        <Notice icon={Info} title="O resumo fica disponível depois do evento">
          Quando {event.name} terminar, juntamos aqui lotação, filas, incidentes e alertas.
        </Notice>
      </div>
    )
  }

  const s = summary
  const share = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setMessage('Ligação do resumo copiada. Partilha-a com a equipa.')
    } catch {
      setMessage('Não foi possível copiar a ligação.')
    }
  }

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title={
          <>
            <span className="max-sm:hidden">Resumo pós-evento</span>
            <span className="sm:hidden">Resumo</span>
          </>
        }
        description={`${event.name} · ${s.window}`}
        actions={
          <div className="flex gap-2.5 max-sm:hidden">
            <Button variant="secondary" onClick={() => window.print()}>
              Exportar PDF
            </Button>
            <Button onClick={share}>Partilhar com a equipa</Button>
          </div>
        }
      />
      <SuccessBanner>{message}</SuccessBanner>

      <div className="grid grid-cols-2 gap-2.5 lg:grid-cols-4 lg:gap-4">
        <StatCard
          icon={Users}
          label="Avisados antes das portas"
          value={`${Math.round((s.warnedBeforeDoors.reached / s.warnedBeforeDoors.total) * 100)}%`}
          note={`${formatNumber(s.warnedBeforeDoors.reached)} de ${formatNumber(s.warnedBeforeDoors.total)} participantes ligados`}
        />
        <StatCard
          icon={DoorClosed}
          label="Pico de lotação"
          value={`${Math.round(s.peak.ratio * 100)}% · ${s.peak.at}`}
          note={`${formatNumber(s.peak.people)} de ${formatNumber(event.capacity)} pessoas no recinto`}
        />
        <StatCard
          icon={TriangleAlert}
          iconClassName="text-danger"
          label="Incidentes"
          value={s.incidents.total}
          note={`Todos resolvidos · ${s.incidents.minutesToAcknowledge} min até reconhecer`}
        />
        <StatCard
          icon={Bell}
          label="Alertas enviados"
          value={s.alerts.total}
          note={`Todos resolvidos · ${formatNumber(s.alerts.deliveries)} entregas`}
        />
      </div>

      <div className="grid grid-cols-2 gap-2.5 sm:hidden">
        <Button variant="secondary" onClick={() => window.print()}>
          Exportar PDF
        </Button>
        <Button onClick={share}>Partilhar</Button>
      </div>

      <div className="flex flex-col gap-4 max-lg:hidden lg:flex-row lg:items-start">
        <Panel
          title="Entradas por hora"
          titleClassName="text-base"
          className="min-w-0 flex-1"
          action={
            <Tag tone="success" dot>
              Contador de entradas
            </Tag>
          }
        >
          <HourlyChart data={s.hourly} />
        </Panel>
        <Panel
          title="Fila máxima por ponto"
          titleClassName="text-base"
          className="gap-3.5 lg:w-[400px] lg:shrink-0"
        >
          {s.maxQueues.map((q) => (
            <div key={q.name} className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between gap-2">
                <p className="flex items-center gap-1.5 text-sm font-medium">
                  {q.name}
                  {q.peakAt && <Tag tone="warning">Pico às {q.peakAt}</Tag>}
                </p>
                <p
                  className={`text-sm font-semibold ${q.tone === 'warning' ? 'text-warning' : ''}`}
                >
                  ~{q.minutes} min
                </p>
              </div>
              <ProgressBar value={q.minutes / FULL_QUEUE_MINUTES} className={queueColor[q.tone]} />
            </div>
          ))}
        </Panel>
      </div>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
        <Panel
          title="Incidentes"
          titleClassName="text-base"
          className="min-w-0 flex-1 gap-0.5"
          action={
            <span className="text-[13px] font-semibold text-ink-secondary">
              {s.incidents.total} no total
            </span>
          }
        >
          <ul>
            {s.incidentList.map((i) => (
              <li
                key={i.title}
                className="flex items-center gap-3 border-b border-border py-3 last:border-b-0"
              >
                <IconTile icon={incidentIcon(i)} className={categoryTile[i.category]} />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold">{i.title}</p>
                  <div className="mt-[3px] flex flex-wrap items-center gap-1.5 text-xs text-ink-secondary">
                    {reportsLabel(i.reports)} · resolvido em {i.resolvedIn} min
                    <ConfidenceBadge level={i.confidence} />
                  </div>
                </div>
                <Tag tone="success">Resolvido</Tag>
              </li>
            ))}
          </ul>
        </Panel>
        <Panel title="Alertas enviados" className="max-lg:hidden lg:w-[400px] lg:shrink-0">
          <ul className="flex flex-col gap-2.5">
            {s.alertList.map((a) => (
              <li key={a.title} className="flex items-center gap-2.5 text-sm">
                <span className="size-2 shrink-0 rounded-full bg-crowd-low" aria-hidden />
                <span className="flex-1 font-medium">{a.title}</span>
                <span className="text-xs text-ink-secondary">
                  {a.sentAt} · resolvido às {a.resolvedAt}
                </span>
              </li>
            ))}
            <li className="flex items-center gap-2.5 text-sm">
              <span className="size-2 shrink-0 rounded-full bg-crowd-low" aria-hidden />
              <span className="flex-1 font-medium">Entregas</span>
              <span className="text-xs text-ink-secondary">
                {formatNumber(s.alerts.push)} por push · {formatNumber(s.alerts.appOnly)} só na app
              </span>
            </li>
          </ul>
        </Panel>
      </div>
    </div>
  )
}
