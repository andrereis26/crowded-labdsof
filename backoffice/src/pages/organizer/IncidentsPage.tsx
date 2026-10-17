import { ChevronRight, CornerDownRight, ListFilter, Lock, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  ago,
  categories,
  incidentTag,
  reportsLabel,
  type Incident,
  type IncidentCategory,
} from '../../data/organizer'
import {
  Button,
  Chip,
  ConfidenceBadge,
  IconTile,
  Metric,
  PageHeader,
  Panel,
  Tag,
} from '../../ui/components'
import { categoryTile, incidentIcon } from '../../ui/categories'

type Filter = 'open' | 'in-progress' | 'resolved'

const matches = (i: Incident, f: Filter) =>
  f === 'open' ? i.status !== 'resolved' : i.status === f

/** O8 · Incidentes agrupados por IA. */
export function IncidentsPage({ incidents, now }: { incidents: Incident[]; now: Date }) {
  const [filter, setFilter] = useState<Filter>('open')
  const [category, setCategory] = useState<IncidentCategory | 'all'>('all')
  const [selectedId, setSelectedId] = useState<string>()
  const navigate = useNavigate()

  const visible = incidents.filter(
    (i) => matches(i, filter) && (category === 'all' || i.category === category),
  )
  const selected = visible.find((i) => i.id === selectedId) ?? visible[0]
  const count = (f: Filter) => incidents.filter((i) => matches(i, f)).length

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title="Incidentes"
        description={<span className="max-sm:hidden">Reportes de participantes, agrupados por assunto.</span>}
      />

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex gap-2 overflow-x-auto" role="group" aria-label="Filtrar incidentes">
          <Chip selected={filter === 'open'} onClick={() => setFilter('open')}>
            Abertos · {count('open')}
          </Chip>
          <Chip selected={filter === 'in-progress'} onClick={() => setFilter('in-progress')}>
            Em curso · {count('in-progress')}
          </Chip>
          <Chip selected={filter === 'resolved'} onClick={() => setFilter('resolved')}>
            Resolvidos · {count('resolved')}
          </Chip>
        </div>
        <span className="flex-1" />
        <label className="relative flex items-center gap-1.5 rounded-[10px] border border-border bg-surface py-2 pr-3 pl-3 text-[13px] font-medium max-sm:hidden">
          <ListFilter size={16} aria-hidden />
          <span className="sr-only">Categoria</span>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as IncidentCategory | 'all')}
            className="appearance-none bg-transparent pr-1 outline-none"
          >
            <option value="all">Todas as categorias</option>
            {Object.entries(categories).map(([value, c]) => (
              <option key={value} value={value}>
                {c.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <ul aria-label="Grupos de reportes" className="flex min-w-0 flex-1 flex-col gap-1.5 rounded-[14px] bg-surface p-2.5">
          {visible.length === 0 && <li className="p-3 text-sm text-ink-secondary">Sem incidentes nesta lista.</li>}
          {visible.map((i) => {
            const isSelected = selected?.id === i.id
            const row = 'flex w-full items-center gap-3 rounded-xl border px-4 py-3.5 text-left'
            return (
              <li key={i.id}>
                {/* Desktop: select to preview on the right. Phones: open it. */}
                <button
                  type="button"
                  aria-pressed={isSelected}
                  onClick={() => setSelectedId(i.id)}
                  className={`${row} max-lg:hidden ${
                    isSelected ? 'border-primary-border bg-primary-soft' : 'border-transparent hover:bg-background'
                  }`}
                >
                  <IncidentSummary incident={i} now={now} />
                </button>
                <Link to={`/organizacao/incidentes/${i.id}`} className={`${row} border-transparent lg:hidden`}>
                  <IncidentSummary incident={i} now={now} />
                </Link>
              </li>
            )
          })}
        </ul>

        {selected && (
          <Panel
            title={selected.title}
            titleClassName="text-[17px]"
            className="gap-3.5 max-lg:hidden lg:w-[420px] lg:shrink-0"
            action={<Tag tone={incidentTag(selected).tone}>{incidentTag(selected).label}</Tag>}
          >
            <div className="flex flex-col gap-1.5 rounded-xl bg-ai-soft px-3.5 py-3">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.44px] text-ai">
                <Sparkles size={14} aria-hidden />
                RESUMO GERADO POR IA
              </p>
              <p className="text-[13px] leading-[1.45]">{selected.summary}</p>
            </div>
            <div className="flex gap-2.5">
              <Metric value={selected.reports.length} label="reportes" />
              <Metric value={selected.confirmations.yes} label="confirmaram" />
              <Metric value={selected.confirmations.no} label="negaram" />
            </div>
            <div className="flex gap-2.5">
              <Button
                className="flex-1"
                onClick={() => navigate(`/organizacao/incidentes/${selected.id}`)}
              >
                Abrir incidente
              </Button>
              <Button
                variant="secondary"
                icon={CornerDownRight}
                className="flex-1"
                onClick={() => navigate(`/organizacao/incidentes/${selected.id}#reportes`)}
              >
                Separar grupo
              </Button>
            </div>
          </Panel>
        )}
      </div>
    </div>
  )
}

function IncidentSummary({ incident: i, now }: { incident: Incident; now: Date }) {
  const tag = incidentTag(i)
  const category = categories[i.category]
  return (
    <>
      <IconTile icon={incidentIcon(i)} className={categoryTile[i.category]} />
      <span className="min-w-0 flex-1">
        <span className="block text-[15px] font-semibold">{i.title}</span>
        <span className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-ink-secondary">
          {category.label} · {reportsLabel(i.reports.length)}
          {category.personal ? ' · privado' : ''} · {ago(i.createdAt, now)}
          {category.personal ? (
            <Tag icon={Lock}>Pessoal · não público</Tag>
          ) : (
            <ConfidenceBadge level={i.confidence} />
          )}
        </span>
      </span>
      <Tag tone={tag.tone}>{tag.label}</Tag>
      <ChevronRight size={16} className="shrink-0 text-ink-secondary max-sm:hidden" aria-hidden />
    </>
  )
}
