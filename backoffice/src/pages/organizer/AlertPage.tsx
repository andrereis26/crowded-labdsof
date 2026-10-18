import { Megaphone, Send, Sparkles, TriangleAlert, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  audienceSize,
  connected,
  draftAlert,
  formatNumber,
  formatTime,
  type AlertImportance,
  type Audience,
  type OrgEvent,
  type SentAlert,
} from '../../data/organizer'
import { AiTag, Button, PageHeader, Panel, SuccessBanner } from '../../ui/components'
import { Field, RadioDot, RadioOption, SegmentedControl, TextArea, TextInput } from '../../ui/forms'

const importanceOptions = [
  { value: 'info', label: 'Informação' },
  { value: 'important', label: 'Importante' },
  { value: 'urgent', label: 'Urgente' },
] as const

/** O6 · Enviar alerta — manual e rascunho IA. */
export function AlertPage({
  event,
  alerts,
  now,
  onSend,
}: {
  event: OrgEvent
  alerts: SentAlert[]
  now: Date
  onSend: (alert: SentAlert) => void
}) {
  const [description, setDescription] = useState('')
  const [draft, setDraft] = useState<'none' | 'ai' | 'manual'>('none')
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')
  const [importance, setImportance] = useState<AlertImportance>('important')
  const [audience, setAudience] = useState<Audience>('all')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState('')

  const people = connected(event)
  const shortName = event.name.replace(/\s\d{4}$/, '')

  const generate = () => {
    if (!description.trim()) {
      setErrors({ description: 'Descreve o que aconteceu para gerar o rascunho.' })
      return
    }
    const result = draftAlert(description)
    setTitle(result.title)
    setText(result.message)
    setDraft('ai')
    setErrors({})
  }

  const send = (e: FormEvent) => {
    e.preventDefault()
    const found: Record<string, string> = {}
    if (draft === 'none') found.description = 'Gera um rascunho ou escreve o alerta manualmente.'
    else {
      if (!title.trim()) found.title = 'Escreve o título.'
      if (!text.trim()) found.text = 'Escreve a mensagem.'
    }
    setErrors(found)
    if (Object.keys(found).length) return
    const delivered = audienceSize(event, audience)
    onSend({
      id: `alerta-${now.getTime()}-${alerts.length}`,
      eventId: event.id,
      title: title.trim(),
      message: text.trim(),
      importance,
      sentAt: new Date(),
      delivered,
      seconds: 0.8,
    })
    setMessage(`Alerta enviado a ${formatNumber(delivered)} pessoas.`)
    setDescription('')
    setTitle('')
    setText('')
    setDraft('none')
  }

  const recipients: { value: Audience; label: string }[] = [
    { value: 'all', label: `Todos os ligados ao evento · ${formatNumber(people)}` },
    { value: 'tickets', label: `Só quem tem bilhete · ${formatNumber(event.tickets)}` },
    { value: 'followers', label: `Só seguidores · ${formatNumber(event.followers)}` },
  ]

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
                <span className="max-sm:hidden">1. </span>Descreve a situação
              </>
            }
            titleClassName="text-base"
            className="gap-3"
            action={
              <button
                type="button"
                onClick={() => {
                  setDraft('manual')
                  setErrors({})
                }}
                className="text-[13px] font-semibold text-primary hover:underline max-sm:hidden"
              >
                Escrever manualmente
              </button>
            }
          >
            <Field label="O que aconteceu e o que devem fazer?" error={errors.description}>
              {(p) => (
                <TextArea
                  {...p}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Ex.: Porta B fechada por avaria no torniquete. Desviar para a Porta C, 200 m à direita."
                />
              )}
            </Field>
            <div>
              <Button variant="ai" size="lg" icon={Sparkles} onClick={generate} className="max-sm:w-full">
                Gerar rascunho com IA
              </Button>
            </div>
          </Panel>

          {draft !== 'none' && (
            <Panel
              title={
                <>
                  <span className="max-sm:hidden">2. </span>
                  {draft === 'ai' ? 'Revê o rascunho' : 'Escreve o alerta'}
                </>
              }
              titleClassName="text-base"
              className={`gap-3 ${draft === 'ai' ? 'border border-ai-border' : ''}`}
              action={draft === 'ai' && <AiTag />}
            >
              <Field label="Título" error={errors.title}>
                {(p) => <TextInput {...p} value={title} onChange={(e) => setTitle(e.target.value)} />}
              </Field>
              <Field label="Mensagem" error={errors.text}>
                {(p) => <TextArea {...p} value={text} onChange={(e) => setText(e.target.value)} />}
              </Field>
              <div className="flex flex-col gap-1.5">
                <p className="text-[13px] font-medium">Importância</p>
                <SegmentedControl
                  label="Importância"
                  options={importanceOptions}
                  value={importance}
                  onChange={setImportance}
                />
              </div>
            </Panel>
          )}

          <Panel
            title={
              <>
                <span className="max-sm:hidden">3. </span>Destinatários
              </>
            }
            titleClassName="text-base"
          >
            <div role="radiogroup" aria-label="Destinatários" className="flex flex-col gap-2.5">
              {recipients.map((r) => (
                <RadioOption
                  key={r.value}
                  name="audience"
                  value={r.value}
                  checked={audience === r.value}
                  onChange={(v) => setAudience(v as Audience)}
                >
                  <RadioDot checked={audience === r.value} />
                  <span className={`text-sm ${audience === r.value ? 'font-semibold' : 'font-medium'}`}>
                    {r.label}
                  </span>
                </RadioOption>
              ))}
            </div>
          </Panel>

          <div className="flex flex-col gap-3 sm:flex-row">
            <Button type="submit" size="lg" icon={Send}>
              Enviar alerta
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
                  {shortName} · {title || 'Título do alerta'}
                </p>
                <p className="line-clamp-2 text-xs leading-[1.35]">{text || 'A mensagem aparece aqui.'}</p>
              </div>
            </div>
            <p className="flex items-center gap-2.5 rounded-xl bg-warning-soft px-3 py-2.5 text-[13px] font-semibold">
              <TriangleAlert size={18} className="shrink-0 text-warning" aria-hidden />
              {title ? `${title}.` : 'Aviso na página do evento.'}
            </p>
          </Panel>

          <Panel title="Alertas recentes" titleClassName="text-sm" className="gap-1 max-lg:hidden">
            {alerts.length === 0 && <p className="py-2 text-sm text-ink-secondary">Ainda não enviaste alertas.</p>}
            <ul>
              {alerts.slice(0, 4).map((a) => (
                <li key={a.id} className="flex items-center gap-2.5 py-2.5">
                  <Megaphone size={18} className="shrink-0" aria-hidden />
                  <div className="min-w-0">
                    <p className="text-sm font-semibold">{a.title}</p>
                    <p className="text-xs text-ink-secondary">
                      {formatTime(a.sentAt)} · {formatNumber(a.delivered)} entregues em{' '}
                      {String(a.seconds).replace('.', ',')} s
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
