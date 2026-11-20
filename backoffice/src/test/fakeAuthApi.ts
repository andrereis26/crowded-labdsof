import type { AuthApi, Session } from '../data/auth'

/** Core's session, without a network: `current` is who `GET /me` answers, and
 * each sign-out is recorded. */
export function fakeAuthApi(current?: Session) {
  const signOuts: number[] = []
  const api: AuthApi = {
    currentSession: () => Promise.resolve(current),
    refresh: () => Promise.resolve('ended'),
    signOut: () => {
      signOuts.push(signOuts.length + 1)
      return Promise.resolve()
    },
  }
  return { api, signOuts }
}
