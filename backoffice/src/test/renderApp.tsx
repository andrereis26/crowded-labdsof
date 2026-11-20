import type { ComponentProps } from 'react'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import type { AuthApi, Session } from '../data/auth'
import { fakeAuthApi } from './fakeAuthApi'

/** The app as main.tsx mounts it, signed in as `session` (or signed out). */
export function AppWithSession({
  session,
  auth = fakeAuthApi().api,
  ...props
}: ComponentProps<typeof App> & { session?: Session; auth?: AuthApi }) {
  return (
    <AuthProvider auth={auth} initialSession={session ?? null}>
      <App {...props} />
    </AuthProvider>
  )
}
