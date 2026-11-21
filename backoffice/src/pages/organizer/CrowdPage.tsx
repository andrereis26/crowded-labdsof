import { ChartNoAxesColumn, DoorClosed, Info, Send } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { eventErrorMessage, EventsApiError, type QueueWait } from '../../data/eventsApi'
import {
  ago,
  formatNumber,
  isOutdated,
  suggestedLevel,
  type LiveState,
  type OrgEvent,
  type PublishedQueue,
} from '../../data/organizer'
import {
  Button,
  CrowdMeter,
  Notice,
  PageHeader,
  Panel,
  SuccessBanner,
  Tag,
} from '../../ui/components'
import { crowdLevels, type CrowdLevel } from '../../ui/styles'
import { RadioOption, Stepper } from '../../ui/forms'

const levels = Object.keys(crowdLevels) as CrowdLevel[]

/** O5 · Atualizar lotação e filas. The crowd level and the queue times are
 * published in Core; the entry counter is still demo state. */
export function CrowdPage({
  event,
  live,
  now,
  onPublishLevel,
  onPublishQueues,
  onChange,
}: {
  event: OrgEvent
  live?: LiveState
  now: Date
  onPublishLevel: (level: CrowdLevel) => Promise<void>
  onPublishQueues: (waits: QueueWait[]) => Promise<void>
  onChange: (state: LiveState) => void
}) {
  const [level, setLevel] = useState<CrowdLevel>(event.crowd?.level ?? 'low')
  // Only the points with a time, published or set here, are published.
  const [minutes, setMinutes] = useState<Record<string, number>>(() =>
    Object.fromEntries((event.queues ?? []).map((q) => [q.point, q.minutes])),
  )
  const [message, setMessage] = useState('')
  const [failure, setFailure] = useState('')
  const [queueFailure, setQueueFailure] = useState('')
  const [sending, setSending] = useState(false)
  const [sendingQueues, setSendingQueues] = useState(false)

  if (!live) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeader title="Lotação e filas" />
        <Notice icon={Info} title="Disponível quando o evento estiver a decorrer">
          A lotação e as filas de {event.name} aparecem aqui no dia do evento, a partir do contador
          de entradas e das tuas atualizações.
        </Notice>
      </div>
    )
  }

  const suggestion = suggestedLevel(live.entries, event.capacity)
  const ratio = Math.round((live.entries / event.capacity) * 100)

  const publishLevel = async () => {
    setSending(true)
    setFailure('')
    setMessage('')
    try {
      await onPublishLevel(level)
      // The live dashboard still reads the demo state.
      onChange({ ...live, level, levelPublishedAt: now })
      setMessage(`Lotação publicada: ${crowdLevels[level].label}.`)
    } catch (error) {
      setFailure(eventErrorMessage(error instanceof EventsApiError ? error.code : 'UNKNOWN'))
    } finally {
      setSending(false)
    }
  }

  const waits = event.entrances
    .filter((point) => minutes[point] !== undefined)
    .map((point) => ({ point, minutes: minutes[point] }))

  const publishQueues = async () => {
    setSendingQueues(true)
    setQueueFailure('')
    setMessage('')
    try {
      await onPublishQueues(waits)
      setMessage('Filas publicadas.')
    } catch (error) {
      setQueueFailure(eventErrorMessage(error instanceof EventsApiError ? error.code : 'UNKNOWN'))
    } finally {
      setSendingQueues(false)
    }
  }

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title="Lotação e filas"
        actions={
          <span className="max-sm:hidden">
            <Tag tone="success" dot>
              Contador de entradas ligado
            </Tag>
          </span>
        }
      />
      <SuccessBanner>{message}</SuccessBanner>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <Panel
          title="Lotação atual"
          titleClassName="text-base"
          className="gap-3.5 lg:w-[520px] lg:shrink-0"
        >
          <div role="radiogroup" aria-label="Lotação atual" className="grid grid-cols-2 gap-2.5">
            {levels.map((l) => (
              <RadioOption
                key={l}
                name="crowd-level"
                value={l}
                checked={level === l}
                onChange={(v) => setLevel(v as CrowdLevel)}
                card
                className="flex-col !items-start !gap-2 !p-3.5"
              >
                <CrowdMeter level={l} />
                <span className="text-base font-semibold">{crowdLevels[l].label}</span>
                <span className="text-xs text-ink-secondary">{crowdLevels[l].hint}</span>
              </RadioOption>
            ))}
          </div>
          <p className="flex items-center gap-2 rounded-[10px] bg-success-soft px-3 py-2.5 text-[13px] font-medium text-success">
            <ChartNoAxesColumn size={18} className="shrink-0" aria-hidden />
            <span>
              <span className="max-sm:hidden">
                Sugestão do contador: {crowdLevels[suggestion].label} ·{' '}
              </span>
              <span className="sm:hidden">Contador: </span>
              {formatNumber(live.entries)} de {formatNumber(event.capacity)}
              <span className="max-sm:hidden"> pessoas</span> ({ratio}%)
            </span>
          </p>
          <p className="text-xs text-ink-secondary">
            {event.crowd
              ? `Publicado por ${event.crowd.publishedBy} ${ago(event.crowd.publishedAt, now)}`
              : 'Ainda não publicaste a lotação deste evento.'}
          </p>
          {failure && (
            <p role="alert" className="text-sm text-danger">
              {failure}
            </p>
          )}
          <Button size="lg" icon={Send} onClick={() => void publishLevel()} disabled={sending}>
            Publicar lotação
          </Button>
        </Panel>

        <Panel title="Filas por ponto" titleClassName="text-base" className="min-w-0 flex-1 gap-1">
          {event.entrances.length === 0 ? (
            <p className="py-2 text-sm text-ink-secondary">
              Este evento ainda não tem entradas nem pontos de fila.{' '}
              <Link
                to={`/organization/events/${event.id}/edit`}
                className="font-semibold text-ink underline"
              >
                Acrescenta-os ao editar o evento
              </Link>
              .
            </p>
          ) : (
            <>
              <ul>
                {event.entrances.map((point) => (
                  <QueueRow
                    key={point}
                    name={point}
                    published={event.queues?.find((q) => q.point === point)}
                    minutes={minutes[point]}
                    now={now}
                    onChange={(m) => setMinutes((all) => ({ ...all, [point]: m }))}
                  />
                ))}
              </ul>
              {queueFailure && (
                <p role="alert" className="pt-3 text-sm text-danger">
                  {queueFailure}
                </p>
              )}
              <div className="pt-3">
                <Button
                  size="lg"
                  icon={Send}
                  onClick={() => void publishQueues()}
                  disabled={sendingQueues || waits.length === 0}
                  className="max-sm:w-full"
                >
                  Publicar filas
                </Button>
              </div>
            </>
          )}
        </Panel>
      </div>
    </div>
  )
}

function QueueRow({
  name,
  published,
  minutes,
  now,
  onChange,
}: {
  name: string
  published?: PublishedQueue
  minutes?: number
  now: Date
  onChange: (minutes: number) => void
}) {
  return (
    <li className="flex items-center gap-3 border-b border-border py-3">
      <DoorClosed size={20} className="shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-center gap-1.5 text-[15px] font-semibold">
          {name}
          {published && isOutdated(published.publishedAt, now) && (
            <Tag tone="warning">Desatualizado</Tag>
          )}
        </p>
        <p className="mt-[3px] text-xs text-ink-secondary">
          {published ? `Manual · ${ago(published.publishedAt, now)}` : 'Sem tempo publicado'}
        </p>
      </div>
      <Stepper value={minutes ?? 0} onChange={onChange} label={`espera em ${name}`} />
    </li>
  )
}
