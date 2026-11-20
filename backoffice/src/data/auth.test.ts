import { describe, expect, it } from 'vitest'
import {
  choosePasswordWithCore,
  homeOf,
  httpAuthApi,
  sessionFetch,
  signInWithCore,
  type AuthApi,
} from './auth'

function answer(status: number, body: unknown): typeof fetch {
  return () =>
    Promise.resolve(
      new Response(typeof body === 'string' ? body : JSON.stringify(body), { status }),
    )
}

// With cookies, Core leaves the tokens out of the answer (ADR 0021).
const signedIn = {
  expiresAt: '2026-11-11T11:00:00Z',
  mustChangePassword: false,
  user: { id: 'u-1', role: 'ORGANIZATION_MEMBER', organizationId: 'org-1' },
}

describe('sign-in with Core', () => {
  it('posts the credentials and reads the session', async () => {
    let url = ''
    let init: RequestInit | undefined
    const fetchImpl: typeof fetch = (input, options) => {
      url = String(input)
      init = options
      return answer(200, signedIn)(input, options)
    }

    const outcome = await signInWithCore(' equipa@ribeira-live.test ', 'x', fetchImpl, '/api/')

    expect(url).toBe('/api/auth/login')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      email: 'equipa@ribeira-live.test',
      password: 'x',
      delivery: 'COOKIE',
    })
    expect(outcome).toEqual({
      kind: 'signed-in',
      session: {
        userId: 'u-1',
        role: 'ORGANIZATION_MEMBER',
        organizationId: 'org-1',
      },
    })
  })

  it('refuses a participant account and ends the session Core started', async () => {
    const participant = { ...signedIn, user: { ...signedIn.user, role: 'PARTICIPANT' } }
    const urls: string[] = []
    const fetchImpl: typeof fetch = (input, init) => {
      urls.push(String(input))
      return answer(200, participant)(input, init)
    }
    expect(await signInWithCore('ana@crowded.test', 'x', fetchImpl, '/api')).toMatchObject({
      kind: 'error',
      message: 'Esta conta é de participante. Usa a app Crowded no telemóvel.',
    })
    expect(urls).toEqual(['/api/auth/login', '/api/auth/logout'])
  })

  it('turns Core refusals into messages', async () => {
    const refusal = (code: string, status: number) =>
      signInWithCore('a@b.pt', 'x', answer(status, { status, code }))

    expect(await refusal('INVALID_CREDENTIALS', 401)).toMatchObject({
      message: 'O email ou a palavra-passe não estão certos.',
    })
    expect(await refusal('PASSWORD_CHANGE_REQUIRED', 403)).toEqual({ kind: 'new-password' })
    expect(await refusal('TEMPORARY_PASSWORD_EXPIRED', 403)).toMatchObject({
      title: 'A palavra-passe temporária expirou',
      message: 'Era válida durante 7 dias. Pede uma nova ao administrador do Crowded.',
    })
    expect(await refusal('RATE_LIMITED', 429)).toMatchObject({
      message: 'Demasiadas tentativas. Tenta novamente daqui a alguns minutos.',
    })
    expect(await signInWithCore('a@b.pt', 'x', answer(502, '<html></html>'))).toMatchObject({
      message: 'Não foi possível entrar agora. Tenta novamente.',
    })
  })

  it('checks the form and the connection', async () => {
    expect(await signInWithCore('sem-email', 'x', answer(200, signedIn))).toMatchObject({
      message: 'Escreve o email e a palavra-passe.',
    })
    expect(await signInWithCore('a@b.pt', '', answer(200, signedIn))).toMatchObject({
      kind: 'error',
    })
    const offline: typeof fetch = () => Promise.reject(new TypeError('Failed to fetch'))
    expect(await signInWithCore('a@b.pt', 'x', offline)).toMatchObject({
      message: 'Não foi possível ligar ao Crowded. Verifica a ligação e tenta novamente.',
    })
  })
})

describe('choosing the first password with Core', () => {
  it('posts the temporary and the new password and reads the session', async () => {
    let url = ''
    let init: RequestInit | undefined
    const fetchImpl: typeof fetch = (input, options) => {
      url = String(input)
      init = options
      return answer(200, signedIn)(input, options)
    }

    const outcome = await choosePasswordWithCore(
      ' novo@ribeira-live.test ', // ggignore
      'T7q-mK4-v9Px', // ggignore
      'Margens-1nverno', // ggignore
      fetchImpl,
      '/api',
    )

    expect(url).toBe('/api/auth/password')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      email: 'novo@ribeira-live.test',
      temporaryPassword: 'T7q-mK4-v9Px', // ggignore
      newPassword: 'Margens-1nverno', // ggignore
      delivery: 'COOKIE',
    })
    expect(outcome).toMatchObject({ kind: 'signed-in', session: { userId: 'u-1' } })
  })

  it('turns Core refusals into messages', async () => {
    const refusal = async (code: string, status: number) => {
      const outcome = await choosePasswordWithCore(
        'a@b.pt',
        't',
        'n',
        answer(status, { status, code }),
      )
      return outcome.kind === 'error' ? outcome.message : outcome.kind
    }

    expect(await refusal('INVALID_CREDENTIALS', 401)).toBe(
      'A palavra-passe temporária já não é válida. Volta a entrar.',
    )
    expect(await refusal('NO_TEMPORARY_PASSWORD', 403)).toBe(
      'A palavra-passe temporária já não é válida. Volta a entrar.',
    )
    expect(await refusal('TEMPORARY_PASSWORD_EXPIRED', 403)).toMatch(/Era válida durante 7 dias/)
    expect(await refusal('PASSWORD_TOO_WEAK', 400)).toMatch(/requisitos de segurança/)
    expect(await refusal('PASSWORD_TOO_SHORT', 400)).toMatch(/requisitos de segurança/)
    expect(await refusal('PASSWORD_TOO_LONG', 400)).toBe(
      'A palavra-passe é demasiado longa. Escolhe uma mais curta.',
    )
    expect(await refusal('PASSWORD_UNCHANGED', 400)).toBe(
      'Escolhe uma palavra-passe diferente da temporária.',
    )
    expect(await refusal('RATE_LIMITED', 429)).toBe(
      'Demasiadas tentativas. Tenta novamente daqui a alguns minutos.',
    )
    expect(await refusal('OTHER', 500)).toBe(
      'Não foi possível guardar a palavra-passe agora. Tenta novamente.',
    )
  })
})

describe('the session Core keeps in cookies', () => {
  it('asks Core who is signed in', async () => {
    const me = {
      id: 'u-1',
      email: 'a@b.pt',
      name: 'Ana',
      role: 'ADMINISTRATOR',
      organizationId: null,
    }
    let url = ''
    const fetchImpl: typeof fetch = (input) => {
      url = String(input)
      return answer(200, me)(input)
    }
    expect(await httpAuthApi(fetchImpl, '/api/').currentSession()).toEqual({
      userId: 'u-1',
      role: 'ADMINISTRATOR',
      organizationId: null,
    })
    expect(url).toBe('/api/me')
    expect(await httpAuthApi(answer(401, '')).currentSession()).toBeUndefined()
    expect(
      await httpAuthApi(answer(200, { id: 'u-1', role: 'OTHER' })).currentSession(),
    ).toBeUndefined()
    const offline: typeof fetch = () => Promise.reject(new TypeError('offline'))
    expect(await httpAuthApi(offline).currentSession()).toBeUndefined()
  })

  it('refreshes the session and tells a concurrent refresh apart', async () => {
    expect(await httpAuthApi(answer(200, signedIn)).refresh()).toBe('refreshed')
    expect(await httpAuthApi(answer(401, { code: 'REFRESH_TOKEN_ROTATED' })).refresh()).toBe(
      'rotated',
    )
    expect(await httpAuthApi(answer(401, { code: 'INVALID_REFRESH_TOKEN' })).refresh()).toBe(
      'ended',
    )
    expect(await httpAuthApi(answer(403, '')).refresh()).toBe('ended')
    const offline: typeof fetch = () => Promise.reject(new TypeError('offline'))
    expect(await httpAuthApi(offline).refresh()).toBe('ended')
  })

  it('signs out with Core, even when it cannot be reached', async () => {
    const calls: [string, RequestInit | undefined][] = []
    const fetchImpl: typeof fetch = (input, init) => {
      calls.push([String(input), init])
      return answer(200, '')(input, init)
    }
    await httpAuthApi(fetchImpl, '/api').signOut()
    expect(calls).toEqual([['/api/auth/logout', { method: 'POST' }]])
    const offline: typeof fetch = () => Promise.reject(new TypeError('offline'))
    await expect(httpAuthApi(offline).signOut()).resolves.toBeUndefined()
  })

  it('starts each role in its area', () => {
    expect(homeOf('ADMINISTRATOR')).toBe('/organizations')
    expect(homeOf('ORGANIZATION_MEMBER')).toBe('/organization/events')
  })
})

describe('requests that keep the session going', () => {
  function auth(outcome: Awaited<ReturnType<AuthApi['refresh']>>) {
    let refreshes = 0
    const api: AuthApi = {
      currentSession: () => Promise.resolve(undefined),
      refresh: () => {
        refreshes++
        return Promise.resolve(outcome)
      },
      signOut: () => Promise.resolve(),
    }
    return { api, refreshes: () => refreshes }
  }

  /** Answers each call with the next status. */
  function statuses(...answers: number[]) {
    let call = 0
    const fetchImpl: typeof fetch = () =>
      Promise.resolve(new Response(null, { status: answers[Math.min(call++, answers.length - 1)] }))
    return { fetchImpl, calls: () => call }
  }

  it('repeats a request once the expired session is refreshed', async () => {
    const refreshed = auth('refreshed')
    const core = statuses(401, 200)
    let ended = false
    const response = await sessionFetch(refreshed.api, () => (ended = true), core.fetchImpl)('/x')
    expect(response.status).toBe(200)
    expect(refreshed.refreshes()).toBe(1)
    expect(core.calls()).toBe(2)
    expect(ended).toBe(false)
  })

  it('refreshes once for every request waiting at the same time', async () => {
    const rotated = auth('rotated')
    const core = statuses(401, 401, 200, 200)
    const send = sessionFetch(rotated.api, () => undefined, core.fetchImpl)
    const answers = await Promise.all([send('/a'), send('/b')])
    expect(answers.map((r) => r.status)).toEqual([200, 200])
    expect(rotated.refreshes()).toBe(1)
  })

  it('signs out when the session has ended', async () => {
    const endedAuth = auth('ended')
    let ended = 0
    const send = sessionFetch(endedAuth.api, () => ended++, statuses(401).fetchImpl)
    expect((await send('/x')).status).toBe(401)
    expect(ended).toBe(1)

    const stillRefused = sessionFetch(auth('refreshed').api, () => ended++, statuses(401).fetchImpl)
    expect((await stillRefused('/x')).status).toBe(401)
    expect(ended).toBe(2)
  })

  it('leaves other answers alone', async () => {
    const refreshed = auth('refreshed')
    const response = await sessionFetch(
      refreshed.api,
      () => undefined,
      statuses(403).fetchImpl,
    )('/x')
    expect(response.status).toBe(403)
    expect(refreshed.refreshes()).toBe(0)
  })
})
