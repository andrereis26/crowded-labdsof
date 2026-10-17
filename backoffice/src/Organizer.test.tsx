import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'
import { draftAlert, formatNumber, suggestedLevel } from './data/organizer'
import { validateRegistration } from './data/verifications'

const now = new Date(2026, 9, 17, 22, 47)

/** Lets a test move to another route of the same app, keeping its state. */
function GoTo() {
  const navigate = useNavigate()
  return (
    <button type="button" onClick={() => navigate('/verificacoes')}>
      ir para verificações
    </button>
  )
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App now={now} />
      <GoTo />
    </MemoryRouter>,
  )
}

const nbsp = (text: string) => text.replace(/ /g, ' ')

describe('organizer helpers', () => {
  it('formats numbers, suggests crowd levels and drafts alerts', () => {
    expect(formatNumber(1680)).toBe(nbsp('1 680'))
    expect(formatNumber(312)).toBe('312')
    expect(suggestedLevel(2790, 4500)).toBe('moderate')
    expect(suggestedLevel(4400, 4500)).toBe('full')
    expect(
      draftAlert('Porta B fechada por avaria no torniquete. Desviar para a Porta C, 200 m à direita.'),
    ).toEqual({
      title: 'Porta B fechada',
      message:
        'A Porta B está temporariamente fechada. Use a Porta C, a 200 m à direita da entrada principal. Obrigado pela paciência.',
    })
  })

  it('validates an organization registration', () => {
    const errors = validateRegistration({
      organization: 'Margens',
      nipc: '1234',
      website: 'margens.pt',
      responsible: 'Ana',
      email: 'ana@margens',
      confirmed: false,
    })
    expect(Object.keys(errors).sort()).toEqual(['confirmed', 'document', 'email', 'nipc'])
  })
})

describe('O1 organization registration', () => {
  it('submits a registration that reaches the verification queue', async () => {
    const user = userEvent.setup()
    renderAt('/registo')

    await user.click(screen.getByRole('button', { name: 'Submeter para verificação' }))
    expect(screen.getByText('O NIPC tem 9 algarismos.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Nome da organização'), 'Festival Douro')
    await user.type(screen.getByLabelText('NIPC'), '509876543')
    await user.type(screen.getByLabelText('Site'), 'festivaldouro.pt')
    await user.type(screen.getByLabelText('Responsável'), 'Inês Costa')
    await user.type(screen.getByLabelText('Email profissional'), 'ines@festivaldouro.pt')
    await user.upload(
      screen.getByLabelText('Comprovativo da organização'),
      new File(['%PDF'], 'certidao.pdf', { type: 'application/pdf' }),
    )
    await user.click(screen.getByLabelText(/Confirmo que represento/))
    await user.click(screen.getByRole('button', { name: 'Submeter para verificação' }))

    expect(screen.getByRole('heading', { name: 'Verificação em curso' })).toBeInTheDocument()
    expect(screen.getByText(/Recebemos o pedido da Festival Douro/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'ir para verificações' }))
    expect(
      within(screen.getByRole('list', { name: 'Pedidos' })).getByText('Festival Douro'),
    ).toBeInTheDocument()
  })
})

describe('O2–O4 events', () => {
  it('lists the events with indicators', () => {
    renderAt('/organizacao/eventos')
    expect(screen.getByRole('heading', { name: 'Eventos', level: 2 })).toBeInTheDocument()
    // Testing Library turns the no-break space into a plain space.
    expect(screen.getByText('1 680')).toBeInTheDocument()
    expect(screen.getAllByText('Jazz no Parque').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Bilheteira · sincronizado').length).toBe(1)
  })

  it('validates and publishes a new event', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/eventos/novo')

    await user.click(screen.getByRole('button', { name: 'Publicar evento' }))
    expect(screen.getByText('Escreve o nome do evento.')).toBeInTheDocument()
    expect(screen.getByText('Indica o local.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Nome do evento'), 'Noite Margens')
    fireEvent.change(screen.getByLabelText('Data'), { target: { value: '2026-11-21' } })
    fireEvent.change(screen.getByLabelText('Início'), { target: { value: '22:00' } })
    await user.type(screen.getByLabelText('Local'), 'Coliseu, Porto')
    await user.type(screen.getByLabelText('Capacidade'), '3000')
    await user.click(screen.getByRole('button', { name: 'Adicionar' }))
    await user.type(screen.getByLabelText('Nome da entrada ou ponto de fila'), 'Porta B{Enter}')
    expect(screen.getByRole('button', { name: 'Remover Porta B' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Publicar evento' }))

    expect(screen.getByRole('heading', { name: 'Noite Margens', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Noite Margens publicado.')
    expect(screen.getAllByText('SÁB 21 NOV · 22:00 · Coliseu, Porto').length).toBeGreaterThan(0)
  })

  it('postpones an event only after confirming the change is official', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/eventos/margens-inverno')

    await user.click(screen.getByRole('button', { name: 'Alterar ou cancelar' }))
    const dialog = screen.getByRole('dialog', { name: 'Alterar data, local ou cancelar' })
    fireEvent.change(within(dialog).getByLabelText('Nova data'), { target: { value: '2026-12-19' } })
    await user.click(within(dialog).getByRole('button', { name: 'Adiar e notificar' }))
    expect(within(dialog).getByText('Confirma que a alteração é oficial.')).toBeInTheDocument()

    await user.click(within(dialog).getByLabelText('Confirmo que esta alteração é oficial.'))
    await user.click(within(dialog).getByRole('button', { name: 'Adiar e notificar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Evento adiado. 128 pessoas notificadas.')
    expect(screen.getByText('SÁB 19 DEZ · 21:00 · Pavilhão Municipal, Porto')).toBeInTheDocument()
  })

  it('does not let organizers change synced events', () => {
    renderAt('/organizacao/eventos/jazz-no-parque')
    expect(screen.getByRole('button', { name: 'Alterar ou cancelar' })).toBeDisabled()
    expect(screen.getByText('Evento sincronizado da bilheteira')).toBeInTheDocument()
  })
})

describe('O5–O7 live operations', () => {
  it('publishes the crowd level and manual queue times', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/ao-vivo/lotacao')

    await user.click(screen.getByRole('radio', { name: /Elevada/ }))
    await user.click(screen.getByRole('button', { name: 'Publicar lotação' }))
    expect(screen.getByText('Lotação publicada: Elevada.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Aumentar espera em Bar principal' }))
    expect(within(screen.getByRole('group', { name: 'espera em Bar principal' })).getByText('6 min')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Publicar filas' }))
    expect(screen.getByText('Filas publicadas.')).toBeInTheDocument()
  })

  it('drafts an alert with AI and sends it', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/alertas')

    await user.click(screen.getByRole('button', { name: 'Gerar rascunho com IA' }))
    expect(screen.getByText('Descreve o que aconteceu para gerar o rascunho.')).toBeInTheDocument()

    await user.type(
      screen.getByLabelText('O que aconteceu e o que devem fazer?'),
      'Porta B fechada por avaria no torniquete. Desviar para a Porta C, 200 m à direita.',
    )
    await user.click(screen.getByRole('button', { name: 'Gerar rascunho com IA' }))
    expect(screen.getByText('Rascunho gerado por IA')).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toHaveValue('Porta B fechada')

    await user.click(screen.getByRole('radio', { name: /Só quem tem bilhete/ }))
    await user.click(screen.getByRole('button', { name: 'Enviar alerta' }))
    expect(screen.getByRole('status')).toHaveTextContent('Alerta enviado a 930 pessoas.')
    expect(screen.getByText('Porta B fechada')).toBeInTheDocument()
  })

  it('shows the live dashboard from the shared state', () => {
    renderAt('/organizacao/ao-vivo')
    expect(screen.getByText('Porta C · 25 min')).toBeInTheDocument()
    expect(screen.getByText('1 urgente')).toBeInTheDocument()
  })
})

describe('O8–O10 incidents and confirmations', () => {
  it('resolves an incident and updates the open count', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/incidentes/fila-porta-c')
    const nav = screen.getByRole('navigation', { name: 'Organização' })
    expect(within(nav).getByLabelText('4 pendentes')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Reconhecer' }))
    expect(screen.getByRole('button', { name: 'Reconhecido' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Resolver' }))

    expect(screen.getByRole('status')).toHaveTextContent('Resolvido por Miguel Sousa')
    expect(within(nav).getByLabelText('3 pendentes')).toBeInTheDocument()
  })

  it('filters incident groups and previews the selected one', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/incidentes')

    await user.click(screen.getByRole('button', { name: /Rampa da entrada Norte bloqueada/ }))
    expect(screen.getByRole('heading', { name: 'Rampa da entrada Norte bloqueada' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Resolvidos · 1' }))
    expect(screen.getAllByText('Falta de água no bar principal').length).toBeGreaterThan(0)
    expect(screen.queryByText('Carteira castanha perdida')).not.toBeInTheDocument()
  })

  it('never asks confirmation for personal categories', () => {
    renderAt('/organizacao/definicoes')
    expect(screen.getByRole('switch', { name: 'Pedir confirmação para Perdidos e achados' })).toBeDisabled()
    expect(screen.getByRole('switch', { name: 'Pedir confirmação para Fila' })).toBeChecked()
  })
})

describe('O11–O14', () => {
  it('shows poll results of the selected event and publishes a new poll', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/eventos/margens-inverno')
    await user.click(within(screen.getByRole('navigation', { name: 'Organização' })).getByRole('link', { name: 'Sondagens' }))

    expect(screen.getByText('Vais ao Margens Inverno a 12 de dezembro?')).toBeInTheDocument()
    expect(screen.getByText('486')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Pergunta'), 'Vais usar o estacionamento?')
    fireEvent.change(screen.getByLabelText('Fecha a'), { target: { value: '2026-12-10T23:59' } })
    await user.click(screen.getByRole('button', { name: 'Publicar sondagem' }))
    expect(screen.getByRole('status')).toHaveTextContent('Sondagem publicada.')
  })

  it('previews and publishes an urgent request', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/pedidos')

    await user.type(screen.getByLabelText('Pergunta'), 'Vais usar o autocarro especial das 23:30?')
    await user.click(screen.getByRole('switch', { name: 'Marcar como urgente' }))
    const preview = screen.getByRole('complementary', { name: 'Pré-visualização' })
    expect(within(preview).getByText('URGENTE')).toBeInTheDocument()
    expect(within(preview).getByText('Vais usar o autocarro especial das 23:30?')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Publicar pedido' }))
    expect(screen.getByRole('status')).toHaveTextContent('Pedido publicado e notificação enviada.')
    expect(screen.getByText(/Pedidos ativos: 3/)).toBeInTheDocument()
  })

  it('sends a management request to the administrators', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/eventos/pedir-gestao')

    await user.click(screen.getByRole('button', { name: 'Enviar pedido' }))
    expect(screen.getByText('Carrega um comprovativo da tua relação com o evento.')).toBeInTheDocument()

    await user.upload(
      screen.getByLabelText('Comprovativo'),
      new File(['%PDF'], 'contrato.pdf', { type: 'application/pdf' }),
    )
    await user.click(screen.getByRole('button', { name: 'Enviar pedido' }))
    expect(screen.getByRole('status')).toHaveTextContent('Pedido enviado para Jazz no Parque.')

    await user.click(screen.getByRole('button', { name: 'ir para verificações' }))
    expect(screen.getByText('Pedido de gestão · Jazz no Parque')).toBeInTheDocument()
  })

  it('shows the post-event summary of an ended event', () => {
    renderAt('/organizacao/eventos/festival-margens-2025/resumo')
    expect(screen.getByText('94%')).toBeInTheDocument()
    expect(screen.getByText('92% · 23:10')).toBeInTheDocument()
  })
})
