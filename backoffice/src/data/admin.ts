/** Platform administration (A2) and sign-in (C1, C4), backed by demo data
 * until the Core API exists. Administrators create the organizations and their
 * members; a member gets a temporary password, valid for 7 days, and chooses a
 * new one at the first sign-in (FR-087, FR-088). */

import festivalImage from '../assets/festival-margens-2026.jpg'

const DAY = 24 * 3600000

/** How long a temporary password works. */
export const TEMPORARY_PASSWORD_DAYS = 7
export const MIN_PASSWORD_LENGTH = 12

export const admin = {
  name: 'Ana Lopes',
  initials: 'AL',
  role: 'Administradora',
  email: 'ana.lopes@crowded.test',
}

/** Address of "Contactar a equipa Crowded" on the sign-in page. */
export const contactEmail = 'equipa@crowded.test'

export interface AdminOrganization {
  id: string
  name: string
  /** yyyy-mm-dd */
  createdAt: string
  events: number
  image?: string
  gradient: [string, string]
}

export type MemberStatus = 'active' | 'temporary' | 'disabled'

export interface Member {
  id: string
  organizationId: string
  name: string
  email: string
  status: MemberStatus
  lastAccess?: Date
  /** Set while the member still has to choose a password. */
  temporaryPassword?: string
  temporaryExpiresAt?: Date
}

export function demoOrganizations(): AdminOrganization[] {
  return [
    {
      id: 'organizacao-margens',
      name: 'Organização Margens',
      createdAt: '2026-09-12',
      events: 4,
      image: festivalImage,
      gradient: ['#596b9e', '#1a1f38'],
    },
    {
      id: 'teatro-municipal-porto',
      name: 'Teatro Municipal do Porto',
      createdAt: '2026-09-20',
      events: 2,
      gradient: ['rgb(51,140,128)', 'rgb(13,46,51)'],
    },
    {
      id: 'camara-municipal-porto',
      name: 'Câmara Municipal do Porto',
      createdAt: '2026-10-02',
      events: 1,
      gradient: ['rgb(64,51,128)', 'rgb(13,13,38)'],
    },
  ]
}

export function demoMembers(now: Date): Member[] {
  const at = (days: number, hours = 0, minutes = 0) =>
    new Date(now.getFullYear(), now.getMonth(), now.getDate() + days, hours, minutes)
  const inDays = (days: number) => new Date(now.getTime() + days * DAY)
  return [
    {
      id: 'miguel-sousa',
      organizationId: 'organizacao-margens',
      name: 'Miguel Sousa',
      email: 'miguel.sousa@margens.pt',
      status: 'active',
      lastAccess: at(0, 21, 40),
    },
    {
      id: 'rita-ferreira',
      organizationId: 'organizacao-margens',
      name: 'Rita Ferreira',
      email: 'rita.ferreira@margens.pt',
      status: 'temporary',
      temporaryPassword: 'T7q-mK4-v9Px',
      temporaryExpiresAt: inDays(6),
    },
    {
      id: 'joao-pinto',
      organizationId: 'organizacao-margens',
      name: 'João Pinto',
      email: 'joao.pinto@margens.pt',
      status: 'disabled',
      lastAccess: new Date(now.getFullYear(), 9, 3, 18, 5),
    },
    {
      id: 'ines-carvalho',
      organizationId: 'teatro-municipal-porto',
      name: 'Inês Carvalho',
      email: 'ines.carvalho@teatromunicipal.pt',
      status: 'active',
      lastAccess: at(-1, 17, 12),
    },
    {
      id: 'pedro-alves',
      organizationId: 'teatro-municipal-porto',
      name: 'Pedro Alves',
      email: 'pedro.alves@teatromunicipal.pt',
      status: 'active',
      lastAccess: at(-3, 10, 30),
    },
    {
      id: 'sofia-martins',
      organizationId: 'teatro-municipal-porto',
      name: 'Sofia Martins',
      email: 'sofia.martins@teatromunicipal.pt',
      status: 'temporary',
      temporaryPassword: 'Hc3-pW8-z2Rt',
      temporaryExpiresAt: inDays(-2),
    },
    {
      id: 'tiago-rocha',
      organizationId: 'teatro-municipal-porto',
      name: 'Tiago Rocha',
      email: 'tiago.rocha@teatromunicipal.pt',
      status: 'active',
      lastAccess: at(-8, 9, 45),
    },
    {
      id: 'marta-lima',
      organizationId: 'camara-municipal-porto',
      name: 'Marta Lima',
      email: 'marta.lima@cm-porto.pt',
      status: 'active',
      lastAccess: at(-2, 11, 0),
    },
    {
      id: 'rui-costa',
      organizationId: 'camara-municipal-porto',
      name: 'Rui Costa',
      email: 'rui.costa@cm-porto.pt',
      status: 'active',
      lastAccess: at(-5, 15, 20),
    },
  ]
}

const months = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ']

/** "12 SET 2026". */
export function longDate(date: string): string {
  const [y, m, d] = date.split('-').map(Number)
  return `${d} ${months[m - 1]} ${y}`
}

/** "14 NOV". */
export function dayMonth(date: Date): string {
  return `${date.getDate()} ${months[date.getMonth()]}`
}

/** "hoje, 21:40", "ontem, 17:12" or "3 OUT". */
export function lastAccessLabel(date: Date | undefined, now: Date): string {
  if (!date) return '—'
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  if (date.toDateString() === now.toDateString()) return `hoje, ${time}`
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return `ontem, ${time}`
  return dayMonth(date)
}

export function isExpired(member: Member, now: Date): boolean {
  return (
    member.status === 'temporary' &&
    !!member.temporaryExpiresAt &&
    member.temporaryExpiresAt.getTime() <= now.getTime()
  )
}

/** Tag of the "Estado" column. */
export function memberStatusTag(
  member: Member,
  now: Date,
): { label: string; tone: 'success' | 'info' | 'neutral' | 'danger'; dot: boolean } {
  if (member.status === 'active') return { label: 'Ativo', tone: 'success', dot: true }
  if (member.status === 'disabled') return { label: 'Desativado', tone: 'neutral', dot: false }
  if (isExpired(member, now)) {
    return { label: 'Temporária · expirada', tone: 'danger', dot: false }
  }
  const days = Math.ceil(((member.temporaryExpiresAt?.getTime() ?? 0) - now.getTime()) / DAY)
  return {
    label: `Temporária · expira em ${days} ${days === 1 ? 'dia' : 'dias'}`,
    tone: 'info',
    dot: false,
  }
}

/** Letters and digits that are hard to confuse (no 0/O, 1/l/I). */
const alphabet = 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'

/** A temporary password such as "T7q-mK4-v9Px". */
export function temporaryPassword(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(10))
  const chars = Array.from(bytes, (b) => alphabet[b % alphabet.length])
  return `${chars.slice(0, 3).join('')}-${chars.slice(3, 6).join('')}-${chars.slice(6).join('')}`
}

/** Gives the member a new temporary password, valid for 7 days; the previous
 * one stops working. */
export function withTemporaryPassword(member: Member, now: Date, password: string): Member {
  return {
    ...member,
    status: 'temporary',
    temporaryPassword: password,
    temporaryExpiresAt: new Date(now.getTime() + TEMPORARY_PASSWORD_DAYS * DAY),
  }
}

export function isEmail(text: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text.trim())
}

export function validateOrganizationName(
  name: string,
  organizations: AdminOrganization[],
): string | undefined {
  const wanted = name.trim().toLowerCase()
  if (!wanted) return 'Escreve o nome da organização.'
  if (organizations.some((o) => o.name.toLowerCase() === wanted)) {
    return 'Já existe uma organização com este nome.'
  }
  return undefined
}

export function validateMember(
  form: { name: string; email: string },
  members: Member[],
): { name?: string; email?: string } {
  const errors: { name?: string; email?: string } = {}
  if (!form.name.trim()) errors.name = 'Escreve o nome do membro.'
  if (!isEmail(form.email)) errors.email = 'Escreve um email válido.'
  else if (members.some((m) => m.email.toLowerCase() === form.email.trim().toLowerCase())) {
    errors.email = 'Já existe um membro com este email.'
  }
  return errors
}

export type SignInResult =
  | { kind: 'admin' }
  | { kind: 'member'; member: Member }
  /** Temporary password: the member must choose a new one (C4). */
  | { kind: 'new-password'; member: Member }
  | { kind: 'expired' }
  | { kind: 'error'; message: string }

/** Sign-in on demo data. There is no identity provider yet: active members and
 * the administrator enter with any password; members with a temporary
 * password must type it. */
export function signIn(
  members: Member[],
  email: string,
  password: string,
  now: Date,
): SignInResult {
  if (!isEmail(email) || !password) {
    return { kind: 'error', message: 'Escreve o email e a palavra-passe.' }
  }
  const wanted = email.trim().toLowerCase()
  if (wanted === admin.email) return { kind: 'admin' }
  const member = members.find((m) => m.email.toLowerCase() === wanted)
  if (!member) return { kind: 'error', message: 'O email ou a palavra-passe não estão certos.' }
  if (member.status === 'disabled') {
    return {
      kind: 'error',
      message: 'Esta conta está desativada. Fala com o administrador do Crowded.',
    }
  }
  if (member.status === 'temporary') {
    if (password !== member.temporaryPassword) {
      return { kind: 'error', message: 'O email ou a palavra-passe não estão certos.' }
    }
    if (isExpired(member, now)) return { kind: 'expired' }
    return { kind: 'new-password', member }
  }
  return { kind: 'member', member }
}

export function validateNewPassword(
  password: string,
  confirmation: string,
  temporary?: string,
): { password?: string; confirmation?: string } {
  if (password.length < MIN_PASSWORD_LENGTH) {
    return { password: `Usa pelo menos ${MIN_PASSWORD_LENGTH} caracteres.` }
  }
  if (password === temporary) {
    return { password: 'Escolhe uma palavra-passe diferente da temporária.' }
  }
  if (password !== confirmation) return { confirmation: 'As palavras-passe não coincidem.' }
  return {}
}
