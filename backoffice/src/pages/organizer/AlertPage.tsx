import { Info, Megaphone, Send, TriangleAlert, Users, type LucideIcon } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { eventErrorMessage, EventsApiError } from '../../data/eventsApi'
import {
  alertLabel,
  alertSeverities,
  alertTypes,
  connected,
  formatNumber,
  formatTime,
  type AlertSeverity,
  type AlertType,
  type OrgEvent,
} from '../../data/organizer'
import { Button, PageHeader, Panel, SuccessBanner, Tag, type TagTone } from '../../ui/components'
import { Field, RadioDot, RadioOption, TextArea } from '../../ui/forms'

/** The longest message Core accepts. */
const MAX_LENGTH = 500

const types = Object.keys(alertTypes) as AlertType[]

/** How each severity shows: a word and an icon, never colour alone. */
const severityStyles: Record<AlertSeverity, { tone: TagTone; icon: LucideIcon; box: string }> = {
  critical: { tone: 'danger', icon: TriangleAlert, box: 'bg-danger-soft text-danger' },
  warning: { tone: 'warning', icon: TriangleAlert, box: 'bg-warning-soft text-warning' },
  info: { tone: 'info', icon: Info, box: 'bg-primary-soft text-primary' },
}

/** O6 · Enviar alerta: the member chooses the kind of alert and writes it
 * without any AI help; Core sends it to everyone connected to the event. */
export function AlertPage({
  event,
  onSend,
}: {
  event: OrgEvent
  /** Sends the alert; resolves with how many people it was sent to. */
  onSend: (type: AlertType, message: string) => Promise<number>
}) {
  const [type, setType] = useState<AlertType>()
  const [text, setText] = useState('')
  const [errors, setErrors] = useState<{ type?: string; text?: string }>({})
  const [message, setMessage] = useState('')
  const [failure, setFailure] = useState('')
  const [sending, setSending] = useState(false)
  const typeErrorId = useId()

  const people = connected(event)
  const shortName = event.name.replace(/\s\d{4}$/, '')
  const severity = type ? alertTypes[type].severity : undefined

  const send = async (e: FormEvent) => {
    e.preventDefault()
    const found = {
      type: type ? undefined : 'Escolhe o tipo de alerta.',
      text: text.trim() ? undefined : 'Escreve a mensagem.',
    }
    setErrors(found)
    setMessage('')
    setFailure('')
    if (!type || found.text) return
    setSending(true)
    try {
      const sent = await onSend(type, text.trim())
      setMessage(`Alerta enviado a ${formatNumber(sent)} ${sent === 1 ? 'pessoa' : 'pessoas'}.`)
      setType(undefined)
      setText('')
    } catch (error) {
      setFailure(eventErrorMessage(error instanceof EventsApiError ? error.code : 'UNKNOWN'))
    } finally {
      setSending(false)
    }
  }

  return (
    <form onSubmit={send} noValidate className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title="Novo alerta"
        description={
          <span className="max-sm:hidden">
            Para as {formatNumber(people)} pessoas ligadas ao {shortName}.
          </span>
        }
      />
      <SuccessBanner>{message}</SuccessBanner>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <Panel
            title={
              <>
                <span className="max-sm:hidden">1. </span>Tipo de alerta
              </>
            }
            titleClassName="text-base"
            className="gap-3"
          >
            <div
              role="radiogroup"
              aria-label="Tipo de alerta"
              aria-invalid={errors.type ? true : undefined}
              aria-describedby={errors.type ? typeErrorId : undefined}
              className="grid gap-2.5 sm:grid-cols-2"
            >
              {types.map((t) => {
                const level = alertTypes[t].severity
                return (
                  <RadioOption
                    key={t}
                    name="alert-type"
                    value={t}
                    checked={type === t}
                    onChange={(v) => {
                      setType(v as AlertType)
                      setErrors((found) => ({ ...found, type: undefined }))
                    }}
                    card
                    className="justify-between"
                  >
                    <span className="text-sm font-semibold">{alertTypes[t].label}</span>
                    <Tag tone={severityStyles[level].tone}>{alertSeverities[level]}</Tag>
                  </RadioOption>
                )
              })}
            </div>
            {errors.type && (
              <p id={typeErrorId} className="text-xs text-danger">
                {errors.type}
              </p>
            )}
          </Panel>

          <Panel
            title={
              <>
                <span className="max-sm:hidden">2. </span>Escreve o alerta
              </>
            }
            titleClassName="text-base"
            className="gap-3"
          >
            <Field
              label="Mensagem"
              help={`O que aconteceu e o que devem fazer. Até ${MAX_LENGTH} caracteres.`}
              error={errors.text}
            >
              {(p) => (
                <TextArea
                  {...p}
                  rows={3}
                  maxLength={MAX_LENGTH}
                  value={text}
                  onChange={(e) => {
                    setText(e.target.value)
                    setErrors((found) => ({ ...found, text: undefined }))
                  }}
                  placeholder="Ex.: A Porta B está fechada. Usem a Porta C, 200 m à direita."
                />
              )}
            </Field>
          </Panel>

          <Panel
            title={
              <>
                <span className="max-sm:hidden">3. </span>Destinatários
              </>
            }
            titleClassName="text-base"
          >
            <p className="flex items-center gap-2.5 text-sm font-semibold">
              <RadioDot checked />
              Todos os ligados ao evento (bilhete ou código de acesso) · {formatNumber(people)}
            </p>
          </Panel>

          {failure && (
            <p role="alert" className="text-sm text-danger">
              {failure}
            </p>
          )}
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button type="submit" size="lg" icon={Send} disabled={sending}>
              {sending ? 'A enviar…' : 'Enviar alerta'}
            </Button>
          </div>
        </div>

        <div className="flex flex-col gap-4 lg:w-[360px] lg:shrink-0">
          <Panel title="Pré-visualização" titleClassName="text-sm" className="gap-3 max-lg:hidden">
            <div className="flex gap-2.5 rounded-2xl bg-background p-3">
              <span className="inline-flex h-fit rounded-lg bg-ink p-1.5 text-white">
                <Users size={16} aria-hidden />
              </span>
              <div className="min-w-0 flex-1">
                <p className="flex text-[11px] text-ink-secondary">
                  <span className="flex-1 font-semibold tracking-[0.44px]">CROWDED</span>
                  agora
                </p>
                <p className="text-[13px] font-semibold">
                  {shortName} · {type ? alertTypes[type].label : 'Tipo de alerta'}
                </p>
                <p className="line-clamp-2 text-xs leading-[1.35]">
                  {text.trim() || 'A mensagem aparece aqui.'}
                </p>
              </div>
            </div>
            {severity ? (
              <p
                className={`flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-[13px] font-semibold ${severityStyles[severity].box}`}
              >
                <TriangleAlert size={18} className="shrink-0" aria-hidden />
                {alertSeverities[severity]} na página do evento.
              </p>
            ) : (
              <p className="rounded-xl bg-background px-3 py-2.5 text-[13px] text-ink-secondary">
                Aviso na página do evento.
              </p>
            )}
          </Panel>

          <Panel title="Alertas ativos" titleClassName="text-sm" className="gap-1">
            {(event.alerts ?? []).length === 0 && (
              <p className="py-2 text-sm text-ink-secondary">Ainda não enviaste alertas.</p>
            )}
            <ul>
              {(event.alerts ?? []).slice(0, 4).map((a) => (
                <li key={a.id} className="flex items-start gap-2.5 py-2.5">
                  <Megaphone size={18} className="mt-0.5 shrink-0" aria-hidden />
                  <div className="min-w-0">
                    <p className="text-sm font-semibold">{alertLabel(a)}</p>
                    <p className="line-clamp-2 text-xs">{a.message}</p>
                    <p className="text-xs text-ink-secondary">
                      {formatTime(a.sentAt)} · {alertSeverities[a.severity]}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </Panel>
        </div>
      </div>
    </form>
  )
}
