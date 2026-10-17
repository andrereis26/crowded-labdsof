import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'
import { decide, demoRequests, initials, maskNipc, timeAgo } from './data/verifications'

const now = new Date(2026, 9, 17, 12, 42)

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App now={now} />
    </MemoryRouter>,
  )
}

describe('verification helpers', () => {
  it('formats initials, NIPC and relative times', () => {
    expect(initials('Associação Cultural do Bonfim')).toBe('AB')
    expect(maskNipc('500123123')).toBe('500 •••• 123')
    expect(timeAgo(new Date(now.getTime() - 2 * 3600000), now)).toBe('há 2 h')
    expect(timeAgo(new Date(now.getTime() - 26 * 3600000), now)).toBe('há 1 dia')
  })

  it('requires a note to request information or reject', () => {
    const [request] = demoRequests(now)
    expect(decide(request, 'reject', '  ')).toEqual({
      error: 'Escreve uma nota para a organização.',
    })
    expect(decide(request, 'approve', '')).toMatchObject({ status: 'approved' })
    expect(decide(request, 'request-info', 'Envia o documento.')).toMatchObject({
      status: 'info-requested',
      note: 'Envia o documento.',
    })
  })
})

describe('A1 verification queue', () => {
  it('lists pending requests and shows the first one', () => {
    renderAt('/verificacoes')

    expect(screen.getByRole('button', { name: 'Pendentes · 3' })).toBeInTheDocument()
    const list = screen.getByRole('list', { name: 'Pedidos' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(3)
    expect(within(list).getByText('Pedido de gestão · A Tempestade')).toBeInTheDocument()
    expect(
      screen.getByText('Submetido hoje às 10:42 por Miguel Sousa'),
    ).toBeInTheDocument()
    expect(screen.getByText('500 •••• 123')).toBeInTheDocument()
  })

  it('approves a request and moves to the next one', async () => {
    const user = userEvent.setup()
    renderAt('/verificacoes/organizacao-margens')

    await user.click(screen.getByRole('button', { name: 'Aprovar' }))

    expect(screen.getByRole('status')).toHaveTextContent(
      'Organização Margens aprovada.',
    )
    expect(screen.getByRole('button', { name: 'Pendentes · 2' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Aprovadas' }))
    expect(
      within(screen.getByRole('list', { name: 'Pedidos' })).getByText(
        'Organização Margens',
      ),
    ).toBeInTheDocument()
  })

  it('asks for a note before rejecting', async () => {
    const user = userEvent.setup()
    renderAt('/verificacoes/associacao-bonfim')

    await user.click(screen.getByRole('button', { name: 'Rejeitar' }))
    expect(screen.getByText('Escreve uma nota para a organização.')).toBeInTheDocument()

    await user.type(
      screen.getByLabelText(/Nota para a organização/),
      'O documento não corresponde à associação.',
    )
    await user.click(screen.getByRole('button', { name: 'Rejeitar' }))
    expect(screen.getByRole('status')).toHaveTextContent(
      'Associação Cultural do Bonfim rejeitada.',
    )
  })

  it('shows a placeholder for sections not built yet', () => {
    renderAt('/auditoria')
    expect(screen.getByText('Disponível em breve.')).toBeInTheDocument()
  })
})
