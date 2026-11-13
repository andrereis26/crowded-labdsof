import { describe, expect, it } from 'vitest'
import { choosePasswordWithCore, signInWithCore } from './auth'

function answer(status: number, body: unknown): typeof fetch {
  return () =>
    Promise.resolve(
      new Response(typeof body === 'string' ? body : JSON.stringify(body), { status }),
    )
}

const signedIn = {
  accessToken: 'jwt',
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
    })
    expect(outcome).toEqual({
      kind: 'signed-in',
      session: {
        accessToken: 'jwt',
        expiresAt: new Date('2026-11-11T11:00:00Z'),
        userId: 'u-1',
        role: 'ORGANIZATION_MEMBER',
        organizationId: 'org-1',
      },
    })
  })

  it('refuses a participant account', async () => {
    const participant = { ...signedIn, user: { ...signedIn.user, role: 'PARTICIPANT' } }
    expect(await signInWithCore('ana@crowded.test', 'x', answer(200, participant))).toMatchObject({
      kind: 'error',
      message: 'Esta conta é de participante. Usa a app Crowded no telemóvel.',
    })
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
    })
    expect(outcome).toMatchObject({ kind: 'signed-in', session: { accessToken: 'jwt' } })
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
