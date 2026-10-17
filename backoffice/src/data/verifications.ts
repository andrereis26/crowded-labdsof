/** Organization verification requests (A1, FR-061), backed by demo data until
 * the Core API exists. */

export type RequestKind = 'registration' | 'management'

export type VerificationStatus =
  | 'pending'
  | 'info-requested'
  | 'approved'
  | 'rejected'

export type Decision = 'approve' | 'request-info' | 'reject'

export interface AutomaticCheck {
  label: string
  passed: boolean
}

export interface VerificationRequest {
  id: string
  organization: string
  kind: RequestKind
  /** Event the organization wants to manage, for management requests. */
  event?: string
  submittedAt: Date
  submittedBy: string
  /** Shown masked: only the first and last digits. */
  nipc: string
  email: string
  website: string
  checks: AutomaticCheck[]
  document: { name: string; size: string; pages: number }
  status: VerificationStatus
  note?: string
}

/** First letters of the first and last words: "Associação Cultural do
 * Bonfim" → "AB". */
export function initials(name: string): string {
  const words = name.split(/\s+/).filter(Boolean)
  const first = words[0]?.[0] ?? ''
  const last = words.length > 1 ? words[words.length - 1][0] : ''
  return (first + last).toUpperCase()
}

/** "500 •••• 123" */
export function maskNipc(nipc: string): string {
  return `${nipc.slice(0, 3)} •••• ${nipc.slice(-3)}`
}

/** "agora", "há 5 min", "há 2 h", "há 1 dia". */
export function timeAgo(date: Date, now: Date): string {
  const minutes = Math.floor((now.getTime() - date.getTime()) / 60000)
  if (minutes < 1) return 'agora'
  if (minutes < 60) return `há ${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `há ${hours} h`
  const days = Math.floor(hours / 24)
  return days === 1 ? 'há 1 dia' : `há ${days} dias`
}

/** "hoje às 10:42" or "12/10 às 10:42". */
export function submittedLabel(date: Date, now: Date): string {
  const time = date.toLocaleTimeString('pt-PT', {
    hour: '2-digit',
    minute: '2-digit',
  })
  const sameDay = date.toDateString() === now.toDateString()
  const day = sameDay
    ? 'hoje'
    : date.toLocaleDateString('pt-PT', { day: '2-digit', month: '2-digit' })
  return `${day} às ${time}`
}

export function demoRequests(now: Date): VerificationRequest[] {
  const ago = (minutes: number) => new Date(now.getTime() - minutes * 60000)
  return [
    {
      id: 'organizacao-margens',
      organization: 'Organização Margens',
      kind: 'registration',
      submittedAt: ago(120),
      submittedBy: 'Miguel Sousa',
      nipc: '500123123',
      email: 'miguel@organizacaomargens.pt',
      website: 'organizacaomargens.pt',
      checks: [
        { label: 'Formato do NIPC válido', passed: true },
        { label: 'Domínio do email igual ao do site', passed: true },
        { label: 'Documento carregado e legível', passed: true },
        {
          label: 'Nome no documento: “Associação Margens” — confirmar',
          passed: false,
        },
      ],
      document: { name: 'certidao-permanente.pdf', size: '1,2 MB', pages: 3 },
      status: 'pending',
    },
    {
      id: 'associacao-bonfim',
      organization: 'Associação Cultural do Bonfim',
      kind: 'registration',
      submittedAt: ago(26 * 60),
      submittedBy: 'Joana Pires',
      nipc: '508765432',
      email: 'geral@acbonfim.pt',
      website: 'acbonfim.pt',
      checks: [
        { label: 'Formato do NIPC válido', passed: true },
        { label: 'Domínio do email igual ao do site', passed: true },
        { label: 'Documento carregado e legível', passed: true },
      ],
      document: { name: 'estatutos.pdf', size: '640 KB', pages: 8 },
      status: 'pending',
    },
    {
      id: 'teatro-municipal',
      organization: 'Teatro Municipal',
      kind: 'management',
      event: 'A Tempestade',
      submittedAt: ago(5 * 60),
      submittedBy: 'Carla Reis',
      nipc: '501306099',
      email: 'programacao@teatromunicipal.pt',
      website: 'teatromunicipal.pt',
      checks: [
        { label: 'Organização verificada', passed: true },
        { label: 'Evento sincronizado com a bilheteira', passed: true },
        {
          label: 'Email da organização igual ao do promotor na bilheteira',
          passed: false,
        },
      ],
      document: { name: 'contrato-acolhimento.pdf', size: '2,4 MB', pages: 5 },
      status: 'pending',
    },
    {
      id: 'festival-norte',
      organization: 'Festival Norte',
      kind: 'registration',
      submittedAt: ago(4 * 24 * 60),
      submittedBy: 'Rui Matos',
      nipc: '509112233',
      email: 'rui@festivalnorte.pt',
      website: 'festivalnorte.pt',
      checks: [
        { label: 'Formato do NIPC válido', passed: true },
        { label: 'Domínio do email igual ao do site', passed: true },
        { label: 'Documento carregado e legível', passed: true },
      ],
      document: { name: 'certidao-permanente.pdf', size: '1,1 MB', pages: 3 },
      status: 'approved',
    },
  ]
}

export function isOpen(r: VerificationRequest): boolean {
  return r.status === 'pending' || r.status === 'info-requested'
}

/** Applies an administrator's decision. A note is required to request more
 * information or to reject, so the organization knows what to do. */
export function decide(
  request: VerificationRequest,
  decision: Decision,
  note: string,
): VerificationRequest | { error: string } {
  const trimmed = note.trim()
  if (decision !== 'approve' && !trimmed) {
    return { error: 'Escreve uma nota para a organização.' }
  }
  const status: VerificationStatus = {
    approve: 'approved',
    'request-info': 'info-requested',
    reject: 'rejected',
  }[decision] as VerificationStatus
  return { ...request, status, note: trimmed || undefined }
}
