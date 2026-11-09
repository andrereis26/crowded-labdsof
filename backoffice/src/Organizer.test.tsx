import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'
import {
  accessCodeFor,
  applyChange,
  demoEvents,
  draftAlert,
  formatNumber,
  suggestedLevel,
  validateEvent,
} from './data/organizer'

const now = new Date(2026, 9, 17, 22, 47)

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App now={now} />
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
      draftAlert(
        'Porta B fechada por avaria no torniquete. Desviar para a Porta C, 200 m à direita.',
      ),
    ).toEqual({
      title: 'Porta B fechada',
      message:
        'A Porta B está temporariamente fechada. Use a Porta C, a 200 m à direita da entrada principal. Obrigado pela paciência.',
    })
  })

  it('checks event codes and applies time changes', () => {
    const events = demoEvents()
    const [margens, jazz] = events
    expect(accessCodeFor('São João no Parque', '2026-06-23')).toBe('SAOJOAONOP26')
    expect(accessCodeFor('', '')).toBe('EVENTO')
    const copy = { ...events[2], id: 'novo', code: 'marg2026' }
    expect(validateEvent(copy, events).code).toBe(
      'Este código já é usado por outro evento futuro (Festival Margens 2026). Escolhe outro código.',
    )
    // Codes of ended events can be used again.
    expect(validateEvent({ ...copy, code: 'MARG2025' }, events).code).toBeUndefined()
    expect(validateEvent({ ...copy, code: '' }, events).code).toBe(
      'Indica o código impresso nos bilhetes.',
    )
    expect(validateEvent({ ...jazz, code: '', district: '' }, events)).toMatchObject({
      code: 'Indica o código de acesso.',
      district: 'Escolhe o distrito.',
    })
    expect(
      applyChange(margens, { kind: 'time', start: '22:30', doors: '20:30', message: '' }),
    ).toMatchObject({ date: margens.date, start: '22:30', doors: '20:30' })
    expect(applyChange(margens, { kind: 'venue', venue: 'Coliseu', message: '' }).venue).toBe(
      'Coliseu',
    )
  })
})

describe('O2–O4 events', () => {
  it('lists the events with indicators', () => {
    renderAt('/organizacao/eventos')
    expect(screen.getByRole('heading', { name: 'Eventos', level: 2 })).toBeInTheDocument()
    // Testing Library turns the no-break space into a plain space.
    expect(screen.getByText('1 680')).toBeInTheDocument()
    expect(screen.getAllByText('Jazz no Parque').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Público · código de acesso').length).toBe(1)
    expect(screen.queryByText(/Pedir gestão/)).not.toBeInTheDocument()
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
    await user.type(screen.getByLabelText('Código do evento impresso nos bilhetes'), 'marg2026')
    expect(screen.getByText(/já é usado por outro evento futuro/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publicar evento' })).toBeDisabled()
    await user.clear(screen.getByLabelText('Código do evento impresso nos bilhetes'))
    await user.type(screen.getByLabelText('Código do evento impresso nos bilhetes'), 'NOITE26')
    await user.type(screen.getByLabelText('Local'), 'Coliseu, Porto')
    await user.selectOptions(screen.getByLabelText('Distrito'), 'Porto')
    await user.type(screen.getByLabelText('Capacidade'), '3000')
    await user.click(screen.getByRole('button', { name: 'Adicionar' }))
    await user.type(screen.getByLabelText('Nome da entrada ou ponto de fila'), 'Porta B{Enter}')
    expect(screen.getByRole('button', { name: 'Remover Porta B' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Publicar evento' }))

    expect(screen.getByRole('heading', { name: 'Noite Margens', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Noite Margens publicado.')
    expect(screen.getAllByText('SÁB 21 NOV · 22:00 · Coliseu, Porto').length).toBeGreaterThan(0)
  })

  it('keeps field hints in tooltips that stay readable by screen readers', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/eventos/novo')
    const hint = 'Usada para calcular a lotação a partir do contador de entradas.'

    expect(screen.getByLabelText('Capacidade')).toHaveAccessibleDescription(hint)
    expect(
      screen.getByRole('switch', { name: 'Pedir confirmação de reportes' }),
    ).toHaveAccessibleDescription('Outros participantes confirmam com Sim ou Não.')

    const [capacityInfo] = screen.getAllByRole('button', { name: 'Mais informação' })
    await user.click(capacityInfo)
    expect(screen.getByRole('tooltip', { name: hint })).toHaveClass('absolute')
    await user.keyboard('{Escape}')
    expect(screen.getByText(hint)).toHaveClass('sr-only')
  })

  it('generates the access code and QR code of a public event', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/eventos/novo')
    await user.type(screen.getByLabelText('Nome do evento'), 'São João no Parque')
    fireEvent.change(screen.getByLabelText('Data'), { target: { value: '2026-06-23' } })
    await user.click(screen.getByRole('radio', { name: /Público/ }))

    expect(screen.getByLabelText('Código de acesso')).toHaveValue('SAOJOAONOP26')
    expect(screen.getByRole('img', { name: 'Código QR de SAOJOAONOP26' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Descarregar código QR (PNG)' })).toBeInTheDocument()
    expect(screen.getByText('Livre')).toBeInTheDocument()
  })

  it('postpones an event only after confirming the change is official', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/eventos/margens-inverno')

    await user.click(screen.getByRole('button', { name: 'Alterar ou cancelar' }))
    const dialog = screen.getByRole('dialog', { name: 'Alterar evento' })
    expect(
      within(dialog).getByLabelText('O bilhete continua válido para a nova data'),
    ).toBeChecked()
    await user.type(within(dialog).getByLabelText(/Reembolso/), 'Reembolso até 5 DEZ.')
    fireEvent.change(within(dialog).getByLabelText('Nova data'), {
      target: { value: '2026-12-19' },
    })
    await user.click(within(dialog).getByRole('button', { name: 'Adiar e notificar' }))
    expect(within(dialog).getByText('Confirma que a alteração é oficial.')).toBeInTheDocument()

    await user.click(within(dialog).getByLabelText('Confirmo que esta alteração é oficial.'))
    await user.click(within(dialog).getByRole('button', { name: 'Adiar e notificar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Evento adiado. 128 pessoas notificadas.')
    expect(screen.getByText('SÁB 19 DEZ · 21:00 · Pavilhão Municipal, Porto')).toBeInTheDocument()
  })

  it('changes the time of a public event and cancels it', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/eventos/jazz-no-parque')
    expect(screen.getByText('JAZZPARQUE26')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Alterar ou cancelar' }))
    let dialog = screen.getByRole('dialog', { name: 'Alterar evento' })
    await user.click(within(dialog).getByRole('radio', { name: 'Hora e portas' }))
    await user.click(within(dialog).getByLabelText('Confirmo que esta alteração é oficial.'))
    await user.click(within(dialog).getByRole('button', { name: 'Alterar hora e notificar' }))
    expect(
      within(dialog).getByText('Escolhe uma nova hora ou abertura de portas.'),
    ).toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText('Nova hora'), { target: { value: '19:30' } })
    await user.click(within(dialog).getByRole('button', { name: 'Alterar hora e notificar' }))
    expect(screen.getByRole('status')).toHaveTextContent('Horário alterado. 312 pessoas')

    await user.click(screen.getByRole('button', { name: 'Alterar ou cancelar' }))
    dialog = screen.getByRole('dialog', { name: 'Alterar evento' })
    await user.click(within(dialog).getByRole('radio', { name: 'Local' }))
    await user.click(within(dialog).getByLabelText('Confirmo que esta alteração é oficial.'))
    await user.click(within(dialog).getByRole('button', { name: 'Mudar local e notificar' }))
    expect(within(dialog).getByText('Indica o novo local.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('radio', { name: 'Cancelar' }))
    // Public events have no ticket: no ticket or refund fields.
    expect(within(dialog).queryByText('O bilhete continua válido')).not.toBeInTheDocument()
    expect(within(dialog).getByText(/alteração crítica/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar e notificar' }))
    expect(screen.getByRole('status')).toHaveTextContent('Evento cancelado.')
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
    expect(
      within(screen.getByRole('group', { name: 'espera em Bar principal' })).getByText('6 min'),
    ).toBeInTheDocument()
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

    await user.click(screen.getByRole('radio', { name: 'Crítico' }))
    expect(screen.getByText(/bilhete ou código de acesso\) · 1 240/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Enviar alerta' }))
    expect(screen.getByRole('status')).toHaveTextContent('Alerta enviado a 1 240 pessoas.')
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
    expect(
      screen.getByRole('heading', { name: 'Rampa da entrada Norte bloqueada' }),
    ).toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Categoria' }), 'Acessibilidade')
    const groups = screen.getByRole('list', { name: 'Grupos de reportes' })
    expect(within(groups).getAllByText('Rampa da entrada Norte bloqueada').length).toBeGreaterThan(
      0,
    )
    expect(within(groups).queryByText('Fila longa na Porta C')).not.toBeInTheDocument()
    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Categoria' }),
      'Todas as categorias',
    )

    await user.click(screen.getByRole('button', { name: 'Resolvidos · 1' }))
    expect(screen.getAllByText('Falta de água no bar principal').length).toBeGreaterThan(0)
    expect(screen.queryByText('Carteira castanha perdida')).not.toBeInTheDocument()
  })

  it('never asks confirmation for personal categories', () => {
    renderAt('/organizacao/definicoes')
    expect(
      screen.getByRole('switch', { name: 'Pedir confirmação para Perdidos e achados' }),
    ).toBeDisabled()
    expect(screen.getByRole('switch', { name: 'Pedir confirmação para Fila' })).toBeChecked()
  })
})

describe('O12 requests', () => {
  it('previews and publishes an urgent request', async () => {
    const user = userEvent.setup()
    renderAt('/organizacao/pedidos')

    await user.click(screen.getByRole('radio', { name: /Escolha múltipla/ }))
    expect(screen.getByRole('option', { name: 'Várias respostas' })).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /Confirmação/ }))
    await user.type(screen.getByLabelText('Pergunta'), 'Vais usar o autocarro especial das 23:30?')
    await user.click(screen.getByRole('switch', { name: 'Marcar como urgente' }))
    const preview = screen.getByRole('complementary', { name: 'Pré-visualização' })
    expect(within(preview).getByText('URGENTE')).toBeInTheDocument()
    expect(
      within(preview).getByText('Vais usar o autocarro especial das 23:30?'),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Publicar pedido' }))
    expect(screen.getByRole('status')).toHaveTextContent('Pedido publicado e notificação enviada.')
    expect(screen.getByText(/Pedidos ativos: 3/)).toBeInTheDocument()
  })
})
