import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'
import {
  demoMembers,
  lastAccessLabel,
  memberStatusTag,
  signIn,
  temporaryPassword,
  validateMember,
  validateNewPassword,
  validateOrganizationName,
  demoOrganizations,
} from './data/admin'

const now = new Date(2026, 10, 7, 22, 0)

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App now={now} />
    </MemoryRouter>,
  )
}

describe('admin helpers', () => {
  const members = demoMembers(now)

  it('labels member states and last access', () => {
    const [miguel, rita, joao] = members
    expect(memberStatusTag(miguel, now).label).toBe('Ativo')
    expect(memberStatusTag(rita, now).label).toBe('Temporária · expira em 6 dias')
    expect(memberStatusTag(joao, now).label).toBe('Desativado')
    expect(memberStatusTag(members[5], now).label).toBe('Temporária · expirada')
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

  it('signs in members, the administrator and temporary passwords', () => {
    expect(signIn(members, 'ana.lopes@crowded.test', 'x', now)).toEqual({ kind: 'admin' })
    expect(signIn(members, 'miguel.sousa@margens.pt', 'x', now).kind).toBe('member')
    expect(signIn(members, 'rita.ferreira@margens.pt', 'T7q-mK4-v9Px', now).kind).toBe(
      'new-password',
    )
    expect(signIn(members, 'rita.ferreira@margens.pt', 'errada', now).kind).toBe('error')
    expect(signIn(members, 'sofia.martins@teatromunicipal.pt', 'Hc3-pW8-z2Rt', now).kind).toBe(
      'expired',
    )
    expect(signIn(members, 'joao.pinto@margens.pt', 'x', now)).toMatchObject({
      message: 'Esta conta está desativada. Fala com o administrador do Crowded.',
    })
    expect(signIn(members, 'ninguem@margens.pt', 'x', now).kind).toBe('error')
    expect(signIn(members, 'sem-email', '', now).kind).toBe('error')
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

  it('shows the expired temporary password error', async () => {
    const user = userEvent.setup()
    renderAt('/entrar')
    await user.type(screen.getByLabelText('Email'), 'sofia.martins@teatromunicipal.pt')
    await user.type(screen.getByLabelText('Palavra-passe'), 'Hc3-pW8-z2Rt')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(screen.getByRole('alert')).toHaveTextContent('A palavra-passe temporária expirou')

    await user.click(screen.getByRole('button', { name: 'Esqueci-me da palavra-passe' }))
    expect(screen.getByText(/Pede uma nova palavra-passe temporária/)).toBeInTheDocument()
  })

  it('makes a member with a temporary password choose a new one', async () => {
    const user = userEvent.setup()
    renderAt('/entrar')
    await user.type(screen.getByLabelText('Email'), 'rita.ferreira@margens.pt')
    await user.type(screen.getByLabelText('Palavra-passe'), 'T7q-mK4-v9Px')
    await user.click(screen.getByRole('button', { name: 'Mostrar palavra-passe' }))
    expect(screen.getByLabelText('Palavra-passe')).toHaveAttribute('type', 'text')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

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

  it('sends the administrator to the organizations and others back to sign-in', async () => {
    const user = userEvent.setup()
    renderAt('/entrar')
    await user.type(screen.getByLabelText('Email'), 'ana.lopes@crowded.test')
    await user.type(screen.getByLabelText('Palavra-passe'), 'qualquer')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(screen.getByRole('heading', { name: 'Organizações' })).toBeInTheDocument()
  })

  it('does not show the new password page without a temporary sign-in', () => {
    renderAt('/definir-palavra-passe')
    expect(screen.getByRole('heading', { name: 'Entrar no Crowded' })).toBeInTheDocument()
  })
})

describe('A2 platform administration', () => {
  it('lists organizations and the members of the selected one', async () => {
    const user = userEvent.setup()
    renderAt('/organizacoes')
    expect(screen.getByText('3 organizações · 9 membros')).toBeInTheDocument()
    expect(screen.getByText('12 SET 2026')).toBeInTheDocument()
    const margens = screen.getByRole('table', { name: 'Membros de Organização Margens' })
    expect(within(margens).getByText('Temporária · expira em 6 dias')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Teatro Municipal do Porto/ }))
    const teatro = screen.getByRole('table', { name: 'Membros de Teatro Municipal do Porto' })
    expect(within(teatro).getAllByRole('row')).toHaveLength(5)
  })

  it('creates an organization and a member, showing the password once', async () => {
    const user = userEvent.setup()
    renderAt('/organizacoes')

    await user.click(screen.getByRole('button', { name: 'Criar organização' }))
    let dialog = screen.getByRole('dialog', { name: 'Criar organização' })
    await user.click(within(dialog).getByRole('button', { name: 'Criar organização' }))
    expect(within(dialog).getByText('Escreve o nome da organização.')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('Nome da organização'), 'Festival Paredes')
    await user.click(within(dialog).getByRole('button', { name: 'Criar organização' }))
    expect(screen.getByRole('status')).toHaveTextContent('Festival Paredes criada.')
    expect(screen.getByText('Esta organização ainda não tem membros.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Criar membro' }))
    dialog = screen.getByRole('dialog', { name: 'Criar membro' })
    await user.type(within(dialog).getByLabelText('Nome'), 'Inês Costa')
    await user.type(within(dialog).getByLabelText('Email'), 'ines.costa@paredes.pt')
    await user.click(within(dialog).getByRole('button', { name: 'Criar membro' }))

    dialog = screen.getByRole('dialog', { name: 'Membro criado' })
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
    renderAt('/organizacoes')

    await user.click(screen.getByRole('button', { name: 'Nova palavra-passe para Miguel Sousa' }))
    const dialog = screen.getByRole('dialog', { name: 'Nova palavra-passe temporária' })
    expect(within(dialog).getByText(/A anterior deixou de funcionar/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Concluído' }))
    expect(screen.getAllByText('Temporária · expira em 7 dias')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Desativar Rita Ferreira' }))
    const confirm = screen.getByRole('dialog', { name: 'Desativar Rita Ferreira?' })
    await user.click(within(confirm).getByRole('button', { name: 'Desativar' }))
    expect(screen.getByRole('status')).toHaveTextContent('Conta de Rita Ferreira desativada.')

    await user.click(screen.getByRole('button', { name: 'Reativar João Pinto' }))
    expect(screen.getByRole('status')).toHaveTextContent('Conta de João Pinto reativada.')
  })

  it('shows a placeholder for sections not built yet', () => {
    renderAt('/auditoria')
    expect(screen.getByText('Disponível em breve.')).toBeInTheDocument()
  })
})
