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

const wrongCredentials = 'O email ou a palavra-passe não estão certos.'

function error(message: string, title = 'Não foi possível entrar'): SignInOutcome {
  return { kind: 'error', title, message }
}

/** Signs in with Core (`POST /auth/login`). Only organization members and
 * administrators use the backoffice. */
export async function signInWithCore(
  email: string,
  password: string,
  fetchImpl: typeof fetch = fetch,
  baseUrl: string = apiBaseUrl,
): Promise<SignInOutcome> {
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim()) || !password) {
    return error('Escreve o email e a palavra-passe.')
  }
  let response: Response
  try {
    response = await fetchImpl(`${baseUrl.replace(/\/$/, '')}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.trim(), password }),
    })
  } catch {
    return error('Não foi possível ligar ao Crowded. Verifica a ligação e tenta novamente.')
  }
  const body: unknown = await response.json().catch(() => undefined)
  const field = (name: string) =>
    typeof body === 'object' && body !== null ? (body as Record<string, unknown>)[name] : undefined

  if (response.ok) {
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
  switch (field('code')) {
    case 'INVALID_CREDENTIALS':
      return error(wrongCredentials)
    case 'PASSWORD_CHANGE_REQUIRED':
      return { kind: 'new-password' }
    case 'RATE_LIMITED':
      return error('Demasiadas tentativas. Tenta novamente daqui a alguns minutos.')
    default:
      return error('Não foi possível entrar agora. Tenta novamente.')
  }
}
