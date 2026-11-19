import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './App'
import type { Session } from './data/auth'
import {
  accessCodeFor,
  applyChange,
  demoEvents,
  draftAlert,
  formatNumber,
  suggestedLevel,
  validateEvent,
} from './data/organizer'
import { EventsApiError } from './data/eventsApi'
import { fakeEventsApi, fakeGeocoder } from './test/fakeEventsApi'

const now = new Date(2026, 9, 17, 22, 47)

const member: Session = {
  accessToken: 'jwt',
  expiresAt: new Date(2026, 9, 18),
  userId: 'u-1',
  role: 'ORGANIZATION_MEMBER',
  organizationId: 'org-1',
}

/** Renders `path` for a signed-in member, and waits for the events to load. */
async function renderAt(path: string, events = fakeEventsApi()) {
  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <App
        now={now}
        initialSession={member}
        eventsApiFor={() => events.api}
        geocoder={fakeGeocoder}
      />
    </MemoryRouter>,
  )
  await waitFor(() => expect(screen.queryByText('A carregar os eventos…')).not.toBeInTheDocument())
  return { ...view, events }
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
  it('lists the events with indicators', async () => {
    await renderAt('/organization/events')
    expect(screen.getByRole('heading', { name: 'Eventos', level: 2 })).toBeInTheDocument()
    // Testing Library turns the no-break space into a plain space.
    expect(screen.getByText('1 680')).toBeInTheDocument()
    expect(screen.getAllByText('Jazz no Parque').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Público · código de acesso').length).toBe(1)
    expect(screen.queryByText(/Pedir gestão/)).not.toBeInTheDocument()
  })

  it('validates and publishes a new event', async () => {
    const user = userEvent.setup()
    const { events } = await renderAt('/organization/events/new')

    await user.click(screen.getByRole('button', { name: 'Publicar evento' }))
    expect(screen.getByText('Escreve o nome do evento.')).toBeInTheDocument()
    expect(screen.getByText('Indica o local.')).toBeInTheDocument()
    expect(screen.getByText('Indica a morada.')).toBeInTheDocument()
    expect(screen.getByText('Indica a hora de fim.')).toBeInTheDocument()
    expect(events.calls).not.toContain('create')

    await user.type(screen.getByLabelText('Nome do evento'), 'Noite Margens')
    await user.type(screen.getByLabelText('Descrição'), 'Concerto de outono.')
    fireEvent.change(screen.getByLabelText('Data'), { target: { value: '2026-11-21' } })
    fireEvent.change(screen.getByLabelText('Início'), { target: { value: '22:00' } })
    fireEvent.change(screen.getByLabelText('Fim'), { target: { value: '01:00' } })
    await user.type(screen.getByLabelText('Código do evento impresso nos bilhetes'), 'marg2026')
    expect(screen.getByText(/já é usado por outro evento futuro/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Publicar evento' })).toBeDisabled()
    await user.clear(screen.getByLabelText('Código do evento impresso nos bilhetes'))
    await user.type(screen.getByLabelText('Código do evento impresso nos bilhetes'), 'NOITE26')
    await user.type(screen.getByLabelText('Local'), 'Coliseu, Porto')
    await user.type(screen.getByLabelText('Morada'), 'Sem nome')
    await user.tab()
    expect(await screen.findByText(/Não encontrámos esta morada no mapa/)).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Morada'))
    await user.type(screen.getByLabelText('Morada'), 'Rua de Passos Manuel 137, Porto')
    await user.tab()
    expect(
      await screen.findByTitle('Mapa com a localização de Rua de Passos Manuel 137, Porto'),
    ).toHaveAttribute('src', expect.stringContaining('marker=41.14960,-8.61100'))
    await user.selectOptions(screen.getByLabelText('Distrito'), 'Porto')
    await user.type(screen.getByLabelText('Capacidade'), '3000')
    await user.click(screen.getByRole('button', { name: 'Adicionar' }))
    await user.type(screen.getByLabelText('Nome da entrada ou ponto de fila'), 'Porta B{Enter}')
    expect(screen.getByRole('button', { name: 'Remover Porta B' })).toBeInTheDocument()

    await user.click(screen.getByRole('switch', { name: 'Pedir confirmação para Fila' }))
    const radius = screen.getByLabelText('Raio do evento (em metros)')
    await user.clear(radius)
    await user.type(radius, '60000')
    await user.click(screen.getByRole('button', { name: 'Publicar evento' }))
    expect(screen.getByText('Indica um raio entre 100 e 50 000 metros.')).toBeInTheDocument()
    await user.clear(radius)
    // A public event spread over a city needs a larger radius than a venue.
    await user.type(radius, '20000')

    await user.click(screen.getByRole('button', { name: 'Publicar evento' }))

    expect(
      await screen.findByRole('heading', { name: 'Noite Margens', level: 2 }),
    ).toBeInTheDocument()
    const [created] = events.events
    expect(created).toMatchObject({
      code: 'NOITE26',
      address: 'Rua de Passos Manuel 137, Porto',
      latitude: 41.1496,
      entrances: ['Porta A', 'Porta B'],
    })
    expect(created.confirmations).toMatchObject({ radiusMeters: 20000 })
    expect(created.confirmations.categories.queue).toBe(false)
    expect(screen.getByRole('status')).toHaveTextContent('Noite Margens publicado.')
    expect(screen.getAllByText('SÁB 21 NOV · 22:00 · Coliseu, Porto').length).toBeGreaterThan(0)
  })

  it('keeps field hints in tooltips that stay readable by screen readers', async () => {
    const user = userEvent.setup()
    await renderAt('/organization/events/new')
    const hint = 'Usada para calcular a lotação a partir do contador de entradas.'

    expect(screen.getByLabelText('Capacidade')).toHaveAccessibleDescription(hint)
    expect(screen.getByLabelText('Respostas para confiança alta')).toHaveAccessibleDescription(
      'De 2 a 50.',
    )

    const [capacityInfo] = screen.getAllByRole('button', { name: 'Mais informação' })
    await user.click(capacityInfo)
    expect(screen.getByRole('tooltip', { name: hint })).toHaveClass('absolute')
    await user.keyboard('{Escape}')
    expect(screen.getByText(hint)).toHaveClass('sr-only')
  })

  it('generates the access code and QR code of a public event', async () => {
    const user = userEvent.setup()
    await renderAt('/organization/events/new')
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
    await renderAt('/organization/events/margens-inverno')

    await user.click(screen.getByRole('button', { name: 'Alterar ou cancelar' }))
    const dialog = screen.getByRole('dialog', { name: 'Alterar evento' })
    expect(
      within(dialog).getByLabelText('O bilhete continua válido para a nova data'),
    ).toBeChecked()
    await user.type(within(dialog).getByLabelText(/Reembolso/), 'Reembolso até 5 DEZ.')
    await user.click(within(dialog).getByLabelText('Confirmo que esta alteração é oficial.'))
    await user.click(within(dialog).getByRole('button', { name: 'Adiar e notificar' }))
    expect(
      within(dialog).getByText('Escolhe uma data ou hora posterior à atual.'),
    ).toBeInTheDocument()
    // Same date, later hour: a postponement too.
    fireEvent.change(within(dialog).getByLabelText('Nova hora'), { target: { value: '22:00' } })
    await user.click(within(dialog).getByRole('button', { name: 'Adiar e notificar' }))
    expect(await screen.findByText(/Evento adiado\./)).toBeInTheDocument()
    expect(screen.getByText('SÁB 12 DEZ · 22:00 · Pavilhão Municipal, Porto')).toBeInTheDocument()
  })

  it('postpones an event to a later date', async () => {
    const user = userEvent.setup()
    await renderAt('/organization/events/margens-inverno')
    await user.click(screen.getByRole('button', { name: 'Alterar ou cancelar' }))
    const dialog = screen.getByRole('dialog', { name: 'Alterar evento' })
    await user.type(within(dialog).getByLabelText(/Reembolso/), 'Reembolso até 5 DEZ.')
    fireEvent.change(within(dialog).getByLabelText('Nova data'), {
      target: { value: '2026-12-19' },
    })
    await user.click(within(dialog).getByRole('button', { name: 'Adiar e notificar' }))
    expect(within(dialog).getByText('Confirma que a alteração é oficial.')).toBeInTheDocument()

    await user.click(within(dialog).getByLabelText('Confirmo que esta alteração é oficial.'))
    await user.click(within(dialog).getByRole('button', { name: 'Adiar e notificar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('status')).toHaveTextContent('Evento adiado. 128 pessoas notificadas.')
    expect(screen.getByText('SÁB 19 DEZ · 21:00 · Pavilhão Municipal, Porto')).toBeInTheDocument()
  })

  it('changes the time of a public event and cancels it', async () => {
    const user = userEvent.setup()
    await renderAt('/organization/events/jazz-no-parque')
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
    expect(await screen.findByRole('status')).toHaveTextContent('Horário alterado. 312 pessoas')

    await user.click(screen.getByRole('button', { name: 'Alterar ou cancelar' }))
    dialog = screen.getByRole('dialog', { name: 'Alterar evento' })
    await user.click(within(dialog).getByRole('radio', { name: 'Local' }))
    await user.click(within(dialog).getByLabelText('Confirmo que esta alteração é oficial.'))
    await user.click(within(dialog).getByRole('button', { name: 'Mudar local e notificar' }))
    expect(within(dialog).getByText('Indica o novo local.')).toBeInTheDocument()
    expect(within(dialog).getByText('Indica a morada do novo local.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('radio', { name: 'Cancelar' }))
    // Public events have no ticket: no ticket or refund fields.
    expect(within(dialog).queryByText('O bilhete continua válido')).not.toBeInTheDocument()
    expect(within(dialog).getByText(/alteração crítica/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar e notificar' }))
    expect(await screen.findByText(/Evento cancelado\./)).toBeInTheDocument()
  })
})

describe('O2–O4 with Core', () => {
  it('corrects an event without touching what an official change covers', async () => {
    const user = userEvent.setup()
    const { events } = await renderAt('/organization/events/margens-inverno/edit')

    for (const label of ['Data', 'Abertura de portas', 'Início', 'Fim', 'Local', 'Morada']) {
      expect(screen.getByLabelText(label)).toBeDisabled()
    }
    expect(screen.getByRole('link', { name: 'Alterar ou cancelar' })).toHaveAttribute(
      'href',
      '/organization/events/margens-inverno',
    )
    await user.clear(screen.getByLabelText('Capacidade'))
    await user.type(screen.getByLabelText('Capacidade'), '5000')
    await user.click(screen.getByRole('button', { name: 'Remover Porta B' }))
    await user.click(screen.getByRole('radio', { name: /Público/ }))
    await user.selectOptions(screen.getByLabelText('Distrito'), 'Braga')
    await user.click(screen.getByRole('button', { name: 'Guardar alterações' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Alterações guardadas.')
    expect(events.events.find((e) => e.id === 'margens-inverno')).toMatchObject({
      capacity: 5000,
      entrances: ['Porta A', 'Bar principal'],
      kind: 'public',
      district: 'Braga',
    })
  })

  it('adds, replaces and removes the image of an event', async () => {
    // The browser's picker hides other types; a file dropped or pasted may still be one.
    const user = userEvent.setup({ applyAccept: false })
    const { events } = await renderAt('/organization/events/margens-inverno/edit')
    const panel = () => screen.getByText('Imagem do evento').closest('section') as HTMLElement

    await user.click(within(panel()).getByRole('button', { name: 'Adicionar imagem' }))
    await user.upload(
      screen.getByLabelText('Escolher a imagem do evento'),
      new File(['gif'], 'cartaz.gif', { type: 'image/gif' }),
    )
    expect(within(panel()).getByRole('alert')).toHaveTextContent(
      'Escolhe uma imagem JPEG, PNG ou WebP.',
    )
    const poster = new File(['png'], 'cartaz.png', { type: 'image/png' })
    await user.upload(screen.getByLabelText('Escolher a imagem do evento'), poster)
    expect(within(panel()).getByText('cartaz.png')).toBeInTheDocument()
    expect(within(panel()).queryByRole('alert')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar alterações' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Alterações guardadas.')
    expect(events.images.get('margens-inverno')).toBe(poster)
    expect(events.calls).toContain('uploadImage')

    await user.click(screen.getAllByRole('link', { name: 'Editar' })[0])
    expect(within(panel()).getByText('Imagem atual')).toBeInTheDocument()
    await user.click(within(panel()).getByRole('button', { name: 'Remover' }))
    expect(within(panel()).getByRole('button', { name: 'Adicionar imagem' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar alterações' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Alterações guardadas.')
    expect(events.images.has('margens-inverno')).toBe(false)
    expect(events.calls).toContain('removeImage')
  })

  it('keeps the new event when Core refuses its image', async () => {
    const user = userEvent.setup()
    const events = fakeEventsApi()
    await renderAt('/organization/events/margens-inverno/edit', events)
    await user.upload(
      screen.getByLabelText('Escolher a imagem do evento'),
      new File(['png'], 'cartaz.png', { type: 'image/png' }),
    )
    const save = screen.getByRole('button', { name: 'Guardar alterações' })
    events.api.uploadImage = async () => {
      throw new EventsApiError('IMAGE_INVALID')
    }
    await user.click(save)

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Alterações guardadas. A imagem não foi guardada: Não foi possível ler esta imagem. Escolhe outra.',
    )
  })

  it('shows Core refusals on their field or above the form', async () => {
    const user = userEvent.setup()
    const { events } = await renderAt('/organization/events/margens-inverno/edit')

    events.refuse = 'EVENT_CODE_TAKEN'
    await user.click(screen.getByRole('button', { name: 'Guardar alterações' }))
    expect(
      await screen.findByText(
        'Este código já é usado por outro evento futuro. Escolhe outro código.',
      ),
    ).toBeInTheDocument()

    events.refuse = 'NOT_EVENT_ORGANIZATION'
    await user.click(screen.getByRole('button', { name: 'Guardar alterações' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Só os membros ativos da organização podem gerir os seus eventos.',
    )

    await user.clear(screen.getByLabelText('Respostas para confiança alta'))
    await user.type(screen.getByLabelText('Respostas para confiança alta'), '99')
    await user.click(screen.getByRole('button', { name: 'Guardar alterações' }))
    expect(screen.getByText('Escolhe um número entre 2 e 50.')).toBeInTheDocument()
  })

  it('keeps a change dialog open with the reason when Core refuses it', async () => {
    const user = userEvent.setup()
    const { events } = await renderAt('/organization/events/jazz-no-parque')

    await user.click(screen.getByRole('button', { name: 'Alterar ou cancelar' }))
    const dialog = screen.getByRole('dialog', { name: 'Alterar evento' })
    await user.click(within(dialog).getByRole('radio', { name: 'Local' }))
    await user.type(within(dialog).getByLabelText('Novo local'), 'Coliseu')
    await user.type(within(dialog).getByLabelText('Morada do novo local'), 'Rua de Passos Manuel')
    await user.tab()
    expect(await within(dialog).findByTitle(/Mapa com a localização/)).toBeInTheDocument()
    await user.click(within(dialog).getByLabelText('Confirmo que esta alteração é oficial.'))

    events.refuse = 'EVENT_CLOSED'
    await user.click(within(dialog).getByRole('button', { name: 'Mudar local e notificar' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Este evento já foi cancelado ou terminou.',
    )

    await user.click(within(dialog).getByRole('button', { name: 'Mudar local e notificar' }))
    expect(await screen.findByText(/Local alterado\./)).toBeInTheDocument()
    expect(events.events.find((e) => e.id === 'jazz-no-parque')).toMatchObject({
      venue: 'Coliseu',
      address: 'Rua de Passos Manuel',
      latitude: 41.1496,
    })
  })

  it('says when the map cannot be reached, not that the address is wrong', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/organization/events/new']}>
        <App
          now={now}
          initialSession={member}
          eventsApiFor={() => fakeEventsApi().api}
          geocoder={() => Promise.reject(new Error('down'))}
        />
      </MemoryRouter>,
    )
    await user.type(await screen.findByLabelText('Morada'), 'Rua de Passos Manuel 137')
    await user.tab()
    expect(
      await screen.findByText(/Não foi possível consultar o mapa para confirmar a morada/),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Publicar evento' }))
    expect(screen.queryByText(/Não encontrámos esta morada/)).not.toBeInTheDocument()
    expect(screen.getAllByText(/Não foi possível consultar o mapa/)).toHaveLength(1)
  })

  it('offers to try again when the events cannot be loaded', async () => {
    const user = userEvent.setup()
    const events = fakeEventsApi()
    events.refuse = 'NETWORK'
    render(
      <MemoryRouter initialEntries={['/organization/events']}>
        <App now={now} initialSession={member} eventsApiFor={() => events.api} />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar os eventos.',
    )
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await screen.findByRole('heading', { name: 'Eventos', level: 2 })).toBeInTheDocument()
  })

  it('starts an organization without events from O2', async () => {
    await renderAt('/organization/live', fakeEventsApi([]))

    expect(await screen.findByRole('heading', { name: 'Eventos', level: 2 })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /Criar evento/ }).length).toBeGreaterThan(0)
  })

  it('sends who is not signed in as a member to the sign-in', () => {
    render(
      <MemoryRouter initialEntries={['/organization/events']}>
        <App now={now} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('heading', { name: /Entrar/ })).toBeInTheDocument()
  })
})

describe('O5–O7 live operations', () => {
  it('publishes the crowd level in Core and manual queue times', async () => {
    const user = userEvent.setup()
    const { events } = await renderAt('/organization/live/crowd')
    expect(screen.getByText('Ainda não publicaste a lotação deste evento.')).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: /Elevada/ }))
    await user.click(screen.getByRole('button', { name: 'Publicar lotação' }))
    expect(await screen.findByText('Lotação publicada: Elevada.')).toBeInTheDocument()
    expect(events.calls).toContain('publishCrowd')
    expect(events.events[0].crowd?.level).toBe('high')
    expect(screen.getByText(/^Publicado por Ribeira Live/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Aumentar espera em Bar principal' }))
    expect(
      within(screen.getByRole('group', { name: 'espera em Bar principal' })).getByText('6 min'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Publicar filas' }))
    expect(screen.getByText('Filas publicadas.')).toBeInTheDocument()
  })

  it('shows the level Core has and why Core refused a new one', async () => {
    const user = userEvent.setup()
    const events = fakeEventsApi(
      demoEvents().map((e) => ({
        ...e,
        crowd: { level: 'full' as const, publishedBy: 'Ribeira Live', publishedAt: now },
      })),
    )
    await renderAt('/organization/live/crowd', events)
    expect(screen.getByRole('radio', { name: /Lotado/ })).toBeChecked()
    expect(screen.getByText('Publicado por Ribeira Live agora')).toBeInTheDocument()

    events.refuse = 'EVENT_CLOSED'
    await user.click(screen.getByRole('button', { name: 'Publicar lotação' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este evento já foi cancelado ou terminou.',
    )
    expect(screen.queryByText(/Lotação publicada/)).not.toBeInTheDocument()
  })

  it('drafts an alert with AI and sends it', async () => {
    const user = userEvent.setup()
    await renderAt('/organization/alerts')

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

  it('shows the live dashboard from the shared state', async () => {
    await renderAt('/organization/live')
    expect(screen.getByText('Porta C · 25 min')).toBeInTheDocument()
    expect(screen.getByText('1 urgente')).toBeInTheDocument()
  })
})

describe('O8–O9 incidents and per-event confirmations', () => {
  it('resolves an incident and updates the open count', async () => {
    const user = userEvent.setup()
    await renderAt('/organization/incidents/fila-porta-c')
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
    await renderAt('/organization/incidents')

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

  it('never asks confirmation for personal categories', async () => {
    await renderAt('/organization/events/festival-margens-2026/edit')
    expect(
      screen.getByRole('switch', { name: 'Pedir confirmação para Perdidos e achados' }),
    ).toBeDisabled()
    expect(screen.getByRole('switch', { name: 'Pedir confirmação para Fila' })).toBeChecked()
  })
})

describe('O12 requests', () => {
  it('previews and publishes an urgent request', async () => {
    const user = userEvent.setup()
    await renderAt('/organization/requests')

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
