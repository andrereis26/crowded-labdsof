import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { homeOf, participantAccount, type Session } from '../data/auth'
import { Logo } from '../layout/Shell'
import { Button } from '../ui/components'
import { useAuth } from './useAuth'

/** Shows `children` only to a signed-in user with `role`. The others go to
 * the sign-in page or their own area; a participant is told the account is for
 * the app. This only decides what to show: Core checks every request. */
export function RequireRole({ role, children }: { role: Session['role']; children: ReactNode }) {
  const { state, signOut } = useAuth()
  if (state.status === 'loading') return <SessionLoading />
  if (state.status === 'anonymous') return <Navigate to="/sign-in" replace />
  if (state.session.role === role) return children
  if (state.session.role === 'PARTICIPANT') return <ParticipantAccount onSignOut={signOut} />
  return <Navigate to={homeOf(state.session.role)} replace />
}

/** While Core says who is signed in, so the sign-in page does not flash. */
export function SessionLoading() {
  return (
    <main className="flex min-h-screen items-center justify-center">
      <p role="status" className="text-sm text-ink-secondary">
        A carregar…
      </p>
    </main>
  )
}

function ParticipantAccount({ onSignOut }: { onSignOut: () => Promise<void> }) {
  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-4 p-6">
      <Logo dark />
      <h1 className="text-xl font-semibold">Conta de participante</h1>
      <p className="text-sm text-ink-secondary">{participantAccount}</p>
      <Button variant="secondary" onClick={() => void onSignOut()}>
        Terminar sessão
      </Button>
    </main>
  )
}
