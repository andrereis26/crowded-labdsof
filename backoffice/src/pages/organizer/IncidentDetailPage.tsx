import {
  Award,
  Check,
  ChevronDown,
  ChevronLeft,
  CircleCheck,
  Megaphone,
  Pencil,
  Sparkles,
} from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  ago,
  formatTime,
  incidentTag,
  organization,
  team,
  type Incident,
} from '../../data/organizer'
import {
  Avatar,
  Breadcrumb,
  Button,
  ButtonLink,
  ConfidenceBadge,
  IconTile,
  Metric,
  Panel,
  ProgressBar,
  SuccessBanner,
  Tag,
} from '../../ui/components'
import { categoryTile, incidentIcon } from '../../ui/categories'

const SHOWN_REPORTS = 4

/** Time of an action, read when the organizer clicks. */
const timestamp = () => new Date()

/** O9 · Detalhe do incidente. */
export function IncidentDetailPage({
  incidents,
  now,
  onChange,
}: {
  incidents: Incident[]
  now: Date
  onChange: (incident: Incident) => void
}) {
  const { id } = useParams()
  const [showAll, setShowAll] = useState(false)
  const [message, setMessage] = useState('')
  const incident = incidents.find((i) => i.id === id)

  if (!incident) {
    return (
      <p className="text-sm text-ink-secondary">
        Não encontrámos este incidente.{' '}
        <Link to="/organization/incidents" className="font-semibold text-primary">
          Voltar aos incidentes
        </Link>
      </p>
    )
  }

  const i = incident
  const tag = incidentTag(i)
  const resolved = i.status === 'resolved'
  const acknowledged = i.status !== 'open'
  const record = (patch: Partial<Incident>, text: string) => {
    onChange({ ...i, ...patch, history: [...i.history, { at: timestamp(), text }] })
    setMessage(text)
  }
  const user = organization.user.name
  const queueName = /Porta \w/.exec(i.title)?.[0]
  const answers = i.confirmations.yes + i.confirmations.no
  const reports = showAll ? i.reports : i.reports.slice(0, SHOWN_REPORTS)
  const assignee = team.find((t) => t.name === i.assignee)

  return (
    <div className="flex flex-col gap-4 lg:gap-[18px]">
      <Link
        to="/organization/incidents"
        className="-ml-1.5 flex items-center text-[17px] text-primary lg:hidden"
      >
        <ChevronLeft size={26} aria-hidden />
        Incidentes
      </Link>
      <div className="max-lg:hidden">
        <Breadcrumb
          items={[{ label: 'Incidentes', to: '/organization/incidents' }, { label: i.title }]}
        />
      </div>

      <div className="flex flex-wrap items-center gap-3.5">
        <span className="max-lg:hidden">
          <IconTile
            icon={incidentIcon(i)}
            className={`${categoryTile[i.category]} rounded-xl !p-2.5`}
            size={24}
          />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-[26px] leading-tight font-bold tracking-[-0.26px]">{i.title}</h2>
          <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
            <Tag tone={tag.tone}>{tag.label}</Tag>
            <ConfidenceBadge level={i.confidence} />
            <span className="text-xs text-ink-secondary">
              {resolved && i.resolvedAt
                ? `Resolvido ${ago(i.resolvedAt, now)}`
                : `Aberto ${ago(i.createdAt, now)}`}
            </span>
          </div>
        </div>
        {!resolved && (
          <div className="flex gap-2.5 max-lg:order-last max-lg:w-full">
            <Button
              variant="secondary"
              icon={Check}
              disabled={acknowledged}
              onClick={() => record({ status: 'in-progress' }, `Reconhecido por ${user}`)}
              className="max-lg:flex-1"
            >
              {acknowledged ? 'Reconhecido' : 'Reconhecer'}
            </Button>
            <Button
              variant="approve"
              icon={CircleCheck}
              onClick={() =>
                record({ status: 'resolved', resolvedAt: timestamp() }, `Resolvido por ${user}`)
              }
              className="max-lg:flex-1"
            >
              Resolver
            </Button>
          </div>
        )}
      </div>

      <SuccessBanner>{message}</SuccessBanner>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <section className="flex flex-col gap-2 rounded-[14px] border border-ai-border bg-ai-soft px-4 py-3.5">
            <p className="flex items-center gap-1.5 text-sm font-semibold text-ai">
              <Sparkles size={16} aria-hidden />
              <span className="flex-1">Resumo gerado por IA</span>
              <span className="text-xs font-normal max-sm:hidden">
                Baseado em {i.reports.length} reportes
              </span>
            </p>
            <p className="text-sm leading-[1.45]">{i.summary}</p>
          </section>

          <div className="flex gap-2.5 lg:hidden">
            <Metric value={i.reports.length} label="reportes" />
            <Metric value={i.confirmations.yes} label="Sim" />
            <Metric value={i.confirmations.no} label="Não" />
          </div>

          <Panel
            title={`Reportes agrupados (${i.reports.length})`}
            titleClassName="text-base"
            className="gap-0.5"
            action={
              i.reports.length > SHOWN_REPORTS && (
                <button
                  type="button"
                  onClick={() => setShowAll((s) => !s)}
                  className="text-[13px] font-semibold text-primary hover:underline"
                >
                  {showAll ? 'Mostrar menos' : 'Mostrar todos'}
                </button>
              )
            }
          >
            <ul id="reports">
              {reports.map((r) => (
                <li key={r.id} className="flex items-center gap-3 border-t border-border py-[11px]">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">“{r.text}”</p>
                    <p className="mt-0.5 text-xs text-ink-secondary">
                      Participante anónimo · {formatTime(r.at)}
                      {r.estimate && ` · ${r.estimate}`}
                    </p>
                  </div>
                  {i.reports.length > 1 && !resolved && (
                    <button
                      type="button"
                      onClick={() =>
                        record(
                          { reports: i.reports.filter((x) => x.id !== r.id) },
                          'Reporte removido do grupo',
                        )
                      }
                      className="text-xs font-semibold whitespace-nowrap text-primary hover:underline"
                    >
                      Remover do grupo
                    </button>
                  )}
                </li>
              ))}
            </ul>
          </Panel>

          <Panel title="Confirmações de outros participantes" className="gap-2 max-lg:hidden">
            <ProgressBar
              value={answers ? i.confirmations.yes / answers : 0}
              className="bg-crowd-low"
              trackClassName="bg-denied-soft"
              height={10}
              label={`${i.confirmations.yes} confirmaram, ${i.confirmations.no} negaram`}
            />
            <p className="text-xs text-ink-secondary">
              {i.confirmations.yes} confirmaram · {i.confirmations.no} negaram · ninguém vê quem
              respondeu
            </p>
          </Panel>
        </div>

        <div className="flex flex-col gap-4 lg:w-[380px] lg:shrink-0">
          <Panel title="Ações" className="gap-2.5">
            <ButtonLink to="/organization/alerts" variant="secondary" icon={Megaphone}>
              Enviar alerta sobre isto
            </ButtonLink>
            {queueName && (
              <ButtonLink
                to="/organization/live/crowd"
                variant="secondary"
                icon={Pencil}
                className="max-lg:hidden"
              >
                Atualizar fila da {queueName}
              </ButtonLink>
            )}
            <Button
              variant="secondary"
              icon={Award}
              className="max-lg:hidden"
              onClick={() => record({}, 'Reporte marcado como útil')}
            >
              Marcar reporte como útil
            </Button>
          </Panel>

          <Panel className="gap-2 max-lg:hidden">
            <label htmlFor="assignee" className="text-[13px] font-medium text-ink-secondary">
              Responsável
            </label>
            <div className="relative flex items-center gap-2.5">
              {assignee ? (
                <Avatar
                  initials={assignee.initials}
                  size={30}
                  className="bg-success-soft text-success"
                />
              ) : (
                <Avatar initials="—" size={30} className="bg-background text-ink-secondary" />
              )}
              <div className="pointer-events-none min-w-0 flex-1">
                <p className="text-sm font-semibold">{assignee?.name ?? 'Sem responsável'}</p>
                <p className="text-xs text-ink-secondary">
                  {assignee?.team ?? 'Escolhe alguém da equipa'}
                </p>
              </div>
              <select
                id="assignee"
                value={i.assignee ?? ''}
                onChange={(e) =>
                  record(
                    { assignee: e.target.value || undefined },
                    e.target.value ? `Atribuído a ${e.target.value}` : 'Responsável removido',
                  )
                }
                className="absolute inset-0 cursor-pointer opacity-0"
              >
                <option value="">Sem responsável</option>
                {team.map((t) => (
                  <option key={t.name} value={t.name}>
                    {t.name} · {t.team}
                  </option>
                ))}
              </select>
              <ChevronDown size={16} aria-hidden />
            </div>
          </Panel>

          <Panel title="Histórico" className="gap-0.5 max-lg:hidden">
            <ol>
              {i.history.map((h, n) => (
                <li key={n} className="flex gap-2.5 py-[7px]">
                  <span className="text-xs font-semibold text-ink-secondary">
                    {formatTime(h.at)}
                  </span>
                  <span className="flex-1 text-[13px]">{h.text}</span>
                </li>
              ))}
            </ol>
          </Panel>
        </div>
      </div>
    </div>
  )
}
