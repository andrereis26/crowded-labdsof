import { createContext, useContext } from 'react'
import type { Session } from '../data/auth'

export type AuthState =
  { status: 'loading' } | { status: 'anonymous' } | { status: 'signed-in'; session: Session }

export interface Auth {
  state: AuthState
  /** After sign-in: the session Core started. */
  signedIn: (session: Session) => void
  /** Ends the session with Core and forgets it here. */
  signOut: () => Promise<void>
  /** `fetch` for Core's API, which refreshes the session when its token expires
   * and signs the user out when the session has ended. */
  fetch: typeof fetch
}

/** The signed-in session, which {@link AuthProvider} gives the app. */
export const AuthContext = createContext<Auth | undefined>(undefined)

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth needs an AuthProvider')
  return auth
}
