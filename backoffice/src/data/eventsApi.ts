import type { CrowdLevel } from '../ui/styles'
import { apiBaseUrl } from './auth'
import {
  categories,
  type ConfirmationSettings,
  type EventChange,
  type EventStatus,
  type IncidentCategory,
  type OrgEvent,
  type PublishedCrowd,
} from './organizer'

/** An organization's events in Core (API Outline, journeys 1 and 3). */
export interface EventsApi {
  /** The events of the signed-in member's organization. */
  list(): Promise<OrgEvent[]>
  /** Registers and publishes `event`; returns it as Core stored it. */
  create(event: OrgEvent): Promise<OrgEvent>
  /** Sends what changed from `before` to `after` that is not an official
   * change: name, description, capacity, code, queue points, confirmations. */
  correct(before: OrgEvent, after: OrgEvent): Promise<OrgEvent>
  /** Publishes an official change, which Core notifies to the participants. */
  publishChange(event: OrgEvent, change: EventChange): Promise<void>
  /** Publishes the crowd level that connected participants see at once. */
  publishCrowd(eventId: string, level: CrowdLevel): Promise<PublishedCrowd>
  /** The event's image, fetched with the token, which an image tag cannot send. */
  image(eventId: string): Promise<Blob>
  /** Uploads or replaces the event's image; Core removes its metadata. */
  uploadImage(eventId: string, file: Blob): Promise<void>
  /** Removes the event's image. */
  removeImage(eventId: string): Promise<void>
}

/** A refusal from Core, with its error code (API Outline). */
export class EventsApiError extends Error {
  readonly code: string

  constructor(code: string) {
    super(code)
    this.code = code
  }
}

interface EventJson {
  id: string
  name: string
  description: string
  type: 'TICKETED' | 'PUBLIC'
  status: 'SCHEDULED' | 'POSTPONED' | 'RELOCATED' | 'ONGOING' | 'ENDED' | 'CANCELLED'
  ticketEventCode: string | null
  accessCode: string | null
  startDateTime: string
  doorsOpenAt: string | null
  endDateTime: string
  venue: {
    name: string
    address: string
    district: string
    latitude: number | null
    longitude: number | null
  }
  capacity: number | null
  connectedUsers: number
  queuePoints: string[]
  confirmations: {
    categories: string[]
    venueRadiusMeters: number
    highConfidenceAnswers: number
    maxPromptsPerHour: number
  }
  imageUpdatedAt?: string | null
  crowd?: CrowdJson | null
}

interface CrowdJson {
  level: 'LOW' | 'MODERATE' | 'HIGH' | 'FULL'
  provenance: { sourceName: string; lastUpdatedAt: string }
}

function crowdOf(json: CrowdJson): PublishedCrowd {
  return {
    level: json.level.toLowerCase() as CrowdLevel,
    publishedBy: json.provenance.sourceName,
    publishedAt: new Date(json.provenance.lastUpdatedAt),
  }
}

/** A postponed or relocated event is still published, with its new values. */
const statusOf: Record<EventJson['status'], EventStatus> = {
  SCHEDULED: 'published',
  POSTPONED: 'published',
  RELOCATED: 'published',
  ONGOING: 'live',
  ENDED: 'ended',
  CANCELLED: 'cancelled',
}

/** The thumbnails' colours: events have no picture yet. */
const gradients: [string, string][] = [
  ['#596b9e', '#1a1f38'],
  ['rgb(51,140,128)', 'rgb(13,46,51)'],
  ['rgb(64,51,128)', 'rgb(13,13,38)'],
  ['rgb(217,107,77)', 'rgb(77,31,89)'],
]

function gradientFor(id: string): [string, string] {
  let sum = 0
  for (const c of id) sum += c.charCodeAt(0)
  return gradients[sum % gradients.length]
}

const pad = (n: number) => String(n).padStart(2, '0')

/** yyyy-mm-dd of `date` in the browser's time zone. */
export function localDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** hh:mm of `date` in the browser's time zone. */
export function localTime(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** The instant of `date` (yyyy-mm-dd) at `time` (hh:mm) in the browser's
 * time zone, which is the organization's. */
export function instantOf(date: string, time: string): Date {
  const [y, m, d] = date.split('-').map(Number)
  const [h, min] = time.split(':').map(Number)
  return new Date(y, m - 1, d, h, min)
}

function plusDays(date: Date, days: number): Date {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

/** The event's instants. The form has one date: an end at or before the start
 * is on the next day ("21:00 to 02:00"), and doors after the start opened the
 * day before. */
export function scheduleOf(e: Pick<OrgEvent, 'date' | 'start' | 'doors' | 'end'>): {
  startDateTime: string
  doorsOpenAt: string | null
  endDateTime: string
} {
  const start = instantOf(e.date, e.start)
  let end = instantOf(e.date, e.end)
  if (end <= start) end = plusDays(end, 1)
  let doors = e.doors ? instantOf(e.date, e.doors) : undefined
  if (doors && doors > start) doors = plusDays(doors, -1)
  return {
    startDateTime: start.toISOString(),
    doorsOpenAt: doors ? doors.toISOString() : null,
    endDateTime: end.toISOString(),
  }
}

function confirmationsOf(json: EventJson['confirmations']): ConfirmationSettings {
  const chosen = new Set(json.categories)
  const all = Object.keys(categories) as IncidentCategory[]
  return {
    categories: Object.fromEntries(all.map((c) => [c, chosen.has(categories[c].code)])) as Record<
      IncidentCategory,
      boolean
    >,
    radiusMeters: json.venueRadiusMeters,
    highConfidenceAnswers: json.highConfidenceAnswers,
    maxPromptsPerHour: json.maxPromptsPerHour,
  }
}

function confirmationsJson(settings: ConfirmationSettings) {
  const all = Object.keys(categories) as IncidentCategory[]
  return {
    categories: all
      .filter((c) => settings.categories[c] && !categories[c].personal)
      .map((c) => categories[c].code),
    venueRadiusMeters: settings.radiusMeters,
    highConfidenceAnswers: settings.highConfidenceAnswers,
    maxPromptsPerHour: settings.maxPromptsPerHour,
  }
}

/** Core's event in the shape O2–O4 show. */
export function fromJson(json: EventJson): OrgEvent {
  const start = new Date(json.startDateTime)
  return {
    id: json.id,
    name: json.name,
    description: json.description,
    date: localDate(start),
    doors: json.doorsOpenAt ? localTime(new Date(json.doorsOpenAt)) : '',
    start: localTime(start),
    end: localTime(new Date(json.endDateTime)),
    venue: json.venue.name,
    address: json.venue.address,
    latitude: json.venue.latitude ?? undefined,
    longitude: json.venue.longitude ?? undefined,
    entrances: json.queuePoints,
    district: json.venue.district,
    capacity: json.capacity ?? 0,
    connected: json.connectedUsers,
    status: statusOf[json.status],
    kind: json.type === 'TICKETED' ? 'ticketed' : 'public',
    code: json.ticketEventCode ?? json.accessCode ?? '',
    confirmations: confirmationsOf(json.confirmations),
    imageUpdatedAt: json.imageUpdatedAt ?? undefined,
    crowd: json.crowd ? crowdOf(json.crowd) : undefined,
    gradient: gradientFor(json.id),
  }
}

/** The code goes in the field of the event's type. */
function codeJson(e: Pick<OrgEvent, 'kind' | 'code'>) {
  const code = e.code.trim().toUpperCase()
  return e.kind === 'ticketed' ? { ticketEventCode: code } : { accessCode: code }
}

/** Body of `POST /events`. */
export function toCreateBody(e: OrgEvent) {
  return {
    name: e.name.trim(),
    description: e.description.trim(),
    type: e.kind === 'ticketed' ? 'TICKETED' : 'PUBLIC',
    ...codeJson(e),
    ...scheduleOf(e),
    venue: {
      name: e.venue.trim(),
      address: e.address.trim(),
      district: e.district,
      latitude: e.latitude ?? null,
      longitude: e.longitude ?? null,
    },
    capacity: e.capacity,
    queuePoints: e.entrances,
    confirmations: confirmationsJson(e.confirmations),
  }
}

/** Body of `PATCH /events/{eventId}`: only the fields that changed. */
export function toCorrectionBody(before: OrgEvent, after: OrgEvent): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  if (after.name.trim() !== before.name) body.name = after.name.trim()
  if (after.description.trim() !== before.description) body.description = after.description.trim()
  if (after.capacity !== before.capacity) body.capacity = after.capacity
  if (after.code.trim().toUpperCase() !== before.code) Object.assign(body, codeJson(after))
  if (after.entrances.join('\n') !== before.entrances.join('\n')) body.queuePoints = after.entrances
  const confirmations = confirmationsJson(after.confirmations)
  if (JSON.stringify(confirmations) !== JSON.stringify(confirmationsJson(before.confirmations))) {
    body.confirmations = confirmations
  }
  return body
}

const HALF_DAY = 12 * 60 * 60 * 1000

/** The day of a new start time with no date: the one that puts it nearest to
 * the current start, so 00:30 after a 23:00 start is the next day and 22:00
 * before a 01:00 start is the day before. */
export function nearestDate(e: Pick<OrgEvent, 'date' | 'start'>, time: string): string {
  const current = instantOf(e.date, e.start).getTime()
  let candidate = instantOf(e.date, time)
  if (candidate.getTime() - current < -HALF_DAY) candidate = plusDays(candidate, 1)
  else if (candidate.getTime() - current > HALF_DAY) candidate = plusDays(candidate, -1)
  return localDate(candidate)
}

/** Body of `POST /events/{eventId}/changes`. A postponement or a new start
 * moves the doors and the end with it unless they are sent (API Outline). */
export function toChangeBody(e: OrgEvent, change: EventChange) {
  const message = change.message.trim() || undefined
  const ticket =
    change.ticketValid === undefined
      ? {}
      : { ticketStillValid: change.ticketValid, refundInfo: change.refund?.trim() || undefined }
  switch (change.kind) {
    case 'postpone': {
      const moved = scheduleOf({
        date: change.date ?? e.date,
        start: change.start ?? e.start,
        doors: change.doors ?? e.doors,
        end: e.end,
      })
      return {
        type: 'POSTPONEMENT',
        newValue: { startDateTime: moved.startDateTime, doorsOpenAt: moved.doorsOpenAt },
        ...ticket,
        message,
      }
    }
    case 'time': {
      const moved = scheduleOf({
        date: change.start ? nearestDate(e, change.start) : e.date,
        start: change.start ?? e.start,
        doors: change.doors ?? e.doors,
        end: e.end,
      })
      return {
        type: 'TIME_CHANGE',
        newValue: {
          startDateTime: change.start !== e.start ? moved.startDateTime : undefined,
          doorsOpenAt: change.doors !== e.doors ? moved.doorsOpenAt : undefined,
        },
        message,
      }
    }
    case 'venue':
      return {
        type: 'VENUE_CHANGE',
        newValue: {
          venueName: change.venue,
          address: change.address,
          latitude: change.latitude,
          longitude: change.longitude,
        },
        message,
      }
    case 'cancel':
      return { type: 'CANCELLATION', ...ticket, message }
  }
}

/** {@link EventsApi} over HTTP, with the member's token. */
export function httpEventsApi(
  accessToken: string,
  fetchImpl: typeof fetch = fetch,
  baseUrl: string = apiBaseUrl,
): EventsApi {
  const base = baseUrl.replace(/\/$/, '')

  /** Sends the request and refuses with Core's error code. */
  async function send(method: string, path: string, init: RequestInit = {}): Promise<Response> {
    let response: Response
    try {
      response = await fetchImpl(`${base}${path}`, {
        ...init,
        method,
        headers: { Authorization: `Bearer ${accessToken}`, ...init.headers },
      })
    } catch {
      throw new EventsApiError('NETWORK')
    }
    if (!response.ok) {
      const json: unknown = await response.json().catch(() => undefined)
      const code =
        typeof json === 'object' && json !== null && 'code' in json
          ? String((json as { code: unknown }).code)
          : `HTTP_${response.status}`
      throw new EventsApiError(code)
    }
    return response
  }

  async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
    const response = await send(
      method,
      path,
      body === undefined
        ? {}
        : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) },
    )
    return (await response.json().catch(() => undefined)) as T
  }

  return {
    async list() {
      return (await call<EventJson[]>('GET', '/organizations/me/events')).map(fromJson)
    },
    async create(event) {
      return fromJson(await call<EventJson>('POST', '/events', toCreateBody(event)))
    },
    async correct(before, after) {
      const body = toCorrectionBody(before, after)
      if (Object.keys(body).length === 0) return before
      return fromJson(await call<EventJson>('PATCH', `/events/${before.id}`, body))
    },
    async publishChange(event, change) {
      await call('POST', `/events/${event.id}/changes`, toChangeBody(event, change))
    },
    async publishCrowd(eventId, level) {
      return crowdOf(
        await call<CrowdJson>('PUT', `/events/${eventId}/crowd`, { level: level.toUpperCase() }),
      )
    },
    async image(eventId) {
      return (await send('GET', `/events/${eventId}/image`)).blob()
    },
    async uploadImage(eventId, file) {
      await send('PUT', `/events/${eventId}/image`, {
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
        body: file,
      })
    },
    async removeImage(eventId) {
      await send('DELETE', `/events/${eventId}/image`)
    },
  }
}

/** The form field a refusal from Core is about, to show it there. */
export function errorField(code: string): keyof OrgEvent | undefined {
  if (code.startsWith('NAME_')) return 'name'
  if (code.startsWith('DESCRIPTION_')) return 'description'
  if (code.startsWith('CODE_') || code === 'EVENT_CODE_TAKEN') return 'code'
  if (code.startsWith('CAPACITY_')) return 'capacity'
  if (code.startsWith('QUEUE_POINT') || code === 'TOO_MANY_QUEUE_POINTS') return 'entrances'
  if (code === 'START_IN_PAST' || code === 'START_REQUIRED' || code === 'DOORS_AFTER_START') {
    return 'start'
  }
  if (code === 'END_REQUIRED' || code === 'END_BEFORE_START') return 'end'
  if (code === 'DISTRICT_UNKNOWN') return 'district'
  if (code === 'LOCATION_REQUIRED' || code.startsWith('COORDINATES_')) return 'address'
  if (code === 'VENUE_REQUIRED') return 'venue'
  return undefined
}

/** The member's message for a refusal from Core, in European Portuguese. */
export function eventErrorMessage(code: string): string {
  switch (code) {
    case 'EVENT_CODE_TAKEN':
      return 'Este código já é usado por outro evento futuro. Escolhe outro código.'
    case 'CODE_INVALID':
      return 'Usa 4 a 64 letras, algarismos ou hífenes, sem espaços nem acentos.'
    case 'START_IN_PAST':
      return 'O evento tem de começar no futuro.'
    case 'DOORS_AFTER_START':
      return 'As portas têm de abrir antes do início.'
    case 'END_BEFORE_START':
      return 'O evento tem de terminar depois de começar.'
    case 'POSTPONEMENT_NOT_LATER':
      return 'Um adiamento passa o evento para mais tarde. Para o antecipar, muda a hora.'
    case 'NOTHING_CHANGES':
      return 'O evento já tem estes valores.'
    case 'DISTRICT_UNKNOWN':
      return 'Escolhe um distrito da lista.'
    case 'LOCATION_REQUIRED':
      return 'Não encontrámos esta morada no mapa. Confirma a rua, o número e a localidade.'
    case 'CAPACITY_OUT_OF_RANGE':
    case 'CAPACITY_REQUIRED':
      return 'Indica a capacidade do recinto, de 1 a 1 000 000 pessoas.'
    case 'QUEUE_POINT_REPEATED':
      return 'Cada entrada ou ponto de fila tem um nome diferente.'
    case 'EVENT_CLOSED':
      return 'Este evento já foi cancelado ou terminou.'
    case 'NOT_ORGANIZATION_MEMBER':
    case 'NOT_EVENT_ORGANIZATION':
      return 'Só os membros ativos da organização podem gerir os seus eventos.'
    case 'IMAGE_TOO_LARGE':
      return 'A imagem tem mais de 5 MB. Escolhe uma mais pequena.'
    case 'IMAGE_TYPE_NOT_ACCEPTED':
      return 'Escolhe uma imagem JPEG, PNG ou WebP.'
    case 'IMAGE_INVALID':
      return 'Não foi possível ler esta imagem. Escolhe outra.'
    case 'NETWORK':
      return 'Não foi possível ligar ao Crowded. Verifica a ligação e tenta novamente.'
    default:
      return 'Não foi possível guardar. Verifica os dados e tenta novamente.'
  }
}
