import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'
import { AdminApiError, type AdminApi } from './data/adminApi'
import type { Session, SignIn, SignInOutcome } from './data/auth'
import {
  demoMembers,
  lastAccessLabel,
  memberStatusTag,
  temporaryPassword,
  validateMember,
  validateNewPassword,
  validateOrganizationName,
  demoOrganizations,
} from './data/admin'

const now = new Date(2026, 10, 7, 22, 0)

function session(role: Session['role']): Session {
  return {
    accessToken: 'jwt',
    expiresAt: new Date(2026, 10, 7, 23, 0),
    userId: 'u-1',
    role,
    organizationId: role === 'ADMINISTRATOR' ? null : 'organizacao-margens',
  }
}

/** Core's sign-in, without a network: answers `outcome` and records the call. */
function fakeSignIn(outcome: SignInOutcome) {
  const calls: [string, string][] = []
  const signIn: SignIn = (email, password) => {
    calls.push([email, password])
    return Promise.resolve(outcome)
  }
  return { signIn, calls }
}

/** Core's administration, without a network: the demo organizations and
 * members, changed in memory. Emails in `taken` already have an account. */
function fakeAdminApi(taken: string[] = []): AdminApi {
  const organizations = demoOrganizations()
  const members = demoMembers(now)
  const week = new Date(now.getTime() + 7 * 24 * 3600000)
  return {
    list: () =>
      Promise.resolve({
        organizations: [...organizations],
        members: members.map((m) => ({ ...m })),
      }),
    createOrganization: (name) => {
      const id = `org-${organizations.length + 1}`
      organizations.push({
        id,
        name,
        createdAt: '2026-11-07',
        events: 0,
        gradient: ['#000', '#111'],
      })
      return Promise.resolve(id)
    },
    addMember: (organizationId, name, email) => {
      if (taken.includes(email))
        return Promise.reject(new AdminApiError('EMAIL_ALREADY_REGISTERED'))
      const userId = `member-${members.length + 1}`
      members.push({
        id: userId,
        organizationId,
        name,
        email,
        status: 'temporary',
        temporaryExpiresAt: week,
      })
      return Promise.resolve({ userId, temporaryPassword: 'T7q-mK4-v9Px', expiresAt: week })
    },
    changeMember: (userId, change) => {
      const member = members.find((m) => m.id === userId)!
      if (change.resetPassword) {
        member.status = 'temporary'
        member.temporaryExpiresAt = week
      }
      if (change.active === false) member.status = 'disabled'
      if (change.active === true) member.status = member.temporaryExpiresAt ? 'temporary' : 'active'
      return Promise.resolve({
        active: member.status !== 'disabled',
        temporaryPassword: change.resetPassword ? 'Hc3-pW8-z2Rt' : undefined,
        expiresAt: change.resetPassword ? week : undefined,
      })
    },
  }
}

function renderAt(
  path: string | { pathname: string; state: unknown },
  signIn: SignIn = fakeSignIn({ kind: 'error', title: 'Erro', message: 'Sem rede.' }).signIn,
  options: { session?: Session; adminApi?: AdminApi } = {},
) {
  const api = options.adminApi ?? fakeAdminApi()
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App now={now} signIn={signIn} initialSession={options.session} adminApiFor={() => api} />
    </MemoryRouter>,
  )
}

function renderAsAdministrator(path: string, adminApi?: AdminApi) {
  return renderAt(path, undefined, { session: session('ADMINISTRATOR'), adminApi })
}

describe('admin helpers', () => {
  const members = demoMembers(now)

  it('labels member states and last access', () => {
    const [miguel, rita, joao] = members
    expect(memberStatusTag(miguel, now).label).toBe('Ativo')
    expect(memberStatusTag(rita, now).label).toBe('Temporária · expira em 6 dias')
    expect(memberStatusTag(joao, now).label).toBe('Desativado')
    expect(memberStatusTag(members[5], now).label).toBe('Temporária · expirada')
    const soon = {
      ...rita,
      temporaryExpiresAt: new Date(now.getTime() + 7 * 24 * 3600000 + 120000),
    }
    expect(memberStatusTag(soon, now).label).toBe('Temporária · expira em 7 dias')
    const hours = { ...rita, temporaryExpiresAt: new Date(now.getTime() + 3 * 3600000) }
    expect(memberStatusTag(hours, now).label).toBe('Temporária · expira em 1 dia')
    expect(lastAccessLabel(miguel.lastAccess, now)).toBe('hoje, 21:40')
    expect(lastAccessLabel(members[3].lastAccess, now)).toBe('ontem, 17:12')
    expect(lastAccessLabel(joao.lastAccess, now)).toBe('3 OUT')
    expect(lastAccessLabel(undefined, now)).toBe('—')
  })

  it('generates temporary passwords and validates forms', () => {
    expect(temporaryPassword()).toMatch(/^[A-Za-z2-9]{3}-[A-Za-z2-9]{3}-[A-Za-z2-9]{4}$/)
    expect(validateOrganizationName(' ', demoOrganizations())).toBe(
      'Escreve o nome da organização.',
    )
    expect(validateOrganizationName('organização margens', demoOrganizations())).toBe(
      'Já existe uma organização com este nome.',
    )
    expect(validateMember({ name: '', email: 'x' }, members)).toEqual({
      name: 'Escreve o nome do membro.',
      email: 'Escreve um email válido.',
    })
    expect(validateMember({ name: 'M', email: 'MIGUEL.sousa@margens.pt' }, members).email).toBe(
      'Já existe um membro com este email.',
    )
    expect(validateNewPassword('curta', 'curta').password).toBe('Usa pelo menos 12 caracteres.')
    expect(validateNewPassword('T7q-mK4-v9Px-1', 'x', 'T7q-mK4-v9Px-1').password).toBe(
      'Escolhe uma palavra-passe diferente da temporária.',
    )
    expect(validateNewPassword('margens-inverno', 'margens-outono').confirmation).toBe(
      'As palavras-passe não coincidem.',
    )
  })
})

describe('C1 and C4 sign-in', () => {
  it('opens the sign-in page with the organizer card', () => {
    renderAt('/')
    expect(screen.getByRole('heading', { name: 'Entrar no Crowded' })).toBeInTheDocument()
    expect(screen.getAllByText(/Organizas eventos?/)).toHaveLength(2)
    expect(screen.getByRole('link', { name: 'Contactar a equipa Crowded' })).toHaveAttribute(
      'href',
      'mailto:equipa@crowded.test',
    )
  })

  it("shows Core's refusal and how to get a new password", async () => {
    const user = userEvent.setup()
    const core = fakeSignIn({
      kind: 'error',
      title: 'Não foi possível entrar',
      message: 'O email ou a palavra-passe não estão certos.',
    })
    renderAt('/entrar', core.signIn)
    await user.type(screen.getByLabelText('Email'), 'equipa@ribeira-live.test')
    await user.type(screen.getByLabelText('Palavra-passe'), 'errada')
    await user.click(screen.getByRole('button', { name: 'Mostrar palavra-passe' }))
    expect(screen.getByLabelText('Palavra-passe')).toHaveAttribute('type', 'text')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O email ou a palavra-passe não estão certos.',
    )
    expect(core.calls).toEqual([['equipa@ribeira-live.test', 'errada']])
    expect(screen.getByLabelText('Palavra-passe')).toHaveAttribute('aria-invalid', 'true')
    await user.click(screen.getByRole('button', { name: 'Esqueci-me da palavra-passe' }))
    expect(screen.getByText(/Pede uma nova palavra-passe temporária/)).toBeInTheDocument()
  })

  it('sends a member to the events', async () => {
    const user = userEvent.setup()
    renderAt(
      '/entrar',
      fakeSignIn({ kind: 'signed-in', session: session('ORGANIZATION_MEMBER') }).signIn,
    )
    await user.type(screen.getByLabelText('Email'), 'equipa@ribeira-live.test')
    await user.type(screen.getByLabelText('Palavra-passe'), 'crowded-demo')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('heading', { name: 'Eventos', level: 2 })).toBeInTheDocument()
  })

  it('sends the administrator to the organizations', async () => {
    const user = userEvent.setup()
    renderAt('/entrar', fakeSignIn({ kind: 'signed-in', session: session('ADMINISTRATOR') }).signIn)
    await user.type(screen.getByLabelText('Email'), 'admin@crowded.test')
    await user.type(screen.getByLabelText('Palavra-passe'), 'crowded-demo')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('heading', { name: 'Organizações' })).toBeInTheDocument()
  })

  it('says when a member still has the temporary password', async () => {
    const user = userEvent.setup()
    renderAt('/entrar', fakeSignIn({ kind: 'new-password' }).signIn)
    await user.type(screen.getByLabelText('Email'), 'novo@ribeira-live.test')
    await user.type(screen.getByLabelText('Palavra-passe'), 'crowded-demo')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Primeiro acesso')
  })

  it('makes a member with a temporary password choose a new one', async () => {
    const user = userEvent.setup()
    renderAt({ pathname: '/definir-palavra-passe', state: { memberId: 'rita-ferreira' } })

    expect(
      screen.getByRole('heading', { name: 'Define a tua nova palavra-passe' }),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar e continuar' }))
    expect(screen.getByText('Usa pelo menos 12 caracteres.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Nova palavra-passe'), 'margens-inverno-26')
    await user.type(screen.getByLabelText('Confirmar palavra-passe'), 'margens-inverno-26')
    await user.click(screen.getByRole('button', { name: 'Guardar e continuar' }))
    expect(screen.getByRole('heading', { name: 'Eventos', level: 2 })).toBeInTheDocument()
  })

  it('does not show the new password page without a temporary sign-in', () => {
    renderAt('/definir-palavra-passe')
    expect(screen.getByRole('heading', { name: 'Entrar no Crowded' })).toBeInTheDocument()
  })
})

describe('A2 platform administration', () => {
  it('lists organizations and the members of the selected one', async () => {
    const user = userEvent.setup()
    renderAsAdministrator('/organizacoes')
    expect(await screen.findByText('3 organizações · 9 membros')).toBeInTheDocument()
    expect(screen.getByText('12 SET 2026')).toBeInTheDocument()
    const margens = screen.getByRole('table', { name: 'Membros de Organização Margens' })
    expect(within(margens).getByText('Temporária · expira em 6 dias')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Teatro Municipal do Porto/ }))
    const teatro = screen.getByRole('table', { name: 'Membros de Teatro Municipal do Porto' })
    expect(within(teatro).getAllByRole('row')).toHaveLength(5)
  })

  it('creates an organization and a member, showing the password once', async () => {
    const user = userEvent.setup()
    renderAsAdministrator('/organizacoes')
    await screen.findByText('3 organizações · 9 membros')

    await user.click(screen.getByRole('button', { name: 'Criar organização' }))
    let dialog = screen.getByRole('dialog', { name: 'Criar organização' })
    await user.click(within(dialog).getByRole('button', { name: 'Criar organização' }))
    expect(within(dialog).getByText('Escreve o nome da organização.')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('Nome da organização'), 'Festival Paredes')
    await user.click(within(dialog).getByRole('button', { name: 'Criar organização' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Festival Paredes criada.')
    expect(screen.getByText('Esta organização ainda não tem membros.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Criar membro' }))
    dialog = screen.getByRole('dialog', { name: 'Criar membro' })
    await user.type(within(dialog).getByLabelText('Nome'), 'Inês Costa')
    await user.type(within(dialog).getByLabelText('Email'), 'ines.costa@paredes.pt')
    await user.click(within(dialog).getByRole('button', { name: 'Criar membro' }))

    dialog = await screen.findByRole('dialog', { name: 'Membro criado' })
    expect(
      within(dialog).getByText('Inês Costa já é membro de Festival Paredes.'),
    ).toBeInTheDocument()
    expect(within(dialog).getByText(/Expira a 14 NOV, daqui a 7 dias/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Copiar' }))
    expect(within(dialog).getByRole('button', { name: 'Copiada' })).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Concluído' }))
    expect(screen.getByText('Temporária · expira em 7 dias')).toBeInTheDocument()
    expect(screen.getByText('4 organizações · 10 membros')).toBeInTheDocument()
  })

  it('issues a new temporary password, deactivates and reactivates members', async () => {
    const user = userEvent.setup()
    renderAsAdministrator('/organizacoes')

    await user.click(
      await screen.findByRole('button', { name: 'Nova palavra-passe para Miguel Sousa' }),
    )
    const dialog = await screen.findByRole('dialog', { name: 'Nova palavra-passe temporária' })
    expect(within(dialog).getByLabelText('Palavra-passe temporária')).toHaveTextContent(
      'Hc3-pW8-z2Rt',
    )
    expect(within(dialog).getByText(/A anterior deixou de funcionar/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Concluído' }))
    expect(screen.getAllByText('Temporária · expira em 7 dias')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Desativar Rita Ferreira' }))
    const confirm = screen.getByRole('dialog', { name: 'Desativar Rita Ferreira?' })
    await user.click(within(confirm).getByRole('button', { name: 'Desativar' }))
    expect(await screen.findByText('Conta de Rita Ferreira desativada.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Reativar João Pinto' }))
    expect(await screen.findByText('Conta de João Pinto reativada.')).toBeInTheDocument()
  })

  it("shows Core's refusal in the member form", async () => {
    const user = userEvent.setup()
    renderAsAdministrator('/organizacoes', fakeAdminApi(['ja.existe@margens.pt']))

    await user.click(await screen.findByRole('button', { name: 'Criar membro' }))
    const dialog = screen.getByRole('dialog', { name: 'Criar membro' })
    await user.type(within(dialog).getByLabelText('Nome'), 'Outra Pessoa')
    await user.type(within(dialog).getByLabelText('Email'), 'ja.existe@margens.pt')
    await user.click(within(dialog).getByRole('button', { name: 'Criar membro' }))

    expect(
      await within(dialog).findByText('Já existe uma conta com este email.'),
    ).toBeInTheDocument()
  })

  it('says when the organizations cannot be loaded', async () => {
    const failing: AdminApi = {
      ...fakeAdminApi(),
      list: () => Promise.reject(new AdminApiError('NETWORK')),
    }
    renderAsAdministrator('/organizacoes', failing)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar as organizações.',
    )
  })

  it('sends anyone but the administrator to the sign-in page', () => {
    renderAt('/organizacoes', undefined, { session: session('ORGANIZATION_MEMBER') })
    expect(screen.getByRole('heading', { name: 'Entrar no Crowded' })).toBeInTheDocument()
  })

  it('shows a placeholder for sections not built yet', () => {
    renderAsAdministrator('/auditoria')
    expect(screen.getByText('Disponível em breve.')).toBeInTheDocument()
  })
})
