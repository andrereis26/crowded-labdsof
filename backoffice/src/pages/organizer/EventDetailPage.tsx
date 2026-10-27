import {
  Calendar,
  ChartNoAxesColumn,
  Clock,
  MapPin,
  Pencil,
  RefreshCw,
  Send,
  Ticket,
  Users,
} from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import {
  applyChange,
  connected,
  eventDateLabel,
  formatNumber,
  type ChangeKind,
  type EventChange,
  type OrgEvent,
} from '../../data/organizer'
import {
  Button,
  ButtonLink,
  Dialog,
  Notice,
  PageHeader,
  Panel,
  StatCard,
  SuccessBanner,
} from '../../ui/components'
import { Checkbox, Field, SegmentedControl, TextArea, TextInput } from '../../ui/forms'

/** O4 · Página do evento, com o diálogo para adiar, mudar o local ou
 * cancelar. */
export function EventDetailPage({
  events,
  now,
  onSave,
}: {
  events: OrgEvent[]
  now: Date
  onSave: (event: OrgEvent) => void
}) {
  const { id } = useParams()
  const location = useLocation()
  const [changing, setChanging] = useState(false)
  const [message, setMessage] = useState<string>(
    (location.state as { message?: string } | null)?.message ?? '',
  )
  const event = events.find((e) => e.id === id)

  if (!event) {
    return (
      <p className="text-sm text-ink-secondary">
        Não encontrámos este evento.{' '}
        <Link to="/organizacao/eventos" className="font-semibold text-primary">
          Voltar aos eventos
        </Link>
      </p>
    )
  }

  const synced = event.source === 'ticketing'
  const closed = event.status === 'ended' || event.status === 'cancelled'

  const applied = (change: EventChange) => {
    onSave(applyChange(event, change))
    setChanging(false)
    const people = `${formatNumber(connected(event))} pessoas notificadas.`
    setMessage(
      {
        postpone: `Evento adiado. ${people}`,
        venue: `Local alterado. ${people}`,
        cancel: `Evento cancelado. ${people}`,
      }[change.kind],
    )
  }

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title={event.name}
        description={`${eventDateLabel(event, now)} · ${event.venue}`}
        actions={
          !closed && (
            <>
              <Button
                variant="secondary"
                size="lg"
                icon={Calendar}
                disabled={synced}
                onClick={() => setChanging(true)}
              >
                Alterar ou cancelar
              </Button>
              <ButtonLink to={`/organizacao/eventos/${event.id}/editar`} size="lg" icon={Pencil}>
                Editar
              </ButtonLink>
            </>
          )
        }
      />

      <SuccessBanner>{message}</SuccessBanner>

      {synced && (
        <Notice icon={RefreshCw} title="Evento sincronizado da bilheteira">
          A data, o local e os cancelamentos vêm sempre da bilheteira oficial.
        </Notice>
      )}

      <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3 lg:gap-4">
        <StatCard
          icon={Users}
          label="Pessoas ligadas"
          value={formatNumber(connected(event))}
          note="Bilhetes e seguidores"
        />
        <StatCard icon={Ticket} label="Com bilhete" value={formatNumber(event.tickets)} />
        <StatCard
          icon={ChartNoAxesColumn}
          label="Capacidade"
          value={event.capacity ? formatNumber(event.capacity) : '—'}
          note={event.entrances.join(' · ')}
        />
      </div>

      <Panel title="Detalhes" titleClassName="text-base">
        {event.description && <p className="text-sm leading-[1.4]">{event.description}</p>}
        <dl className="grid gap-3 sm:grid-cols-3">
          <Detail icon={Calendar} label="Data" value={eventDateLabel(event, now)} />
          <Detail
            icon={Clock}
            label="Horário"
            value={[
              event.doors && `Portas ${event.doors}`,
              event.start && `Início ${event.start}`,
              event.end && `Fim ${event.end}`,
            ]
              .filter(Boolean)
              .join(' · ')}
          />
          <Detail icon={MapPin} label="Local" value={event.venue || '—'} />
        </dl>
        <div className="flex flex-wrap gap-2.5 pt-1">
          {event.status === 'live' && (
            <ButtonLink
              to="/organizacao/ao-vivo"
              variant="secondary"
              size="sm"
              icon={ChartNoAxesColumn}
            >
              Ver ao vivo
            </ButtonLink>
          )}
          {event.status === 'ended' && (
            <ButtonLink
              to={`/organizacao/eventos/${event.id}/resumo`}
              variant="secondary"
              size="sm"
            >
              Resumo pós-evento
            </ButtonLink>
          )}
        </div>
      </Panel>

      {changing && (
        <ChangeEventDialog
          event={event}
          now={now}
          onClose={() => setChanging(false)}
          onApply={applied}
        />
      )}
    </div>
  )
}

function Detail({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Calendar
  label: string
  value: string
}) {
  return (
    <div className="flex flex-col gap-1 rounded-[10px] bg-background px-3 py-2.5">
      <dt className="flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.44px] text-ink-secondary uppercase">
        <Icon size={14} aria-hidden />
        {label}
      </dt>
      <dd className="text-sm font-medium">{value}</dd>
    </div>
  )
}

const kinds = [
  { value: 'postpone', label: 'Adiar' },
  { value: 'venue', label: 'Mudar local' },
  { value: 'cancel', label: 'Cancelar evento' },
] as const

const submitLabel: Record<ChangeKind, string> = {
  postpone: 'Adiar e notificar',
  venue: 'Mudar local e notificar',
  cancel: 'Cancelar e notificar',
}

function ChangeEventDialog({
  event,
  now,
  onClose,
  onApply,
}: {
  event: OrgEvent
  now: Date
  onClose: () => void
  onApply: (change: EventChange) => void
}) {
  const [kind, setKind] = useState<ChangeKind>('postpone')
  const [date, setDate] = useState(event.date)
  const [start, setStart] = useState(event.start)
  const [venue, setVenue] = useState('')
  const [text, setText] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const found: Record<string, string> = {}
    if (kind === 'postpone' && date === event.date && start === event.start) {
      found.date = 'Escolhe uma nova data ou hora.'
    }
    if (kind === 'venue' && !venue.trim()) found.venue = 'Indica o novo local.'
    if (!confirmed) found.confirmed = 'Confirma que a alteração é oficial.'
    setErrors(found)
    if (Object.keys(found).length) return
    onApply({ kind, date, start, venue: venue.trim(), message: text.trim() })
  }

  return (
    <Dialog title="Alterar data, local ou cancelar" phoneTitle="Alterar evento" onClose={onClose}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <div className="flex items-center gap-3 sm:hidden">
          <span className="text-[15px] font-semibold">{event.name}</span>
          <span className="text-xs text-ink-secondary">{eventDateLabel(event, now)}</span>
        </div>
        <SegmentedControl
          label="Tipo de alteração"
          options={kinds}
          value={kind}
          onChange={setKind}
        />

        {kind === 'postpone' && (
          <div className="grid grid-cols-2 gap-3.5">
            <Field label="Nova data" error={errors.date}>
              {(p) => (
                <TextInput
                  {...p}
                  type="date"
                  icon={Calendar}
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                />
              )}
            </Field>
            <Field label="Nova hora">
              {(p) => (
                <TextInput
                  {...p}
                  type="time"
                  icon={Clock}
                  value={start}
                  onChange={(e) => setStart(e.target.value)}
                />
              )}
            </Field>
          </div>
        )}
        {kind === 'venue' && (
          <Field label="Novo local" error={errors.venue} help={`Local atual: ${event.venue}`}>
            {(p) => (
              <TextInput
                {...p}
                icon={MapPin}
                value={venue}
                onChange={(e) => setVenue(e.target.value)}
              />
            )}
          </Field>
        )}
        {kind === 'cancel' && (
          <Notice tone="warning" icon={Calendar} title="O evento deixa de estar disponível">
            Os participantes recebem a alteração oficial na carteira e por notificação.
          </Notice>
        )}

        <Field label="Mensagem para os participantes" optional>
          {(p) => (
            <TextArea
              {...p}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Ex.: Os bilhetes continuam válidos."
            />
          )}
        </Field>

        <div className="flex items-center gap-3 rounded-xl border border-warning-border bg-warning-soft px-3.5 py-3">
          <Users size={22} className="shrink-0 text-warning" aria-hidden />
          <div>
            <p className="text-sm font-semibold">
              Vamos notificar {formatNumber(connected(event))} pessoas ligadas a este evento
            </p>
            <p className="text-xs text-ink/80">Bilhetes e seguidores do evento.</p>
          </div>
        </div>

        <Checkbox checked={confirmed} onChange={setConfirmed} error={errors.confirmed}>
          <span className="font-medium">Confirmo que esta alteração é oficial.</span>
        </Checkbox>

        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="secondary" size="lg" onClick={onClose} className="max-sm:hidden">
            Voltar
          </Button>
          <Button
            type="submit"
            size="lg"
            icon={Send}
            variant={kind === 'cancel' ? 'danger' : 'primary'}
          >
            {submitLabel[kind]}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
