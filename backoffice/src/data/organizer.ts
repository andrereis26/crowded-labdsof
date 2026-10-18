/** Organizer area (O1–O14), backed by demo data until the Core API exists.
 * Times are built from `now` so the demo always reads "há 3 min". */

import type { Confidence } from '../ui/components'
import type { CrowdLevel } from '../ui/styles'
import festivalImage from '../assets/festival-margens-2026.jpg'

export const organization = {
  name: 'Organização Margens',
  initials: 'OM',
  verified: true,
  nipc: '500123123',
  email: 'miguel@organizacaomargens.pt',
  website: 'organizacaomargens.pt',
  user: { name: 'Miguel Sousa', initials: 'MS', role: 'Coordenador de operações' },
}

export type EventStatus = 'draft' | 'published' | 'live' | 'ended' | 'cancelled'
export type EventSource = 'crowded' | 'ticketing'
export type Visibility = 'public' | 'link'

export interface OrgEvent {
  id: string
  name: string
  description: string
  /** yyyy-mm-dd */
  date: string
  /** hh:mm */
  doors: string
  start: string
  end: string
  venue: string
  entrances: string[]
  capacity: number
  tickets: number
  followers: number
  status: EventStatus
  source: EventSource
  visibility: Visibility
  askConfirmation: boolean
  showEstimates: boolean
  image?: string
  imageName?: string
  gradient: [string, string]
}

export const statusTag: Record<
  EventStatus,
  { label: string; tone: 'success' | 'info' | 'neutral' | 'danger'; dot: boolean }
> = {
  draft: { label: 'Rascunho', tone: 'neutral', dot: true },
  published: { label: 'Publicado', tone: 'info', dot: false },
  live: { label: 'A decorrer', tone: 'success', dot: true },
  ended: { label: 'Terminado', tone: 'neutral', dot: false },
  cancelled: { label: 'Cancelado', tone: 'danger', dot: false },
}

export const connected = (e: OrgEvent) => e.tickets + e.followers

const weekdays = ['DOM', 'SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB']
const months = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ']

export function parseDate(date: string): Date {
  const [y, m, d] = date.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** "SÁB 17 OUT · 22:00", or "SÁB 18 OUT 2025" for another year. */
export function eventDateLabel(e: Pick<OrgEvent, 'date' | 'start'>, now: Date): string {
  if (!e.date) return 'Data por definir'
  const d = parseDate(e.date)
  const day = `${weekdays[d.getDay()]} ${d.getDate()} ${months[d.getMonth()]}`
  if (d.getFullYear() !== now.getFullYear()) return `${day} ${d.getFullYear()}`
  return e.start ? `${day} · ${e.start}` : day
}

/** "12 DEZ", for the wallet card. */
export function shortDate(date: string): string {
  if (!date) return ''
  const d = parseDate(date)
  return `${d.getDate()} ${months[d.getMonth()]}`
}

/** "Hoje · 22:00 · Parque da Cidade" when the event is today. */
export function eventSubtitle(e: OrgEvent, now: Date): string {
  const d = parseDate(e.date)
  const today = d.toDateString() === now.toDateString()
  const venue = e.venue.split(',')[0]
  return `${today ? `Hoje · ${e.start}` : eventDateLabel(e, now)} · ${venue}`
}

/** Digit groups split by a no-break space, as in the mockups: 1 680. The
 * pt-PT locale does not group four-digit numbers, so this is done by hand. */
export function formatNumber(n: number): string {
  return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '\u00a0')
}

export function formatTime(date: Date): string {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

/** "agora", "há 5 min", "há 2 h". */
export function ago(date: Date, now: Date): string {
  const minutes = Math.floor((now.getTime() - date.getTime()) / 60000)
  if (minutes < 1) return 'agora'
  if (minutes < 60) return `há ${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `há ${hours} h`
  const days = Math.floor(hours / 24)
  return `há ${days} ${days === 1 ? 'dia' : 'dias'}`
}

export function slug(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
}

export function demoEvents(): OrgEvent[] {
  return [
    {
      id: 'festival-margens-2026',
      name: 'Festival Margens 2026',
      description: 'Festival de música com três palcos à beira-rio.',
      date: '2026-10-17',
      doors: '20:00',
      start: '22:00',
      end: '02:00',
      venue: 'Parque da Cidade, Porto',
      entrances: ['Porta A', 'Porta B', 'Porta C', 'Bar principal', 'WC Norte'],
      capacity: 4500,
      tickets: 930,
      followers: 310,
      status: 'live',
      source: 'crowded',
      visibility: 'public',
      askConfirmation: true,
      showEstimates: true,
      image: festivalImage,
      imageName: 'margens-2026.jpg',
      gradient: ['#596b9e', '#1a1f38'],
    },
    {
      id: 'jazz-no-parque',
      name: 'Jazz no Parque',
      description: 'Concerto ao ar livre no Jardim do Palácio.',
      date: '2026-10-18',
      doors: '18:00',
      start: '19:00',
      end: '22:00',
      venue: 'Jardim do Palácio, Porto',
      entrances: ['Entrada principal'],
      capacity: 800,
      tickets: 250,
      followers: 62,
      status: 'published',
      source: 'ticketing',
      visibility: 'public',
      askConfirmation: false,
      showEstimates: true,
      gradient: ['rgb(51,140,128)', 'rgb(13,46,51)'],
    },
    {
      id: 'margens-inverno',
      name: 'Margens Inverno',
      description: 'Concerto de inverno com artistas da cena independente do Porto.',
      date: '2026-12-12',
      doors: '20:00',
      start: '21:00',
      end: '02:00',
      venue: 'Pavilhão Municipal, Porto',
      entrances: ['Porta A', 'Porta B', 'Bar principal'],
      capacity: 4500,
      tickets: 96,
      followers: 32,
      status: 'published',
      source: 'crowded',
      visibility: 'public',
      askConfirmation: true,
      showEstimates: true,
      imageName: 'inverno-2026.jpg',
      gradient: ['rgb(64,51,128)', 'rgb(13,13,38)'],
    },
    {
      id: 'festival-margens-2025',
      name: 'Festival Margens 2025',
      description: 'Edição de 2025 do Festival Margens.',
      date: '2025-10-18',
      doors: '20:00',
      start: '22:00',
      end: '02:00',
      venue: 'Parque da Cidade, Porto',
      entrances: ['Porta A', 'Porta C', 'Bar principal', 'WC Norte'],
      capacity: 4500,
      tickets: 940,
      followers: 300,
      status: 'ended',
      source: 'crowded',
      visibility: 'public',
      askConfirmation: true,
      showEstimates: true,
      gradient: ['rgb(217,107,77)', 'rgb(77,31,89)'],
    },
  ]
}

export function emptyEvent(): OrgEvent {
  return {
    id: '',
    name: '',
    description: '',
    date: '',
    doors: '',
    start: '',
    end: '',
    venue: '',
    entrances: ['Porta A'],
    capacity: 0,
    tickets: 0,
    followers: 0,
    status: 'draft',
    source: 'crowded',
    visibility: 'public',
    askConfirmation: true,
    showEstimates: true,
    gradient: ['rgb(64,51,128)', 'rgb(13,13,38)'],
  }
}

/** Checks what an event needs before being published. */
export function validateEvent(e: OrgEvent): Partial<Record<keyof OrgEvent, string>> {
  const errors: Partial<Record<keyof OrgEvent, string>> = {}
  if (!e.name.trim()) errors.name = 'Escreve o nome do evento.'
  if (!e.date) errors.date = 'Escolhe a data.'
  if (!e.start) errors.start = 'Indica a hora de início.'
  if (!e.venue.trim()) errors.venue = 'Indica o local.'
  if (!(e.capacity > 0)) errors.capacity = 'Indica a capacidade do recinto.'
  return errors
}

// ——— Event changes (O4) ———

export type ChangeKind = 'postpone' | 'venue' | 'cancel'

export interface EventChange {
  kind: ChangeKind
  date?: string
  start?: string
  venue?: string
  message: string
}

export function applyChange(e: OrgEvent, change: EventChange): OrgEvent {
  if (change.kind === 'postpone') {
    return { ...e, date: change.date ?? e.date, start: change.start ?? e.start }
  }
  if (change.kind === 'venue') return { ...e, venue: change.venue ?? e.venue }
  return { ...e, status: 'cancelled' }
}

// ——— Live operations (O5, O7) ———

export type QueueKind = 'entrance' | 'service'

export interface QueuePoint {
  id: string
  name: string
  kind: QueueKind
  /** Automatic values come from the entry counter. */
  source: 'counter' | 'manual'
  minutes: number
  open: boolean
  rising: boolean
  updatedAt: Date
}

export interface LiveState {
  level: CrowdLevel
  levelPublishedAt: Date
  levelPublishedBy: string
  entries: number
  connectedLastHour: number
  hourly: { hour: string; count: number }[]
  queues: QueuePoint[]
  services: { name: string; detail: string; ok: boolean }[]
}

/** A manual queue time older than this is shown as out of date. */
export const STALE_MINUTES = 20

/** Level suggested by the entry counter. */
export function suggestedLevel(entries: number, capacity: number): CrowdLevel {
  const ratio = entries / capacity
  if (ratio < 0.4) return 'low'
  if (ratio < 0.75) return 'moderate'
  if (ratio < 0.95) return 'high'
  return 'full'
}

export function isStale(q: QueuePoint, now: Date): boolean {
  return q.source === 'manual' && now.getTime() - q.updatedAt.getTime() > STALE_MINUTES * 60000
}

export function demoLive(now: Date): Record<string, LiveState> {
  const minAgo = (m: number) => new Date(now.getTime() - m * 60000)
  return {
    'festival-margens-2026': {
      level: 'moderate',
      levelPublishedAt: minAgo(12),
      levelPublishedBy: organization.user.name,
      entries: 2790,
      connectedLastHour: 38,
      hourly: [
        { hour: '18h', count: 70 },
        { hour: '19h', count: 190 },
        { hour: '20h', count: 680 },
        { hour: '21h', count: 830 },
        { hour: '22h', count: 520 },
        { hour: '23h', count: 105 },
      ],
      queues: [
        { id: 'porta-a', name: 'Porta A', kind: 'entrance', source: 'counter', minutes: 10, open: true, rising: false, updatedAt: minAgo(1) },
        { id: 'porta-b', name: 'Porta B', kind: 'entrance', source: 'manual', minutes: 0, open: false, rising: false, updatedAt: minAgo(3) },
        { id: 'porta-c', name: 'Porta C', kind: 'entrance', source: 'counter', minutes: 25, open: true, rising: true, updatedAt: minAgo(1) },
        { id: 'bar-principal', name: 'Bar principal', kind: 'service', source: 'manual', minutes: 5, open: true, rising: false, updatedAt: minAgo(9) },
        { id: 'wc-norte', name: 'WC Norte', kind: 'service', source: 'manual', minutes: 8, open: true, rising: false, updatedAt: minAgo(24) },
      ],
      services: [
        { name: 'Contador de entradas', detail: 'Ligado · há 1 min', ok: true },
        { name: 'Notificações', detail: 'A funcionar · 0,8 s em média', ok: true },
        { name: 'Rascunhos com IA', detail: 'Lento', ok: false },
        { name: 'Sincronização da bilheteira', detail: 'Há 4 min', ok: true },
      ],
    },
  }
}

// ——— Incidents (O8, O9) ———

export type IncidentCategory = 'crowd' | 'queue' | 'accessibility' | 'venue' | 'lost'
export type IncidentStatus = 'open' | 'in-progress' | 'resolved'

export interface Report {
  id: string
  text: string
  at: Date
  /** Wait estimate given by the participant, e.g. "20–40 min". */
  estimate?: string
}

export interface Incident {
  id: string
  eventId: string
  title: string
  category: IncidentCategory
  /** Overrides the category icon, e.g. water for a venue problem. */
  icon?: 'water'
  status: IncidentStatus
  urgent: boolean
  confidence: Confidence
  summary: string
  reports: Report[]
  confirmations: { yes: number; no: number }
  assignee?: string
  createdAt: Date
  resolvedAt?: Date
  history: { at: Date; text: string }[]
}

export const categories: Record<
  IncidentCategory,
  { label: string; example: string; personal?: boolean }
> = {
  crowd: { label: 'Lotação', example: 'Ex.: muita gente junto ao palco' },
  queue: { label: 'Fila', example: 'Ex.: espera longa numa porta' },
  accessibility: { label: 'Acessibilidade', example: 'Ex.: rampa ou elevador bloqueado' },
  venue: { label: 'Problema no recinto', example: 'Ex.: falta de água, avaria' },
  lost: { label: 'Perdidos e achados', example: 'Nunca pede confirmação nem é público.', personal: true },
}

export const team = [
  { name: 'Ana Ramos', initials: 'AR', team: 'Equipa de portas' },
  { name: 'Miguel Sousa', initials: 'MS', team: 'Coordenação' },
  { name: 'Rui Matos', initials: 'RM', team: 'Equipa do recinto' },
]

export const incidentStatusTag: Record<
  IncidentStatus,
  { label: string; tone: 'neutral' | 'info' | 'success' }
> = {
  open: { label: 'Aberto', tone: 'neutral' },
  'in-progress': { label: 'Em curso', tone: 'info' },
  resolved: { label: 'Resolvido', tone: 'success' },
}

/** Urgent open incidents show "Urgente" instead of their status. */
export function incidentTag(i: Incident): { label: string; tone: 'danger' | 'neutral' | 'info' | 'success' } {
  if (i.urgent && i.status !== 'resolved') return { label: 'Urgente', tone: 'danger' }
  return incidentStatusTag[i.status]
}

export function reportsLabel(n: number): string {
  return `${n} ${n === 1 ? 'reporte' : 'reportes'}`
}

export function demoIncidents(now: Date): Incident[] {
  const minAgo = (m: number) => new Date(now.getTime() - m * 60000)
  const queueTexts: [string, string][] = [
    ['A fila está a crescer muito depressa.', '20–40 min'],
    ['Estamos parados há 25 minutos.', '20–40 min'],
    ['Só uma pessoa a ler bilhetes.', '> 40 min'],
    ['Porta B fechada, toda a gente veio para aqui.', '20–40 min'],
    ['Não há ninguém a organizar a fila.', '20–40 min'],
    ['A fila já chega à rotunda.', '> 40 min'],
    ['Muito tempo de espera na Porta C.', '20–40 min'],
    ['Os leitores de bilhetes estão lentos.', '20–40 min'],
    ['Vale a pena abrir outra porta.', '20–40 min'],
  ]
  return [
    {
      id: 'fila-porta-c',
      eventId: 'festival-margens-2026',
      title: 'Fila longa na Porta C',
      category: 'queue',
      status: 'open',
      urgent: true,
      confidence: 'high',
      summary:
        'Vários participantes relatam espera superior a 20 minutos na Porta C, a aumentar desde as 22:30, provavelmente por causa do fecho da Porta B.',
      reports: queueTexts.map(([text, estimate], i) => ({
        id: `r${i + 1}`,
        text,
        estimate,
        at: minAgo(13 - i),
      })),
      confirmations: { yes: 12, no: 2 },
      assignee: 'Ana Ramos',
      createdAt: minAgo(11),
      history: [
        { at: minAgo(11), text: 'Criado a partir de 3 reportes' },
        { at: minAgo(6), text: 'Atribuído a Ana Ramos' },
        { at: minAgo(2), text: '6 reportes adicionados ao grupo' },
      ],
    },
    {
      id: 'rampa-norte',
      eventId: 'festival-margens-2026',
      title: 'Rampa da entrada Norte bloqueada',
      category: 'accessibility',
      status: 'open',
      urgent: false,
      confidence: 'medium',
      summary:
        'Três participantes indicam que a rampa da entrada Norte está ocupada por grades, o que impede a passagem de cadeiras de rodas.',
      reports: [
        { id: 'r1', text: 'Há grades em cima da rampa.', at: minAgo(15) },
        { id: 'r2', text: 'Não consigo passar com a cadeira de rodas.', at: minAgo(13) },
        { id: 'r3', text: 'A rampa da entrada Norte está bloqueada.', at: minAgo(12) },
      ],
      confirmations: { yes: 4, no: 1 },
      createdAt: minAgo(15),
      history: [{ at: minAgo(15), text: 'Criado a partir de 3 reportes' }],
    },
    {
      id: 'lotacao-palco',
      eventId: 'festival-margens-2026',
      title: 'Lotação elevada junto ao palco',
      category: 'crowd',
      status: 'in-progress',
      urgent: false,
      confidence: 'medium',
      summary: 'Participantes relatam muita gente e dificuldade em sair da zona em frente ao palco principal.',
      reports: [
        { id: 'r1', text: 'Muito apertado junto às grades.', at: minAgo(18) },
        { id: 'r2', text: 'Difícil sair da frente do palco.', at: minAgo(17) },
        { id: 'r3', text: 'Está demasiado cheio aqui.', at: minAgo(16) },
        { id: 'r4', text: 'Pessoas a empurrar.', at: minAgo(15) },
        { id: 'r5', text: 'Falta espaço à frente.', at: minAgo(14) },
      ],
      confirmations: { yes: 7, no: 3 },
      assignee: 'Ana Ramos',
      createdAt: minAgo(18),
      history: [
        { at: minAgo(18), text: 'Criado a partir de 5 reportes' },
        { at: minAgo(14), text: 'Atribuído a Ana Ramos' },
      ],
    },
    {
      id: 'carteira-castanha',
      eventId: 'festival-margens-2026',
      title: 'Carteira castanha perdida',
      category: 'lost',
      status: 'open',
      urgent: false,
      confidence: 'low',
      summary: 'Um participante perdeu uma carteira castanha perto do bar principal.',
      reports: [{ id: 'r1', text: 'Perdi uma carteira castanha perto do bar.', at: minAgo(30) }],
      confirmations: { yes: 0, no: 0 },
      createdAt: minAgo(30),
      history: [{ at: minAgo(30), text: 'Reporte privado recebido' }],
    },
    {
      id: 'agua-bar',
      eventId: 'festival-margens-2026',
      title: 'Falta de água no bar principal',
      category: 'venue',
      icon: 'water',
      status: 'resolved',
      urgent: false,
      confidence: 'medium',
      summary: 'O bar principal ficou sem garrafas de água durante cerca de 20 minutos.',
      reports: [
        { id: 'r1', text: 'Não há água no bar.', at: minAgo(50) },
        { id: 'r2', text: 'O bar principal não tem água.', at: minAgo(48) },
      ],
      confirmations: { yes: 5, no: 0 },
      createdAt: minAgo(50),
      resolvedAt: minAgo(25),
      history: [
        { at: minAgo(50), text: 'Criado a partir de 2 reportes' },
        { at: minAgo(25), text: 'Resolvido por Miguel Sousa' },
      ],
    },
  ]
}

// ——— Report confirmations (O10) ———

export interface ConfirmationSettings {
  categories: Record<IncidentCategory, boolean>
  radius: string
  highConfidenceAnswers: number
  maxRequests: string
}

export const reportsToday: Record<IncidentCategory, number> = {
  crowd: 14,
  queue: 21,
  accessibility: 3,
  venue: 4,
  lost: 2,
}

export const demoConfirmationSettings = (): ConfirmationSettings => ({
  categories: { crowd: true, queue: true, accessibility: true, venue: false, lost: false },
  radius: '500 m',
  highConfidenceAnswers: 8,
  maxRequests: '3 por hora',
})

// ——— Alerts (O6) ———

export type AlertImportance = 'info' | 'important' | 'urgent'
export type Audience = 'all' | 'tickets' | 'followers'

export interface SentAlert {
  id: string
  eventId: string
  title: string
  message: string
  importance: AlertImportance
  sentAt: Date
  delivered: number
  seconds: number
}

export function audienceSize(e: OrgEvent, audience: Audience): number {
  if (audience === 'tickets') return e.tickets
  if (audience === 'followers') return e.followers
  return connected(e)
}

export function demoAlerts(now: Date): SentAlert[] {
  const minAgo = (m: number) => new Date(now.getTime() - m * 60000)
  const base = { importance: 'info' as const, message: '' }
  return [
    { ...base, id: 'a1', eventId: 'festival-margens-2026', title: 'Água disponível no bar principal', sentAt: minAgo(3), delivered: 1240, seconds: 0.8 },
    { ...base, id: 'a2', eventId: 'festival-margens-2026', title: 'Portas abertas', sentAt: minAgo(140), delivered: 1180, seconds: 0.9 },
    { ...base, id: 'a3', eventId: 'festival-margens-2026', title: 'Bilhetes disponíveis na bilheteira', sentAt: minAgo(26 * 60), delivered: 1100, seconds: 0.9 },
    { ...base, id: 'a4', eventId: 'festival-margens-2026', title: 'Horários dos palcos publicados', sentAt: minAgo(2 * 24 * 60), delivered: 1050, seconds: 1.1 },
    { ...base, id: 'a5', eventId: 'jazz-no-parque', title: 'Entrada pelo portão norte', sentAt: minAgo(3 * 24 * 60), delivered: 290, seconds: 0.7 },
    { ...base, id: 'a6', eventId: 'margens-inverno', title: 'Primeiros artistas anunciados', sentAt: minAgo(5 * 24 * 60), delivered: 120, seconds: 0.6 },
  ]
}

/** Stand-in for the AI drafting service (FR-042) until it exists: turns the
 * organizer's description into a title and a clear message. */
export function draftAlert(description: string): { title: string; message: string } {
  const text = description.trim().replace(/\s+/g, ' ')
  const sentences = text.split(/(?<=[.!?])\s+/).filter(Boolean)
  const first = (sentences[0] ?? text).replace(/[.!?]$/, '')
  const title = first.split(/ (?:por|devido a|porque) /)[0].slice(0, 60)
  const doorClosed = /^(.+?) fechad[ao]/i.exec(first)
  const redirect = /desvia[r]? para (?:a |o )?(.+?)(?:,\s*(.+))?$/i.exec(sentences[1] ?? '')
  if (doorClosed && redirect) {
    const place = redirect[1].replace(/[.!?]$/, '')
    const where = redirect[2] ? `, a ${redirect[2].replace(/[.!?]$/, '')} da entrada principal` : ''
    return {
      title,
      message: `A ${doorClosed[1]} está temporariamente fechada. Use a ${place}${where}. Obrigado pela paciência.`,
    }
  }
  return { title, message: `${sentences.join(' ')} Obrigado pela compreensão.`.trim() }
}

// ——— Polls (O11) ———

export interface PollQuestion {
  text: string
  options: { label: string; votes: number; negative?: boolean }[]
}

export interface Poll {
  id: string
  eventId: string
  questions: PollQuestion[]
  closesAt: Date
  /** People connected to the event when the poll was published. */
  audience: number
  insight?: { title: string; detail: string }
}

export function pollResponses(p: Poll): number {
  return p.questions[0]?.options.reduce((sum, o) => sum + o.votes, 0) ?? 0
}

export function demoPolls(): Poll[] {
  return [
    {
      id: 'presenca-inverno',
      eventId: 'margens-inverno',
      closesAt: new Date(2026, 11, 10, 23, 59),
      audience: 1280,
      questions: [
        {
          text: 'Vais ao Margens Inverno a 12 de dezembro?',
          options: [
            { label: 'Sim, vou', votes: 296 },
            { label: 'Talvez', votes: 117 },
            { label: 'Não vou', votes: 73, negative: true },
          ],
        },
        {
          text: 'A que horas pensas chegar?',
          options: [
            { label: 'Antes das 20h', votes: 76 },
            { label: 'Entre as 20h e as 22h', votes: 269 },
            { label: 'Depois das 22h', votes: 76 },
          ],
        },
      ],
      insight: {
        title: 'Cerca de 2 em cada 3 pessoas pensam chegar entre as 20h e as 22h.',
        detail:
          'Considera reforçar as entradas nesse período. É uma indicação, não uma garantia de presença.',
      },
    },
  ]
}

// ——— Requests to participants (O12) ———

export type RequestType = 'confirmation' | 'poll' | 'idea'

export const requestTypes: Record<RequestType, { label: string; hint: string }> = {
  confirmation: { label: 'Confirmação', hint: 'Sim ou Não, com prazo' },
  poll: { label: 'Sondagem', hint: 'Várias opções, anónima' },
  idea: { label: 'Ideia', hint: 'Resposta livre' },
}

export const responseOptions: Record<RequestType, string[]> = {
  confirmation: ['Sim / Não', 'Sim / Não / Talvez'],
  poll: ['Escolha única', 'Escolha múltipla'],
  idea: [],
}

// ——— Management requests (O13) ———

export interface CatalogEvent {
  id: string
  name: string
  date: string
  start: string
  venue: string
  gradient: [string, string]
}

/** Events synced from ticketing that the organization does not manage yet. */
export const catalog: CatalogEvent[] = [
  { id: 'jazz-no-parque-sabado', name: 'Jazz no Parque', date: '2026-10-18', start: '19:00', venue: 'Jardim do Palácio, Porto', gradient: ['rgb(51,140,128)', 'rgb(13,46,51)'] },
  { id: 'jazz-no-parque-domingo', name: 'Jazz no Parque · Sessão de domingo', date: '2026-10-25', start: '18:00', venue: 'Jardim do Palácio, Porto', gradient: ['rgb(51,140,128)', 'rgb(13,46,51)'] },
  { id: 'noite-fado', name: 'Noite de Fado na Ribeira', date: '2026-11-07', start: '21:30', venue: 'Cais da Ribeira, Porto', gradient: ['rgb(140,90,51)', 'rgb(46,26,13)'] },
  { id: 'porto-electronic', name: 'Porto Electronic Weekend', date: '2026-11-20', start: '23:00', venue: 'Alfândega do Porto', gradient: ['rgb(51,64,140)', 'rgb(13,13,46)'] },
]

export const relations = ['Organizador', 'Promotor', 'Gestor do recinto', 'Outra'] as const

// ——— Post-event summary (O14) ———

export interface EventSummary {
  window: string
  warnedBeforeDoors: { reached: number; total: number }
  peak: { ratio: number; at: string; people: number }
  incidents: { total: number; minutesToAcknowledge: number }
  alerts: { total: number; deliveries: number; push: number; appOnly: number }
  hourly: { hour: string; count: number }[]
  maxQueues: { name: string; minutes: number; peakAt?: string; tone: 'ok' | 'warning' | 'muted' }[]
  incidentList: {
    title: string
    category: IncidentCategory
    icon?: 'water'
    reports: number
    resolvedIn: number
    confidence: Confidence
  }[]
  alertList: { title: string; sentAt: string; resolvedAt: string }[]
}

export const summaries: Record<string, EventSummary> = {
  'festival-margens-2025': {
    window: '18 out, 20:00–02:00',
    warnedBeforeDoors: { reached: 1166, total: 1240 },
    peak: { ratio: 0.92, at: '23:10', people: 4140 },
    incidents: { total: 7, minutesToAcknowledge: 6 },
    alerts: { total: 3, deliveries: 3720, push: 3610, appOnly: 110 },
    hourly: [
      { hour: '18h', count: 70 },
      { hour: '19h', count: 190 },
      { hour: '20h', count: 680 },
      { hour: '21h', count: 830 },
      { hour: '22h', count: 520 },
      { hour: '23h', count: 105 },
    ],
    maxQueues: [
      { name: 'Porta A', minutes: 15, tone: 'ok' },
      { name: 'Porta C', minutes: 35, peakAt: '22:40', tone: 'warning' },
      { name: 'Bar principal', minutes: 20, tone: 'ok' },
      { name: 'WC Norte', minutes: 12, peakAt: '00:30', tone: 'muted' },
    ],
    incidentList: [
      { title: 'Fila longa na Porta C', category: 'queue', reports: 9, resolvedIn: 18, confidence: 'high' },
      { title: 'Rampa da entrada Norte bloqueada', category: 'accessibility', reports: 3, resolvedIn: 42, confidence: 'medium' },
      { title: 'Falta de água no bar principal', category: 'venue', icon: 'water', reports: 2, resolvedIn: 25, confidence: 'medium' },
    ],
    alertList: [
      { title: 'Porta B fechada', sentAt: '22:41', resolvedAt: '23:05' },
      { title: 'Fila longa na Porta C', sentAt: '22:50', resolvedAt: '23:08' },
      { title: 'Chuva a partir da 01:00', sentAt: '00:15', resolvedAt: '01:30' },
    ],
  },
}
