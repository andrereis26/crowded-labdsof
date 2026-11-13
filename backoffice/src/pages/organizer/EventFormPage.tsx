import { Calendar, Clock, MapPin, Plus, X } from 'lucide-react'
import { useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  accessCodeFor,
  codeOwner,
  districts,
  duplicateCodeError,
  emptyEvent,
  eventKinds,
  organization,
  shortDate,
  slug,
  statusTag,
  validateEvent,
  type EventKind,
  type OrgEvent,
} from '../../data/organizer'
import {
  Breadcrumb,
  Button,
  EventThumb,
  PageHeader,
  Panel,
  Tag,
  TextLink,
  TicketCard,
} from '../../ui/components'
import {
  Field,
  RadioDot,
  RadioOption,
  Select,
  TextArea,
  TextInput,
  ToggleRow,
} from '../../ui/forms'
import { QrCode } from '../../ui/qr'
import { downloadQrPng } from '../../ui/qrMatrix'

/** O3 · Criar / editar evento. */
export function EventFormPage({
  events,
  onSave,
}: {
  events: OrgEvent[]
  onSave: (event: OrgEvent) => void
}) {
  const { id } = useParams()
  const existing = events.find((e) => e.id === id)
  const [event, setEvent] = useState<OrgEvent>(() => existing ?? emptyEvent())
  const [errors, setErrors] = useState<ReturnType<typeof validateEvent>>({})
  const navigate = useNavigate()
  const editing = !!existing

  if (id && !existing) {
    return (
      <p className="text-sm text-ink-secondary">
        Não encontrámos este evento.{' '}
        <Link to="/organization/events" className="font-semibold text-primary">
          Voltar aos eventos
        </Link>
      </p>
    )
  }

  const set = <K extends keyof OrgEvent>(key: K, value: OrgEvent[K]) => {
    setEvent((e) => ({ ...e, [key]: value }))
    setErrors((all) => ({ ...all, [key]: undefined }))
  }

  const save = (publish: boolean) => {
    const owner = codeOwner(event.code, event, events)
    const found = publish
      ? validateEvent(event, events)
      : !event.name.trim()
        ? { name: 'Escreve o nome do evento.' }
        : owner
          ? { code: duplicateCodeError(owner) }
          : {}
    setErrors(found)
    if (Object.keys(found).length) return

    const newId = editing ? event.id : uniqueId(slug(event.name), events)
    const status = editing ? event.status : publish ? 'published' : 'draft'
    onSave({
      ...event,
      id: newId,
      name: event.name.trim(),
      code: event.code.trim().toUpperCase(),
      status,
    })
    const message = editing
      ? 'Alterações guardadas.'
      : publish
        ? `${event.name.trim()} publicado.`
        : 'Rascunho guardado.'
    navigate(`/organization/events/${newId}`, { state: { message } })
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    save(true)
  }

  const chooseKind = (kind: EventKind) => {
    setEvent((e) => ({
      ...e,
      kind,
      code: kind === 'public' && !e.code ? accessCodeFor(e.name, e.date) : e.code,
    }))
    setErrors((all) => ({ ...all, code: undefined }))
  }

  // A code in use by another upcoming event is shown while typing (O3).
  const owner = codeOwner(event.code, event, events)
  const codeError = errors.code ?? (owner ? duplicateCodeError(owner) : undefined)
  const isPublic = event.kind === 'public'

  const cancelTo = editing ? `/organization/events/${event.id}` : '/organization/events'
  const tag = statusTag[event.status]
  const title = editing ? 'Editar evento' : 'Criar evento'

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 lg:gap-5">
      <div className="-mx-4 -mt-4 grid grid-cols-[1fr_auto_1fr] items-center border-b border-border bg-surface px-4 py-3 lg:hidden">
        <Link to={cancelTo} className="justify-self-start text-[17px] text-primary">
          Cancelar
        </Link>
        <p className="text-[17px] font-semibold">{editing ? 'Editar evento' : 'Novo evento'}</p>
        <button
          type="button"
          onClick={() => save(editing)}
          className="justify-self-end text-[17px] font-semibold text-primary"
        >
          Guardar
        </button>
      </div>

      <div className="flex flex-col gap-5 max-lg:hidden">
        <Breadcrumb
          items={[
            { label: 'Eventos', to: '/organization/events' },
            { label: editing ? event.name : 'Novo evento' },
          ]}
        />
        <PageHeader
          title={title}
          actions={
            <Tag tone={tag.tone} dot={tag.dot}>
              {tag.label}
            </Tag>
          }
        />
      </div>
      <h2 className="sr-only lg:hidden">{title}</h2>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <Panel title="Informação básica" titleClassName="text-base" className="gap-3.5">
            <Field label="Nome do evento" error={errors.name}>
              {(p) => (
                <TextInput
                  {...p}
                  value={event.name}
                  onChange={(e) => set('name', e.target.value)}
                />
              )}
            </Field>
            <Field label="Descrição">
              {(p) => (
                <TextArea
                  {...p}
                  value={event.description}
                  onChange={(e) => set('description', e.target.value)}
                />
              )}
            </Field>
            <div className="grid grid-cols-2 gap-3.5 sm:grid-cols-4">
              <Field label="Data" error={errors.date} className="col-span-2 sm:col-span-1">
                {(p) => (
                  <TextInput
                    {...p}
                    type="date"
                    icon={Calendar}
                    value={event.date}
                    onChange={(e) => set('date', e.target.value)}
                  />
                )}
              </Field>
              <Field label="Abertura de portas">
                {(p) => (
                  <TextInput
                    {...p}
                    type="time"
                    icon={Clock}
                    value={event.doors}
                    onChange={(e) => set('doors', e.target.value)}
                  />
                )}
              </Field>
              <Field label="Início" error={errors.start}>
                {(p) => (
                  <TextInput
                    {...p}
                    type="time"
                    icon={Clock}
                    value={event.start}
                    onChange={(e) => set('start', e.target.value)}
                  />
                )}
              </Field>
              <Field label="Fim" className="max-sm:hidden">
                {(p) => (
                  <TextInput
                    {...p}
                    type="time"
                    icon={Clock}
                    value={event.end}
                    onChange={(e) => set('end', e.target.value)}
                  />
                )}
              </Field>
            </div>
          </Panel>

          <Panel title="Tipo de evento" titleClassName="text-base" className="gap-3.5">
            <div
              role="radiogroup"
              aria-label="Tipo de evento"
              className="flex flex-col gap-2.5 sm:flex-row"
            >
              {(Object.keys(eventKinds) as EventKind[]).map((k) => (
                <RadioOption
                  key={k}
                  name="event-kind"
                  value={k}
                  checked={event.kind === k}
                  onChange={(v) => chooseKind(v as EventKind)}
                  card
                  className="min-w-0 flex-1 items-start"
                >
                  <RadioDot checked={event.kind === k} />
                  <span className="min-w-0 flex-1">
                    <span className="block text-[15px] font-semibold">{eventKinds[k].label}</span>
                    <span className="block text-[13px] leading-[1.4] text-ink-secondary">
                      {eventKinds[k].hint}
                    </span>
                  </span>
                </RadioOption>
              ))}
            </div>
            <Field
              label={isPublic ? 'Código de acesso' : 'Código do evento impresso nos bilhetes'}
              error={codeError}
              help={
                isPublic
                  ? 'Divulga este código ou o código QR. Quem o inserir na app fica ligado ao evento.'
                  : 'O código que aparece em todos os bilhetes deste evento. É assim que o Crowded associa cada bilhete importado.'
              }
            >
              {(p) => (
                <TextInput
                  {...p}
                  autoCapitalize="characters"
                  value={event.code}
                  onChange={(e) => set('code', e.target.value.toUpperCase().replace(/\s/g, ''))}
                />
              )}
            </Field>
          </Panel>

          <Panel title="Local e entradas" titleClassName="text-base" className="gap-3.5">
            <Field label="Local" error={errors.venue}>
              {(p) => (
                <TextInput
                  {...p}
                  icon={MapPin}
                  value={event.venue}
                  onChange={(e) => set('venue', e.target.value)}
                />
              )}
            </Field>
            <Field
              label="Distrito"
              error={errors.district}
              help="Usado para os avisos e a previsão do IPMA."
            >
              {(p) => (
                <Select
                  {...p}
                  value={event.district}
                  onChange={(e) => set('district', e.target.value)}
                  options={[{ value: '', label: 'Escolher distrito' }, ...districts]}
                />
              )}
            </Field>
            <Entrances
              value={event.entrances}
              onChange={(entrances) => set('entrances', entrances)}
            />
            <Field
              label="Capacidade"
              error={errors.capacity}
              hint="Usada para calcular a lotação a partir do contador de entradas."
            >
              {(p) => (
                <TextInput
                  {...p}
                  inputMode="numeric"
                  value={event.capacity ? String(event.capacity) : ''}
                  onChange={(e) => set('capacity', Number(e.target.value.replace(/\D/g, '')))}
                />
              )}
            </Field>
          </Panel>

          <Panel title="Participação no recinto" titleClassName="text-base" className="gap-3">
            <ToggleRow
              title="Pedir confirmação de reportes"
              hint="Outros participantes confirmam com Sim ou Não."
              checked={event.askConfirmation}
              onChange={(v) => set('askConfirmation', v)}
              action={<TextLink to="/organization/settings">Escolher categorias</TextLink>}
            />
            <ToggleRow
              title="Mostrar estimativas de participantes"
              hint="Só com confiança média ou alta e quando não há dados teus."
              checked={event.showEstimates}
              onChange={(v) => set('showEstimates', v)}
            />
          </Panel>
        </div>

        <div className="flex flex-col gap-4 lg:w-[360px] lg:shrink-0">
          <Panel title="Publicação" titleClassName="text-base" className="gap-3">
            {editing ? (
              <>
                <Button type="submit" size="lg">
                  Guardar alterações
                </Button>
                <Link to={cancelTo} className="text-center text-sm font-semibold text-primary">
                  Cancelar
                </Link>
              </>
            ) : (
              <>
                <Button type="submit" size="lg" disabled={!!owner}>
                  Publicar evento
                </Button>
                <Button variant="secondary" size="lg" onClick={() => save(false)}>
                  Guardar rascunho
                </Button>
              </>
            )}
          </Panel>

          {isPublic && event.code && (
            <Panel title="Código QR do evento" className="gap-3">
              <div className="flex items-center gap-3">
                <QrCode text={event.code} />
                <div className="flex min-w-0 flex-col gap-2">
                  <p className="text-[13px] leading-[1.4] text-ink-secondary">
                    Gerado a partir do código de acesso. Usa-o em cartazes e à entrada.
                  </p>
                  <button
                    type="button"
                    onClick={() =>
                      downloadQrPng(event.code, `codigo-qr-${slug(event.name) || 'evento'}.png`)
                    }
                    className="text-left text-[13px] font-semibold text-primary hover:underline"
                  >
                    Descarregar código QR (PNG)
                  </button>
                </div>
              </div>
            </Panel>
          )}

          <Panel title="Pré-visualização" className="max-lg:hidden">
            <TicketCard
              name={event.name}
              dateLabel={shortDate(event.date)}
              venue={event.venue}
              fields={[
                { label: 'HORA', value: event.start || '—' },
                isPublic
                  ? { label: 'ENTRADA', value: 'Código' }
                  : { label: 'PORTA', value: event.entrances[0]?.replace(/^Porta /, '') ?? '—' },
                { label: 'LUGAR', value: isPublic ? 'Livre' : 'Geral' },
              ]}
              status={event.status === 'draft' ? 'Publicado' : statusTag[event.status].label}
              source={organization.name}
              image={event.image}
              gradient={event.gradient}
            />
          </Panel>

          <ImagePanel
            event={event}
            onChange={(image, imageName) => setEvent((e) => ({ ...e, image, imageName }))}
          />
        </div>
      </div>
    </form>
  )
}

function uniqueId(base: string, events: OrgEvent[]): string {
  let id = base || 'evento'
  for (let n = 2; events.some((e) => e.id === id); n++) id = `${base}-${n}`
  return id
}

/** Entrances and queue points as removable chips. */
function Entrances({ value, onChange }: { value: string[]; onChange: (value: string[]) => void }) {
  const [adding, setAdding] = useState(false)
  const [draft, setDraft] = useState('')

  const add = () => {
    const name = draft.trim()
    if (name && !value.includes(name)) onChange([...value, name])
    setDraft('')
    setAdding(false)
  }

  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className="mb-1.5 text-[13px] font-medium">Entradas e pontos de fila</legend>
      <ul className="flex flex-wrap items-center gap-2">
        {value.map((name) => (
          <li
            key={name}
            className="flex items-center gap-1.5 rounded-full bg-background py-[7px] pr-2.5 pl-3 text-[13px] font-medium"
          >
            {name}
            <button
              type="button"
              aria-label={`Remover ${name}`}
              onClick={() => onChange(value.filter((v) => v !== name))}
              className="rounded-full text-ink-secondary hover:text-ink"
            >
              <X size={14} aria-hidden />
            </button>
          </li>
        ))}
        <li>
          {adding ? (
            <input
              autoFocus
              aria-label="Nome da entrada ou ponto de fila"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onBlur={add}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  add()
                }
                if (e.key === 'Escape') setAdding(false)
              }}
              placeholder="Ex.: Porta C"
              className="w-36 rounded-full border border-primary px-3 py-[6px] text-[13px] outline-none"
            />
          ) : (
            <button
              type="button"
              onClick={() => setAdding(true)}
              className="flex items-center gap-1.5 rounded-full border border-border px-3 py-[7px] text-[13px] font-semibold text-primary hover:bg-primary-soft"
            >
              <Plus size={14} aria-hidden />
              Adicionar
            </button>
          )}
        </li>
      </ul>
    </fieldset>
  )
}

function ImagePanel({
  event,
  onChange,
}: {
  event: OrgEvent
  onChange: (image: string, name: string) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  return (
    <Panel title="Imagem do evento" className="max-lg:hidden">
      <div className="flex items-center gap-3">
        <EventThumb
          image={event.image}
          gradient={event.gradient}
          size={56}
          rounded="rounded-[10px]"
        />
        <div className="min-w-0 flex-1">
          <p className="truncate text-[13px] font-semibold">{event.imageName ?? 'Sem imagem'}</p>
          <p className="text-xs text-ink-secondary">1600 × 900 recomendado</p>
        </div>
        <input
          ref={input}
          type="file"
          accept="image/*"
          className="sr-only"
          aria-label="Escolher imagem do evento"
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) onChange(URL.createObjectURL(file), file.name)
          }}
        />
        <button
          type="button"
          onClick={() => input.current?.click()}
          className="text-[13px] font-semibold text-primary hover:underline"
        >
          {event.imageName ? 'Substituir' : 'Escolher'}
        </button>
      </div>
    </Panel>
  )
}
