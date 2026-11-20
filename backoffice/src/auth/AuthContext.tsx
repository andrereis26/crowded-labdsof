import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { sessionFetch, type AuthApi, type Session } from '../data/auth'
import { AuthContext, type Auth, type AuthState } from './useAuth'

/** Who is signed in, for the whole backoffice (ADR 0021). It asks Core at
 * start-up (`GET /me`), so a reload or a new tab keeps the session. */
export function AuthProvider({
  auth,
  initialSession,
  fetchImpl = fetch,
  children,
}: {
  auth: AuthApi
  /** For tests: the session to start with, or `null` for none; Core is not
   * asked either way. */
  initialSession?: Session | null
  fetchImpl?: typeof fetch
  children: ReactNode
}) {
  const [state, setState] = useState<AuthState>(
    initialSession
      ? { status: 'signed-in', session: initialSession }
      : initialSession === null
        ? { status: 'anonymous' }
        : { status: 'loading' },
  )

  useEffect(() => {
    if (initialSession !== undefined) return
    let current = true
    auth.currentSession().then((session) => {
      if (current) setState(session ? { status: 'signed-in', session } : { status: 'anonymous' })
    })
    return () => {
      current = false
    }
  }, [auth, initialSession])

  // One fetch for the whole session, so the pages keep their data while it refreshes.
  const apiFetch = useMemo(
    () =>
      sessionFetch(
        auth,
        () =>
          setState((before) => (before.status === 'signed-in' ? { status: 'anonymous' } : before)),
        fetchImpl,
      ),
    [auth, fetchImpl],
  )

  const value = useMemo<Auth>(
    () => ({
      state,
      signedIn: (session) => setState({ status: 'signed-in', session }),
      signOut: async () => {
        await auth.signOut()
        setState({ status: 'anonymous' })
      },
      fetch: apiFetch,
    }),
    [state, auth, apiFetch],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
