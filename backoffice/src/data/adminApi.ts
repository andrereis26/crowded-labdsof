import type { AdminOrganization, Member } from './admin'
import { apiBaseUrl } from './auth'

/** A change to a member (`PATCH /admin/members/{userId}`). */
export interface MemberChange {
  active?: boolean
  resetPassword?: boolean
}

/** A temporary password Core issued, shown once. */
export interface IssuedPassword {
  userId: string
  temporaryPassword: string
  expiresAt: Date
}

/** A refusal from Core, with its error code (API Outline). */
export class AdminApiError extends Error {
  readonly code: string

  constructor(code: string) {
    super(code)
    this.code = code
  }
}

/** Platform administration in Core (API Outline, Administration). */
export interface AdminApi {
  list(): Promise<{ organizations: AdminOrganization[]; members: Member[] }>
  createOrganization(name: string): Promise<string>
  addMember(organizationId: string, name: string, email: string): Promise<IssuedPassword>
  changeMember(
    userId: string,
    change: MemberChange,
  ): Promise<{ active: boolean; temporaryPassword?: string; expiresAt?: Date }>
}

interface OrganizationJson {
  id: string
  name: string
  createdAt: string
  eventCount: number
  members: {
    userId: string
    name: string | null
    email: string
    active: boolean
    mustChangePassword: boolean
    temporaryPasswordExpiresAt: string | null
    lastSignedInAt: string | null
  }[]
}

/** The thumbnails' colours: organizations have no picture yet. */
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

/** Core's organizations and members, in the shape A2 shows. */
export function fromJson(json: OrganizationJson[]): {
  organizations: AdminOrganization[]
  members: Member[]
} {
  return {
    organizations: json.map((o) => ({
      id: o.id,
      name: o.name,
      createdAt: o.createdAt.slice(0, 10),
      events: o.eventCount,
      gradient: gradientFor(o.id),
    })),
    members: json.flatMap((o) =>
      o.members.map((m): Member => ({
        id: m.userId,
        organizationId: o.id,
        name: m.name ?? m.email,
        email: m.email,
        status: !m.active ? 'disabled' : m.mustChangePassword ? 'temporary' : 'active',
        lastAccess: m.lastSignedInAt ? new Date(m.lastSignedInAt) : undefined,
        temporaryExpiresAt: m.temporaryPasswordExpiresAt
          ? new Date(m.temporaryPasswordExpiresAt)
          : undefined,
      })),
    ),
  }
}

/** {@link AdminApi} over HTTP, with the administrator's session (its cookies). */
export function httpAdminApi(
  fetchImpl: typeof fetch = fetch,
  baseUrl: string = apiBaseUrl,
): AdminApi {
  const base = baseUrl.replace(/\/$/, '')

  async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
    let response: Response
    try {
      response = await fetchImpl(`${base}${path}`, {
        method,
        headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
    } catch {
      throw new AdminApiError('NETWORK')
    }
    const json: unknown = await response.json().catch(() => undefined)
    if (!response.ok) {
      const code =
        typeof json === 'object' && json !== null && 'code' in json
          ? String((json as { code: unknown }).code)
          : `HTTP_${response.status}`
      throw new AdminApiError(code)
    }
    return json as T
  }

  return {
    async list() {
      return fromJson(await call<OrganizationJson[]>('GET', '/admin/organizations'))
    },
    async createOrganization(name) {
      const created = await call<{ organizationId: string }>('POST', '/admin/organizations', {
        name,
      })
      return created.organizationId
    },
    async addMember(organizationId, name, email) {
      const issued = await call<{ userId: string; temporaryPassword: string; expiresAt: string }>(
        'POST',
        `/admin/organizations/${organizationId}/members`,
        { name, email },
      )
      return { ...issued, expiresAt: new Date(issued.expiresAt) }
    },
    async changeMember(userId, change) {
      const changed = await call<{
        active: boolean
        temporaryPassword: string | null
        expiresAt: string | null
      }>('PATCH', `/admin/members/${userId}`, change)
      return {
        active: changed.active,
        temporaryPassword: changed.temporaryPassword ?? undefined,
        expiresAt: changed.expiresAt ? new Date(changed.expiresAt) : undefined,
      }
    },
  }
}

/** The administrator's message for a refusal from Core, in European Portuguese. */
export function adminErrorMessage(code: string): string {
  switch (code) {
    case 'ORGANIZATION_NAME_TAKEN':
      return 'Já existe uma organização com este nome.'
    case 'EMAIL_ALREADY_REGISTERED':
      return 'Já existe uma conta com este email.'
    case 'ADMINISTRATOR_ONLY':
      return 'Só o administrador do Crowded pode fazer isto.'
    case 'NETWORK':
      return 'Não foi possível ligar ao Crowded. Verifica a ligação e tenta novamente.'
    default:
      return 'Não foi possível guardar. Tenta novamente.'
  }
}
