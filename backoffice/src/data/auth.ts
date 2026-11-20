import { config } from '../config'

/** Base address of the Crowded API: the router serves it next to the
 * backoffice; the dev server proxies it to a local Core (vite.config.ts). */
export const apiBaseUrl: string = config.apiBaseUrl

/** Who is signed in. Core keeps the session's tokens in cookies that page
 * scripts cannot read, so a reload keeps it (ADR 0021). */
export interface Session {
  userId: string
  role: 'ADMINISTRATOR' | 'ORGANIZATION_MEMBER' | 'PARTICIPANT'
  organizationId: string | null
}

/** The roles that use the backoffice. */
export const isBackofficeRole = (role: Session['role']) =>
  role === 'ADMINISTRATOR' || role === 'ORGANIZATION_MEMBER'

export type SignInOutcome =
  | { kind: 'signed-in'; session: Session }
  | { kind: 'new-password' }
  | { kind: 'error'; title: string; message: string }

export type SignIn = (email: string, password: string) => Promise<SignInOutcome>

/** Replaces a member's temporary password with the one they chose (C4). */
export type ChoosePassword = (
  email: string,
  temporaryPassword: string,
  newPassword: string,
) => Promise<SignInOutcome>

/** Shown when a participant's account reaches the backoffice. */
export const participantAccount = 'Esta conta é de participante. Usa a app Crowded no telemóvel.'

const wrongCredentials = 'O email ou a palavra-passe não estão certos.'
const expired = 'Era válida durante 7 dias. Pede uma nova ao administrador do Crowded.'
const weakPassword =
  'A palavra-passe não cumpre os requisitos de segurança: deve incluir uma letra maiúscula, uma letra minúscula, um algarismo e um símbolo.'

function error(message: string, title = 'Não foi possível entrar'): SignInOutcome {
  return { kind: 'error', title, message }
}

/** Posts `body` to Core and reads the session, or the refusal's code. */
async function post(
  path: string,
  body: unknown,
  fetchImpl: typeof fetch,
  baseUrl: string,
): Promise<SignInOutcome | { kind: 'refused'; code: unknown }> {
  let response: Response
  try {
    response = await fetchImpl(`${baseUrl.replace(/\/$/, '')}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...(body as object), delivery: 'COOKIE' }),
    })
  } catch {
    return error('Não foi possível ligar ao Crowded. Verifica a ligação e tenta novamente.')
  }
  const answer: unknown = await response.json().catch(() => undefined)
  const field = (name: string) =>
    typeof answer === 'object' && answer !== null
      ? (answer as Record<string, unknown>)[name]
      : undefined

  if (!response.ok) return { kind: 'refused', code: field('code') }
  const session = sessionOf(field('user'))
  if (!session || !isBackofficeRole(session.role)) {
    // Core already started the session in cookies: end it, so no participant
    // session is left behind by a refused sign-in.
    await fetchImpl(`${baseUrl.replace(/\/$/, '')}/auth/logout`, { method: 'POST' }).catch(
      () => undefined,
    )
    return error(participantAccount)
  }
  return { kind: 'signed-in', session }
}

/** The session in Core's `user` or `GET /me` answer, or nothing when there
 * is none. */
function sessionOf(user: unknown): Session | undefined {
  if (typeof user !== 'object' || user === null) return undefined
  const { id, role, organizationId } = user as Record<string, unknown>
  if (typeof id !== 'string') return undefined
  if (role !== 'ADMINISTRATOR' && role !== 'ORGANIZATION_MEMBER' && role !== 'PARTICIPANT') {
    return undefined
  }
  return {
    userId: id,
    role,
    organizationId: typeof organizationId === 'string' ? organizationId : null,
  }
}

const isEmail = (email: string) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())

/** Signs in with Core (`POST /auth/login`). Only organization members and
 * administrators use the backoffice. */
export async function signInWithCore(
  email: string,
  password: string,
  fetchImpl: typeof fetch = fetch,
  baseUrl: string = apiBaseUrl,
): Promise<SignInOutcome> {
  if (!isEmail(email) || !password) {
    return error('Escreve o email e a palavra-passe.')
  }
  const outcome = await post('/auth/login', { email: email.trim(), password }, fetchImpl, baseUrl)
  if (outcome.kind !== 'refused') return outcome
  switch (outcome.code) {
    case 'INVALID_CREDENTIALS':
      return error(wrongCredentials)
    case 'PASSWORD_CHANGE_REQUIRED':
      return { kind: 'new-password' }
    case 'TEMPORARY_PASSWORD_EXPIRED':
      return error(expired, 'A palavra-passe temporária expirou')
    case 'RATE_LIMITED':
      return error('Demasiadas tentativas. Tenta novamente daqui a alguns minutos.')
    default:
      return error('Não foi possível entrar agora. Tenta novamente.')
  }
}

/** Replaces the temporary password with Core (`POST /auth/password`), which
 * signs the member in. */
export async function choosePasswordWithCore(
  email: string,
  temporaryPassword: string,
  newPassword: string,
  fetchImpl: typeof fetch = fetch,
  baseUrl: string = apiBaseUrl,
): Promise<SignInOutcome> {
  const outcome = await post(
    '/auth/password',
    { email: email.trim(), temporaryPassword, newPassword },
    fetchImpl,
    baseUrl,
  )
  if (outcome.kind !== 'refused') return outcome
  const title = 'Não foi possível guardar'
  switch (outcome.code) {
    case 'INVALID_CREDENTIALS':
    case 'NO_TEMPORARY_PASSWORD':
      return error('A palavra-passe temporária já não é válida. Volta a entrar.', title)
    case 'TEMPORARY_PASSWORD_EXPIRED':
      return error(expired, 'A palavra-passe temporária expirou')
    case 'PASSWORD_TOO_SHORT':
    case 'PASSWORD_TOO_WEAK':
      return error(weakPassword, title)
    case 'PASSWORD_TOO_LONG':
      return error('A palavra-passe é demasiado longa. Escolhe uma mais curta.', title)
    case 'PASSWORD_UNCHANGED':
      return error('Escolhe uma palavra-passe diferente da temporária.', title)
    case 'RATE_LIMITED':
      return error('Demasiadas tentativas. Tenta novamente daqui a alguns minutos.', title)
    default:
      return error('Não foi possível guardar a palavra-passe agora. Tenta novamente.', title)
  }
}

/** The session Core keeps in cookies (ADR 0021): who is signed in, refreshing
 * it, and ending it. */
export interface AuthApi {
  /** Who is signed in (`GET /me`), or nothing when no one is. */
  currentSession(): Promise<Session | undefined>
  /** New tokens for the session (`POST /auth/refresh`): `rotated` when another
   * tab has just refreshed it, so the request only needs repeating. */
  refresh(): Promise<'refreshed' | 'rotated' | 'ended'>
  /** Ends the session and clears its cookies (`POST /auth/logout`). */
  signOut(): Promise<void>
}

/** {@link AuthApi} over HTTP. The browser sends the session's cookies, which
 * only Core can read or delete. */
export function httpAuthApi(
  fetchImpl: typeof fetch = fetch,
  baseUrl: string = apiBaseUrl,
): AuthApi {
  const base = baseUrl.replace(/\/$/, '')
  return {
    async currentSession() {
      let response: Response
      try {
        response = await fetchImpl(`${base}/me`)
      } catch {
        return undefined
      }
      if (!response.ok) return undefined
      return sessionOf(await response.json().catch(() => undefined))
    },
    async refresh() {
      let response: Response
      try {
        response = await fetchImpl(`${base}/auth/refresh`, { method: 'POST' })
      } catch {
        return 'ended'
      }
      if (response.ok) return 'refreshed'
      const answer: unknown = await response.json().catch(() => undefined)
      const code =
        typeof answer === 'object' && answer !== null && 'code' in answer
          ? (answer as { code: unknown }).code
          : undefined
      return code === 'REFRESH_TOKEN_ROTATED' ? 'rotated' : 'ended'
    },
    async signOut() {
      await fetchImpl(`${base}/auth/logout`, { method: 'POST' }).catch(() => undefined)
    },
  }
}

/** A `fetch` for Core's API that keeps the session going: when the access
 * token has expired (401), it refreshes the session once, shared by every
 * request waiting, and repeats the request. When the session has ended, it
 * calls `onEnded`. */
export function sessionFetch(
  auth: AuthApi,
  onEnded: () => void,
  fetchImpl: typeof fetch = fetch,
): typeof fetch {
  let refreshing: Promise<'refreshed' | 'rotated' | 'ended'> | undefined
  return async (input, init) => {
    const response = await fetchImpl(input, init)
    if (response.status !== 401) return response
    refreshing ??= auth.refresh().finally(() => (refreshing = undefined))
    if ((await refreshing) === 'ended') {
      onEnded()
      return response
    }
    const repeated = await fetchImpl(input, init)
    if (repeated.status === 401) onEnded()
    return repeated
  }
}

/** Where each role starts in the backoffice. */
export function homeOf(role: Session['role']): string {
  return role === 'ADMINISTRATOR' ? '/organizations' : '/organization/events'
}
