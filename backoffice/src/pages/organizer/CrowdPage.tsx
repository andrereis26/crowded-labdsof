import { ChartNoAxesColumn, DoorClosed, Info, Send } from 'lucide-react'
import { useState } from 'react'
import {
  ago,
  formatNumber,
  isStale,
  suggestedLevel,
  type LiveState,
  type OrgEvent,
  type QueuePoint,
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
import { RadioOption, Stepper, Toggle } from '../../ui/forms'

const levels = Object.keys(crowdLevels) as CrowdLevel[]

/** O5 · Atualizar lotação e filas. */
export function CrowdPage({
  event,
  live,
  now,
  onChange,
}: {
  event: OrgEvent
  live?: LiveState
  now: Date
  onChange: (state: LiveState) => void
}) {
  const [level, setLevel] = useState<CrowdLevel>(live?.level ?? 'low')
  const [queues, setQueues] = useState<QueuePoint[]>(live?.queues ?? [])
  const [message, setMessage] = useState('')

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

  const publishLevel = () => {
    onChange({ ...live, level, levelPublishedAt: now, queues })
    setMessage(`Lotação publicada: ${crowdLevels[level].label}.`)
  }

  const publishQueues = () => {
    const updated = queues.map((q) => (q.source === 'manual' ? { ...q, updatedAt: now } : q))
    setQueues(updated)
    onChange({ ...live, queues: updated })
    setMessage('Filas publicadas.')
  }

  const updateQueue = (id: string, patch: Partial<QueuePoint>) =>
    setQueues((all) => all.map((q) => (q.id === id ? { ...q, ...patch } : q)))

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
            Publicado por {live.levelPublishedBy} {ago(live.levelPublishedAt, now)}
          </p>
          <Button size="lg" icon={Send} onClick={publishLevel}>
            Publicar lotação
          </Button>
        </Panel>

        <Panel
          title="Filas por ponto"
          titleClassName="text-base"
          className="min-w-0 flex-1 gap-1"
          action={
            <span className="text-xs text-ink-secondary max-sm:hidden">
              Valores automáticos vêm do contador de entradas
            </span>
          }
        >
          <ul>
            {queues.map((q) => (
              <QueueRow
                key={q.id}
                queue={q}
                now={now}
                onChange={(patch) => updateQueue(q.id, patch)}
              />
            ))}
          </ul>
          <div className="pt-3">
            <Button size="lg" icon={Send} onClick={publishQueues} className="max-sm:w-full">
              Publicar filas
            </Button>
          </div>
        </Panel>
      </div>
    </div>
  )
}

function QueueRow({
  queue: q,
  now,
  onChange,
}: {
  queue: QueuePoint
  now: Date
  onChange: (patch: Partial<QueuePoint>) => void
}) {
  const stale = isStale(q, now)
  return (
    <li className="flex items-center gap-3 border-b border-border py-3">
      <DoorClosed size={20} className="shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-center gap-1.5 text-[15px] font-semibold">
          {q.name}
          {q.rising && <Tag tone="warning">A aumentar</Tag>}
          {stale && <Tag tone="warning">Desatualizado</Tag>}
        </p>
        <p
          className={`mt-[3px] text-xs ${q.source === 'counter' ? 'text-success' : 'text-ink-secondary'}`}
        >
          {q.source === 'counter' ? 'Contador automático' : 'Manual'} · {ago(q.updatedAt, now)}
        </p>
      </div>
      {q.source === 'counter' ? (
        <p className="text-[15px] font-semibold">~{q.minutes} min</p>
      ) : q.kind === 'entrance' ? (
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-ink-secondary">
            {q.open ? 'Aberta' : 'Fechada'}
          </span>
          <Toggle
            checked={q.open}
            onChange={(open) => onChange({ open })}
            label={`${q.name} aberta`}
          />
        </div>
      ) : (
        <Stepper
          value={q.minutes}
          onChange={(minutes) => onChange({ minutes })}
          label={`espera em ${q.name}`}
        />
      )}
    </li>
  )
}
