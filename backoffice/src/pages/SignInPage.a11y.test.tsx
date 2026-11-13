import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from '../App'
import type { ChoosePassword, SignIn } from '../data/auth'

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

const refused: SignIn = () =>
  Promise.resolve({
    kind: 'error',
    title: 'Não foi possível entrar',
    message: 'O email ou a palavra-passe não estão certos.',
  })

describe('C1 sign-in accessibility', () => {
  it('has no WCAG 2.1 AA violations, before and after a refused sign-in', async () => {
    const user = userEvent.setup()
    const { container } = render(
      <MemoryRouter initialEntries={['/entrar']}>
        <App now={new Date(2026, 10, 12)} signIn={refused} />
      </MemoryRouter>,
    )
    expect(await violations(container)).toEqual([])

    await user.type(screen.getByLabelText('Email'), 'equipa@ribeira-live.test')
    await user.type(screen.getByLabelText('Palavra-passe'), 'errada')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    await screen.findByRole('alert')

    expect(await violations(container)).toEqual([])
  })

  it('can be filled in and sent with the keyboard alone', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/entrar']}>
        <App now={new Date(2026, 10, 12)} signIn={refused} />
      </MemoryRouter>,
    )

    await user.tab()
    expect(screen.getByLabelText('Email')).toHaveFocus()
    await user.keyboard('equipa@ribeira-live.test')
    await user.tab()
    expect(screen.getByLabelText('Palavra-passe')).toHaveFocus()
    await user.keyboard('errada{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível entrar')
  })
})

const temporary: SignIn = () => Promise.resolve({ kind: 'new-password' })
const choosePassword: ChoosePassword = () =>
  Promise.resolve({
    kind: 'error',
    title: 'Não foi possível guardar',
    message: 'Demasiadas tentativas. Tenta novamente daqui a alguns minutos.',
  })

describe('C4 new password accessibility', () => {
  it('has no WCAG 2.1 AA violations, with the strength meter and a refusal', async () => {
    const user = userEvent.setup()
    const { container } = render(
      <MemoryRouter initialEntries={['/entrar']}>
        <App now={new Date(2026, 10, 12)} signIn={temporary} choosePassword={choosePassword} />
      </MemoryRouter>,
    )
    await user.type(screen.getByLabelText('Email'), 'novo@ribeira-live.test')
    await user.type(screen.getByLabelText('Palavra-passe'), 'T7q-mK4-v9Px') // ggignore
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    await screen.findByRole('heading', { name: 'Define a tua nova palavra-passe' })
    expect(await violations(container)).toEqual([])

    await user.type(screen.getByLabelText('Nova palavra-passe'), 'Margens-1nverno') // ggignore
    expect(screen.getByLabelText('Nova palavra-passe')).toHaveAccessibleDescription(
      /A palavra-passe deve conter/,
    )
    await user.type(screen.getByLabelText('Confirmar palavra-passe'), 'Margens-1nverno') // ggignore
    await user.keyboard('{Enter}')
    await screen.findByRole('alert')
    expect(await violations(container)).toEqual([])
  })
})
