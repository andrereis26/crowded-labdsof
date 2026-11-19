import { AlertTriangle, Calendar, Clock, MapPin, Plus, X } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { errorField, eventErrorMessage, EventsApiError } from '../../data/eventsApi'
import { MAP_UNAVAILABLE, type Geocoder } from '../../data/geocoding'
import {
  accessCodeFor,
  codeOwner,
  districts,
  duplicateCodeError,
  emptyEvent,
  eventKinds,
  parseAnswers,
  parseRadius,
  organization,
  shortDate,
  slug,
  statusTag,
  validateEvent,
  type EventKind,
  type OrgEvent,
} from '../../data/organizer'
import { Breadcrumb, Button, Notice, PageHeader, Panel, Tag, TicketCard } from '../../ui/components'
import { Field, RadioDot, RadioOption, Select, TextArea, TextInput } from '../../ui/forms'
import { QrCode } from '../../ui/qr'
import { downloadQrPng } from '../../ui/qrMatrix'
import { AddressField } from './AddressField'
import { ConfirmationCategoriesPanel, ConfirmationRulesPanel } from './ConfirmationPanels'
import { EventImagePanel, type ImageChoice } from './EventImagePanel'

/** O3 · Criar / editar evento. Creating publishes the event in Core. Editing
 * corrects what is not an official change; the date, times and venue change
 * through "Alterar ou cancelar" in O4, which notifies the participants. */
export function EventFormPage({
  events,
  geocode,
  onCreate,
  onCorrect,
  onImage,
}: {
  events: OrgEvent[]
  geocode: Geocoder
  onCreate: (event: OrgEvent) => Promise<OrgEvent>
  onCorrect: (before: OrgEvent, after: OrgEvent) => Promise<OrgEvent>
  /** Uploads (a file) or removes (null) the saved event's image. */
  onImage: (eventId: string, file: File | null) => Promise<void>
}) {
  const { id } = useParams()
  const existing = events.find((e) => e.id === id)
  const [event, setEvent] = useState<OrgEvent>(() => existing ?? emptyEvent())
  const [answers, setAnswers] = useState(() =>
    String((existing ?? event).confirmations.highConfidenceAnswers),
  )
  const [radius, setRadius] = useState(() => String((existing ?? event).confirmations.radiusMeters))
  const [errors, setErrors] = useState<ReturnType<typeof validateEvent>>({})
  const [answersError, setAnswersError] = useState<string>()
  const [radiusError, setRadiusError] = useState<string>()
  const [mapUnavailable, setMapUnavailable] = useState(false)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [imageChoice, setImageChoice] = useState<ImageChoice>({ kind: 'keep' })
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

  const save = async () => {
    if (saving) return
    const found = validateEvent(event, events)
    if (found.address && mapUnavailable && event.address.trim()) found.address = MAP_UNAVAILABLE
    const parsed = parseAnswers(answers)
    const meters = parseRadius(radius)
    setErrors(found)
    setAnswersError(typeof parsed === 'string' ? parsed : undefined)
    setRadiusError(typeof meters === 'string' ? meters : undefined)
    setFormError('')
    if (Object.keys(found).length || typeof parsed === 'string' || typeof meters === 'string') {
      return
    }

    const ready: OrgEvent = {
      ...event,
      confirmations: {
        ...event.confirmations,
        radiusMeters: meters,
        highConfidenceAnswers: parsed,
      },
    }
    setSaving(true)
    try {
      const saved = existing ? await onCorrect(existing, ready) : await onCreate(ready)
      let message = editing ? 'Alterações guardadas.' : `${saved.name} publicado.`
      // The event is saved by now: a refused image is reported, not retried.
      try {
        if (imageChoice.kind === 'new') await onImage(saved.id, imageChoice.file)
        if (imageChoice.kind === 'remove') await onImage(saved.id, null)
      } catch (e) {
        const code = e instanceof EventsApiError ? e.code : 'UNKNOWN'
        message = `${message} A imagem não foi guardada: ${eventErrorMessage(code)}`
      }
      navigate(`/organization/events/${saved.id}`, { state: { message } })
    } catch (e) {
      const code = e instanceof EventsApiError ? e.code : 'UNKNOWN'
      const field = errorField(code)
      if (field) setErrors({ [field]: eventErrorMessage(code) })
      else setFormError(eventErrorMessage(code))
      setSaving(false)
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    void save()
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
          onClick={() => void save()}
          disabled={saving}
          className="justify-self-end text-[17px] font-semibold text-primary disabled:opacity-50"
        >
          {editing ? 'Guardar' : 'Publicar'}
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
      {formError && (
        <div role="alert">
          <Notice tone="warning" icon={AlertTriangle} title="Não foi possível guardar">
            {formError}
          </Notice>
        </div>
      )}

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
            <Field label="Descrição" error={errors.description}>
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
                    disabled={editing}
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
                    disabled={editing}
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
                    disabled={editing}
                    onChange={(e) => set('start', e.target.value)}
                  />
                )}
              </Field>
              <Field label="Fim" error={errors.end}>
                {(p) => (
                  <TextInput
                    {...p}
                    type="time"
                    icon={Clock}
                    value={event.end}
                    disabled={editing}
                    onChange={(e) => set('end', e.target.value)}
                  />
                )}
              </Field>
            </div>
            {editing && <OfficialChangeNote eventId={event.id} />}
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
                  disabled={editing}
                  placeholder="Ex.: Pavilhão Rosa Mota"
                  onChange={(e) => set('venue', e.target.value)}
                />
              )}
            </Field>
            <AddressField
              address={event.address}
              place={
                event.latitude !== undefined && event.longitude !== undefined
                  ? { latitude: event.latitude, longitude: event.longitude }
                  : undefined
              }
              geocode={geocode}
              disabled={editing}
              error={errors.address}
              onChange={(address, place, unavailable) => {
                setMapUnavailable(!!unavailable)
                setEvent((e) => ({
                  ...e,
                  address,
                  latitude: place?.latitude,
                  longitude: place?.longitude,
                }))
                setErrors((all) => ({ ...all, address: undefined }))
              }}
            />
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
              error={errors.entrances}
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

          <ConfirmationCategoriesPanel
            value={event.confirmations}
            onChange={(confirmations) => set('confirmations', confirmations)}
          />
          <ConfirmationRulesPanel
            value={event.confirmations}
            onChange={(confirmations) => set('confirmations', confirmations)}
            radius={radius}
            onRadiusChange={(value) => {
              setRadius(value)
              setRadiusError(undefined)
            }}
            radiusError={radiusError}
            answers={answers}
            onAnswersChange={(value) => {
              setAnswers(value)
              setAnswersError(undefined)
            }}
            answersError={answersError}
          />
        </div>

        <div className="flex flex-col gap-4 lg:w-[360px] lg:shrink-0">
          <Panel title="Publicação" titleClassName="text-base" className="gap-3">
            <Button type="submit" size="lg" disabled={!!owner || saving}>
              {saving ? 'A guardar…' : editing ? 'Guardar alterações' : 'Publicar evento'}
            </Button>
            <Link to={cancelTo} className="text-center text-sm font-semibold text-primary">
              Cancelar
            </Link>
          </Panel>

          <EventImagePanel current={event.image} choice={imageChoice} onChange={setImageChoice} />

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
              image={
                imageChoice.kind === 'new'
                  ? imageChoice.url
                  : imageChoice.kind === 'remove'
                    ? undefined
                    : event.image
              }
              gradient={event.gradient}
            />
          </Panel>
        </div>
      </div>
    </form>
  )
}

/** Where to change what an official change covers, in edit mode. */
function OfficialChangeNote({ eventId }: { eventId: string }) {
  return (
    <p className="text-xs leading-[1.35] text-ink-secondary">
      A data, as horas e o local mudam com uma alteração oficial, que notifica os participantes:{' '}
      <Link to={`/organization/events/${eventId}`} className="font-semibold text-primary">
        Alterar ou cancelar
      </Link>
      .
    </p>
  )
}

/** Entrances and queue points as removable chips. */
function Entrances({
  value,
  error,
  onChange,
}: {
  value: string[]
  error?: string
  onChange: (value: string[]) => void
}) {
  const [adding, setAdding] = useState(false)
  const [draft, setDraft] = useState('')

  const add = () => {
    const name = draft.trim()
    if (name && !value.some((v) => v.toLowerCase() === name.toLowerCase())) {
      onChange([...value, name])
    }
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
              maxLength={60}
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
      {error && <p className="text-xs text-danger">{error}</p>}
    </fieldset>
  )
}
