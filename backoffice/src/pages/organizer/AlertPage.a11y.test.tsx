import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { Session } from '../../data/auth'
import { fakeEventsApi, fakeGeocoder } from '../../test/fakeEventsApi'
import { AppWithSession } from '../../test/renderApp'

/** WCAG 2.1 A and AA rules of axe. Colour contrast needs a real layout, which jsdom has not:
 * it is checked in the browser instead. */
async function violations(container: HTMLElement) {
  const result = await axe.run(container, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } },
  })
  return result.violations.map(
    (v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`,
  )
}

const member: Session = { userId: 'u-1', role: 'ORGANIZATION_MEMBER', organizationId: 'org-1' }

describe('O6 send an alert accessibility', () => {
  it('has no WCAG 2.1 AA violations, empty, refused for missing fields and sent', async () => {
    const user = userEvent.setup()
    const events = fakeEventsApi()
    const { container } = render(
      <MemoryRouter initialEntries={['/organization/alerts']}>
        <AppWithSession
          now={new Date(2026, 9, 17, 22, 47)}
          session={member}
          eventsApiFor={() => events.api}
          geocoder={fakeGeocoder}
        />
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Novo alerta' })).toBeVisible())
    expect(await violations(container)).toEqual([])

    await user.click(screen.getByRole('button', { name: 'Enviar alerta' }))
    expect(screen.getByRole('radiogroup', { name: 'Tipo de alerta' })).toHaveAccessibleDescription(
      'Escolhe o tipo de alerta.',
    )
    expect(await violations(container)).toEqual([])

    await user.click(screen.getByRole('radio', { name: /Outro/ }))
    await user.type(screen.getByLabelText('Mensagem'), 'Água grátis no bar central.')
    await user.keyboard('{Tab}{Enter}')
    await screen.findByText(/Alerta enviado a/)
    expect(await violations(container)).toEqual([])
  })
})
