import { CircleCheck, Clock, Lightbulb, Send, TriangleAlert } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import {
  requestTypes,
  responseOptions,
  type OrgEvent,
  type RequestType,
} from '../../data/organizer'
import { Button, PageHeader, Panel, SuccessBanner, Tag, type Icon } from '../../ui/components'
import {
  Field,
  RadioDot,
  RadioOption,
  Select,
  TextArea,
  TextInput,
  ToggleRow,
} from '../../ui/forms'
import { PollIcon } from '../../ui/icons'

const typeIcon: Record<RequestType, Icon> = {
  confirmation: CircleCheck,
  poll: PollIcon,
  idea: Lightbulb,
}

/** O12 · Publicar pedido. */
export function RequestPage({ event }: { event: OrgEvent }) {
  const [type, setType] = useState<RequestType>('confirmation')
  const [question, setQuestion] = useState('')
  const [details, setDetails] = useState('')
  const [deadline, setDeadline] = useState('')
  const [responses, setResponses] = useState(responseOptions.confirmation[0])
  const [urgent, setUrgent] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [active, setActive] = useState(2)
  const shortName = event.name.replace(/\s\d{4}$/, '')

  const chooseType = (t: RequestType) => {
    setType(t)
    setResponses(responseOptions[t][0] ?? '')
  }

  const publish = (e: FormEvent) => {
    e.preventDefault()
    if (!question.trim()) {
      setError('Escreve a pergunta.')
      return
    }
    setError('')
    setActive((n) => n + 1)
    setMessage(
      urgent ? 'Pedido publicado e notificação enviada.' : 'Pedido publicado na página do evento.',
    )
    setQuestion('')
    setDetails('')
    setUrgent(false)
  }

  const answerButtons =
    type === 'confirmation'
      ? responses.split(' / ').reverse()
      : type === 'poll'
        ? ['Responder']
        : []

  return (
    <form onSubmit={publish} noValidate className="flex flex-col gap-4 lg:gap-5">
      <div className="-mx-4 -mt-4 grid grid-cols-[1fr_auto_1fr] items-center border-b border-border bg-surface px-4 py-3 lg:hidden">
        <Link to="/organizacao/mais" className="justify-self-start text-[17px] text-primary">
          Cancelar
        </Link>
        <p className="text-[17px] font-semibold">Novo pedido</p>
      </div>
      <PageHeader
        title="Novo pedido"
        description={`Aparece na página do ${shortName}. Os pedidos urgentes também enviam notificação.`}
        className="max-lg:hidden"
      />
      <SuccessBanner>{message}</SuccessBanner>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <Panel title="Tipo de pedido" titleClassName="text-base" className="min-w-0 flex-1 gap-4">
          <div
            role="radiogroup"
            aria-label="Tipo de pedido"
            className="flex flex-col gap-2.5 sm:flex-row"
          >
            {(Object.keys(requestTypes) as RequestType[]).map((t) => {
              const TypeIcon = typeIcon[t]
              return (
                <RadioOption
                  key={t}
                  name="request-type"
                  value={t}
                  checked={type === t}
                  onChange={(v) => chooseType(v as RequestType)}
                  card
                  className="min-w-0 flex-1"
                >
                  <TypeIcon
                    size={20}
                    className={`shrink-0 ${type === t ? 'text-primary' : ''}`}
                    aria-hidden
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold">{requestTypes[t].label}</span>
                    <span className="block truncate text-xs text-ink-secondary">
                      {requestTypes[t].hint}
                    </span>
                  </span>
                  <RadioDot checked={type === t} />
                </RadioOption>
              )
            })}
          </div>
          <Field label="Pergunta" error={error}>
            {(p) => (
              <TextInput
                {...p}
                value={question}
                onChange={(e) => {
                  setQuestion(e.target.value)
                  setError('')
                }}
                placeholder={
                  type === 'idea'
                    ? 'Ex.: Que artista gostavas de ver no próximo ano?'
                    : 'Ex.: Vais usar o autocarro especial das 23:30?'
                }
              />
            )}
          </Field>
          <Field label="Detalhes" optional className="max-sm:hidden">
            {(p) => (
              <TextArea {...p} value={details} onChange={(e) => setDetails(e.target.value)} />
            )}
          </Field>
          <div className="grid gap-3.5 sm:grid-cols-2">
            <Field label="Prazo">
              {(p) => (
                <TextInput
                  {...p}
                  type="time"
                  icon={Clock}
                  value={deadline}
                  onChange={(e) => setDeadline(e.target.value)}
                />
              )}
            </Field>
            {type !== 'idea' && (
              <Field label="Respostas" className="max-sm:hidden">
                {(p) => (
                  <Select
                    {...p}
                    value={responses}
                    onChange={(e) => setResponses(e.target.value)}
                    options={responseOptions[type]}
                  />
                )}
              </Field>
            )}
          </div>
          <ToggleRow
            title="Marcar como urgente"
            description="Envia uma notificação a quem está ligado ao evento."
            checked={urgent}
            onChange={setUrgent}
          />
          <p className="flex items-center gap-2 rounded-[10px] bg-warning-soft px-3 py-2.5 text-xs font-medium text-warning">
            <TriangleAlert size={16} className="shrink-0" aria-hidden />
            Usa com moderação: os participantes podem desativar os avisos de pedidos.
          </p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button type="submit" icon={Send}>
              Publicar pedido
            </Button>
          </div>
        </Panel>

        <aside
          className="flex flex-col gap-3 max-lg:hidden lg:w-[380px] lg:shrink-0"
          aria-label="Pré-visualização"
        >
          <p className="text-[13px] font-semibold text-ink-secondary">Como os participantes veem</p>
          <div
            className={`flex flex-col gap-2.5 rounded-[14px] border bg-surface p-4 ${urgent ? 'border-danger-border' : 'border-border'}`}
          >
            <div className="flex items-center gap-1.5">
              {urgent && (
                <Tag tone="danger" icon={TriangleAlert}>
                  URGENTE
                </Tag>
              )}
              <Tag tone="info">{requestTypes[type].label}</Tag>
              <span className="flex-1" />
              <span className="text-xs text-ink-secondary">agora</span>
            </div>
            <p className="text-base font-semibold">{question || 'A tua pergunta aparece aqui.'}</p>
            {details && <p className="text-[13px] text-ink/85">{details}</p>}
            {answerButtons.length > 0 ? (
              <div className="flex gap-2.5">
                {answerButtons.map((label, n) => (
                  <span
                    key={label}
                    className={`flex flex-1 justify-center rounded-[10px] px-3.5 py-2 text-[13px] font-semibold ${
                      n === answerButtons.length - 1 ? 'bg-ink text-white' : 'border border-border'
                    }`}
                  >
                    {label}
                  </span>
                ))}
              </div>
            ) : (
              <span className="rounded-[10px] border border-border px-3.5 py-2 text-[13px] text-ink-secondary">
                Escreve a tua ideia…
              </span>
            )}
          </div>
          <p className="text-xs text-ink-secondary">
            Pedidos ativos: {active} · Ideias recebidas: 23
          </p>
        </aside>
      </div>
    </form>
  )
}
