/** Base address of the Crowded API: the router serves it next to the
 * backoffice; the dev server proxies it to a local Core (vite.config.ts). */
export const apiBaseUrl: string = import.meta.env.VITE_API_BASE_URL ?? '/api'

/** A signed-in session: Core's short-lived token and who it is for. The
 * token stays in memory, so reloading the page signs the user out. */
export interface Session {
  accessToken: string
  expiresAt: Date
  userId: string
  role: 'ADMINISTRATOR' | 'ORGANIZATION_MEMBER'
  organizationId: string | null
}

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
      body: JSON.stringify(body),
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
  const user = field('user') as { id: string; role: string; organizationId: string | null }
  if (user.role !== 'ADMINISTRATOR' && user.role !== 'ORGANIZATION_MEMBER') {
    return error('Esta conta é de participante. Usa a app Crowded no telemóvel.')
  }
  return {
    kind: 'signed-in',
    session: {
      accessToken: field('accessToken') as string,
      expiresAt: new Date(field('expiresAt') as string),
      userId: user.id,
      role: user.role,
      organizationId: user.organizationId,
    },
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
