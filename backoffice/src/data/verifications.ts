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

/** "420 KB", "1,2 MB". */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}

export interface RegistrationForm {
  organization: string
  nipc: string
  website: string
  responsible: string
  email: string
  document?: { name: string; size: number }
  confirmed: boolean
}

const domain = (value: string) =>
  value.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/^www\./, '').split(/[/?#]/)[0]

/** Checks an organization registration (O1) before it reaches A1. */
export function validateRegistration(
  form: RegistrationForm,
): Partial<Record<keyof RegistrationForm, string>> {
  const errors: Partial<Record<keyof RegistrationForm, string>> = {}
  if (!form.organization.trim()) errors.organization = 'Escreve o nome da organização.'
  if (!/^\d{9}$/.test(form.nipc.replace(/\s/g, ''))) errors.nipc = 'O NIPC tem 9 algarismos.'
  if (!/^[^\s.]+(\.[^\s.]+)+$/.test(domain(form.website))) errors.website = 'Indica o site da organização.'
  if (!form.responsible.trim()) errors.responsible = 'Indica quem é o responsável.'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = 'Indica um email válido.'
  if (!form.document) errors.document = 'Carrega um comprovativo da organização.'
  if (!form.confirmed) errors.confirmed = 'Confirma que representas esta organização.'
  return errors
}

/** A new registration request, with the automatic checks of A1. */
export function registrationRequest(form: RegistrationForm, now: Date): VerificationRequest {
  const nipc = form.nipc.replace(/\s/g, '')
  return {
    id: `registo-${now.getTime()}`,
    organization: form.organization.trim(),
    kind: 'registration',
    submittedAt: now,
    submittedBy: form.responsible.trim(),
    nipc,
    email: form.email.trim(),
    website: domain(form.website),
    checks: [
      { label: 'Formato do NIPC válido', passed: /^\d{9}$/.test(nipc) },
      {
        label: 'Domínio do email igual ao do site',
        passed: form.email.trim().toLowerCase().endsWith(`@${domain(form.website)}`),
      },
      { label: 'Documento carregado', passed: !!form.document },
    ],
    document: {
      name: form.document?.name ?? '—',
      size: form.document ? formatFileSize(form.document.size) : '—',
      pages: 1,
    },
    status: 'pending',
  }
}

/** A request from a verified organization to manage a synced event (O13). */
export function managementRequest(
  organization: { name: string; nipc: string; email: string; website: string; user: string },
  event: string,
  relation: string,
  document: { name: string; size: number },
  now: Date,
): VerificationRequest {
  return {
    id: `gestao-${now.getTime()}`,
    organization: organization.name,
    kind: 'management',
    event,
    submittedAt: now,
    submittedBy: organization.user,
    nipc: organization.nipc,
    email: organization.email,
    website: organization.website,
    checks: [
      { label: 'Organização verificada', passed: true },
      { label: 'Evento sincronizado com a bilheteira', passed: true },
      { label: `Relação declarada: ${relation}`, passed: true },
    ],
    document: { name: document.name, size: formatFileSize(document.size), pages: 1 },
    status: 'pending',
  }
}
