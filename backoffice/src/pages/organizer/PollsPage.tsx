import { Calendar, GripVertical, Lightbulb, Plus, Send, X } from 'lucide-react'
import { useRef, useState, type FormEvent, type RefObject } from 'react'
import {
  connected,
  formatNumber,
  pollResponses,
  shortDate,
  type OrgEvent,
  type Poll,
} from '../../data/organizer'
import {
  Button,
  Chip,
  Metric,
  PageHeader,
  Panel,
  ProgressBar,
  SuccessBanner,
  Tag,
} from '../../ui/components'
import { Field, TextInput, ToggleRow } from '../../ui/forms'

const DAY = 24 * 3600000

/** O11 · Criar sondagem e ver resultados. */
export function PollsPage({
  event,
  polls,
  now,
  onPublish,
}: {
  event: OrgEvent
  polls: Poll[]
  now: Date
  onPublish: (poll: Poll) => void
}) {
  const [selectedId, setSelectedId] = useState<string>()
  const [message, setMessage] = useState('')
  const question = useRef<HTMLInputElement>(null)
  const selected = polls.find((p) => p.id === selectedId) ?? polls[0]
  const shortName = event.name.replace(/\s\d{4}$/, '')

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title="Sondagens"
        description={<span className="max-sm:hidden">Intenção de presença no {shortName}.</span>}
        actions={
          <Button icon={Plus} onClick={() => question.current?.focus()} className="max-sm:!rounded-full max-sm:!p-2">
            <span className="max-sm:sr-only">Nova sondagem</span>
          </Button>
        }
      />
      <SuccessBanner>{message}</SuccessBanner>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          {polls.length > 1 && (
            <div className="flex gap-2 overflow-x-auto" role="group" aria-label="Escolher sondagem">
              {polls.map((p) => (
                <Chip key={p.id} selected={p.id === selected?.id} onClick={() => setSelectedId(p.id)}>
                  {p.questions[0].text}
                </Chip>
              ))}
            </div>
          )}
          {selected ? (
            <Results poll={selected} now={now} />
          ) : (
            <Panel title="Resultados" titleClassName="text-lg">
              <p className="text-sm text-ink-secondary">
                Ainda não há sondagens para {event.name}. Cria a primeira para saber quantas pessoas
                pensam ir e a que horas chegam.
              </p>
            </Panel>
          )}
        </div>

        <NewPollForm
          event={event}
          inputRef={question}
          now={now}
          onPublish={(poll) => {
            onPublish(poll)
            setSelectedId(poll.id)
            setMessage('Sondagem publicada.')
          }}
        />
      </div>
    </div>
  )
}

function Results({ poll, now }: { poll: Poll; now: Date }) {
  const responses = pollResponses(poll)
  const daysLeft = Math.max(0, Math.ceil((poll.closesAt.getTime() - now.getTime()) / DAY))
  const closed = poll.closesAt < now
  const closesLabel = shortDate(
    `${poll.closesAt.getFullYear()}-${poll.closesAt.getMonth() + 1}-${poll.closesAt.getDate()}`,
  )

  return (
    <Panel
      title="Resultados"
      titleClassName="text-lg"
      className="gap-4 !px-[22px] !py-[18px]"
      action={
        <Tag tone={closed ? 'neutral' : 'success'} dot>
          {closed ? 'Fechada' : `Ativa · fecha a ${closesLabel}`}
        </Tag>
      }
    >
      <div className="flex gap-3 max-sm:hidden">
        <Metric value={formatNumber(responses)} label="respostas" />
        <Metric value={`${Math.round((responses / poll.audience) * 100)}%`} label="das pessoas ligadas" />
        <Metric value={closed ? '—' : `${daysLeft} ${daysLeft === 1 ? 'dia' : 'dias'}`} label="até fechar" />
      </div>
      <p className="text-xs text-ink-secondary sm:hidden">
        {formatNumber(responses)} respostas · {Math.round((responses / poll.audience) * 100)}% das pessoas ligadas
      </p>

      {poll.questions.map((q) => {
        const total = q.options.reduce((sum, o) => sum + o.votes, 0)
        const max = Math.max(...q.options.map((o) => o.votes))
        return (
          <section key={q.text} className="flex flex-col gap-4">
            <h4 className="text-[15px] font-semibold">{q.text}</h4>
            {q.options.map((o) => {
              const pct = total ? Math.round((o.votes / total) * 100) : 0
              const color = o.negative
                ? 'bg-muted-bar'
                : o.votes === max && total
                  ? 'bg-primary'
                  : 'bg-chart-mid'
              return (
                <div key={o.label} className="flex flex-col gap-1.5">
                  <p className="flex justify-between gap-2 text-sm">
                    <span className="font-medium">{o.label}</span>
                    <span className="font-semibold">
                      {pct}%<span className="max-sm:hidden"> · {formatNumber(o.votes)}</span>
                    </span>
                  </p>
                  <ProgressBar value={total ? o.votes / total : 0} className={color} height={10} />
                </div>
              )
            })}
          </section>
        )
      })}

      {poll.insight && (
        <div className="flex gap-2.5 rounded-xl bg-primary-soft px-3.5 py-3">
          <Lightbulb size={20} className="shrink-0 text-primary" aria-hidden />
          <div>
            <p className="text-sm font-semibold">{poll.insight.title}</p>
            <p className="mt-0.5 text-xs text-ink/80">{poll.insight.detail}</p>
          </div>
        </div>
      )}
    </Panel>
  )
}

function NewPollForm({
  event,
  inputRef,
  now,
  onPublish,
}: {
  event: OrgEvent
  inputRef: RefObject<HTMLInputElement | null>
  now: Date
  onPublish: (poll: Poll) => void
}) {
  const [question, setQuestion] = useState('')
  const [options, setOptions] = useState(['Sim', 'Não', 'Ainda não sei'])
  const [closesAt, setClosesAt] = useState('')
  const [notify, setNotify] = useState(true)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const filled = options.map((o) => o.trim()).filter(Boolean)
    const found: Record<string, string> = {}
    if (!question.trim()) found.question = 'Escreve a pergunta.'
    if (filled.length < 2) found.options = 'Indica pelo menos duas opções.'
    if (!closesAt) found.closesAt = 'Escolhe quando a sondagem fecha.'
    else if (new Date(closesAt) <= now) found.closesAt = 'Escolhe uma data no futuro.'
    setErrors(found)
    if (Object.keys(found).length) return
    onPublish({
      id: `sondagem-${Date.now()}`,
      eventId: event.id,
      closesAt: new Date(closesAt),
      audience: connected(event),
      questions: [{ text: question.trim(), options: filled.map((label) => ({ label, votes: 0 })) }],
    })
    setQuestion('')
    setOptions(['Sim', 'Não', 'Ainda não sei'])
    setClosesAt('')
  }

  return (
    <form onSubmit={submit} noValidate className="lg:w-[400px] lg:shrink-0">
      <Panel title="Nova sondagem" titleClassName="text-base" className="gap-3.5">
        <Field label="Pergunta" error={errors.question}>
          {(p) => (
            <TextInput
              {...p}
              ref={inputRef}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="Ex.: Vais usar o estacionamento?"
            />
          )}
        </Field>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-[13px] font-medium">Opções</legend>
          {options.map((o, n) => (
            <div key={n} className="flex items-center gap-2 rounded-[10px] border border-border px-3 py-1 focus-within:outline-2 focus-within:outline-primary">
              <GripVertical size={16} className="shrink-0 text-ink-secondary" aria-hidden />
              <input
                aria-label={`Opção ${n + 1}`}
                value={o}
                onChange={(e) => setOptions(options.map((x, i) => (i === n ? e.target.value : x)))}
                className="min-w-0 flex-1 bg-transparent py-1.5 text-sm outline-none"
              />
              {options.length > 2 && (
                <button
                  type="button"
                  aria-label={`Remover opção ${n + 1}`}
                  onClick={() => setOptions(options.filter((_, i) => i !== n))}
                  className="rounded p-1 text-ink-secondary hover:text-ink"
                >
                  <X size={14} aria-hidden />
                </button>
              )}
            </div>
          ))}
          {errors.options && <p className="text-xs text-danger">{errors.options}</p>}
          <button
            type="button"
            onClick={() => setOptions([...options, ''])}
            className="flex items-center gap-1.5 self-start text-[13px] font-semibold text-primary hover:underline"
          >
            <Plus size={16} aria-hidden />
            Adicionar opção
          </button>
        </fieldset>
        <Field label="Fecha a" error={errors.closesAt}>
          {(p) => (
            <TextInput
              {...p}
              type="datetime-local"
              icon={Calendar}
              value={closesAt}
              onChange={(e) => setClosesAt(e.target.value)}
            />
          )}
        </Field>
        <ToggleRow
          title="Notificar participantes ligados"
          description={`${formatNumber(connected(event))} pessoas · respostas anónimas`}
          checked={notify}
          onChange={setNotify}
        />
        <Button type="submit" icon={Send}>
          Publicar sondagem
        </Button>
      </Panel>
    </form>
  )
}
