import { useCallback, useEffect, useMemo, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { type AdminOrganization, type Member } from './data/admin'
import { AdminApiError, httpAdminApi, type AdminApi, type MemberChange } from './data/adminApi'
import { httpEventsApi, type EventsApi } from './data/eventsApi'
import { photonGeocoder, type Geocoder } from './data/geocoding'
import {
  choosePasswordWithCore,
  signInWithCore,
  type ChoosePassword,
  type Session,
  type SignIn,
} from './data/auth'
import { AdminLayout } from './layout/AdminLayout'
import { OrganizerApp } from './OrganizerApp'
import { OrganizationsPage, type Outcome } from './pages/admin/OrganizationsPage'
import { NewPasswordPage, SignInPage } from './pages/SignInPage'
import { Panel } from './ui/components'

/** One geocoder for the whole app, so the map service's rate limit holds. */
const defaultGeocoder = photonGeocoder()

function codeOf(error: unknown): string {
  return error instanceof AdminApiError ? error.code : 'UNKNOWN'
}

/** `now` is fixed at start-up so demo times stay stable between renders.
 * Sign-in, the platform administration (A2) and the organization's events
 * (O2–O4) go to Core; the other organizer pages still show demo data. */
export default function App({
  now,
  signIn = signInWithCore,
  choosePassword = choosePasswordWithCore,
  adminApiFor = httpAdminApi,
  eventsApiFor = httpEventsApi,
  geocoder = defaultGeocoder,
  initialSession,
}: {
  now: Date
  signIn?: SignIn
  choosePassword?: ChoosePassword
  adminApiFor?: (accessToken: string) => AdminApi
  eventsApiFor?: (accessToken: string) => EventsApi
  geocoder?: Geocoder
  /** A session to start with, for tests. */
  initialSession?: Session
}) {
  const [session, setSession] = useState<Session | undefined>(initialSession)
  const [organizations, setOrganizations] = useState<AdminOrganization[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [loadError, setLoadError] = useState('')
  // The temporary password C4 replaces, in memory only, never in the URL or storage.
  const [pending, setPending] = useState<{ email: string; temporaryPassword: string }>()
  const administrator = session?.role === 'ADMINISTRATOR'
  const api = useMemo(
    () => (session && administrator ? adminApiFor(session.accessToken) : undefined),
    [session, administrator, adminApiFor],
  )
  const member = session?.role === 'ORGANIZATION_MEMBER'
  const eventsApi = useMemo(
    () => (session && member ? eventsApiFor(session.accessToken) : undefined),
    [session, member, eventsApiFor],
  )

  const show = useCallback((data: Awaited<ReturnType<AdminApi['list']>>) => {
    setOrganizations(data.organizations)
    setMembers(data.members)
    setLoadError('')
  }, [])
  const loadFailed = () =>
    setLoadError('Não foi possível carregar as organizações. Tenta novamente.')

  // Loads the organizations from Core when an administrator signs in.
  useEffect(() => {
    if (!api) return
    let current = true
    api.list().then(
      (data) => current && show(data),
      () => current && loadFailed(),
    )
    return () => {
      current = false
    }
  }, [api, show])

  /** After a change: the list as Core has it now. */
  const reload = async () => {
    try {
      show(await api!.list())
    } catch {
      loadFailed()
    }
  }

  const createOrganization = async (name: string): Promise<Outcome<string>> => {
    try {
      const id = await api!.createOrganization(name)
      await reload()
      return { ok: true, value: id }
    } catch (e) {
      return { ok: false, code: codeOf(e) }
    }
  }

  const createMember = async (
    organizationId: string,
    name: string,
    email: string,
  ): Promise<Outcome<Member>> => {
    try {
      const issued = await api!.addMember(organizationId, name, email)
      await reload()
      return {
        ok: true,
        value: {
          id: issued.userId,
          organizationId,
          name,
          email,
          status: 'temporary',
          temporaryPassword: issued.temporaryPassword,
          temporaryExpiresAt: issued.expiresAt,
        },
      }
    } catch (e) {
      return { ok: false, code: codeOf(e) }
    }
  }

  const changeMember = async (member: Member, change: MemberChange): Promise<Outcome<Member>> => {
    try {
      const changed = await api!.changeMember(member.id, change)
      await reload()
      const temporary = changed.temporaryPassword !== undefined
      return {
        ok: true,
        value: {
          ...member,
          status: !changed.active
            ? 'disabled'
            : temporary || member.status === 'temporary'
              ? 'temporary'
              : 'active',
          temporaryPassword: changed.temporaryPassword,
          temporaryExpiresAt: changed.expiresAt ?? member.temporaryExpiresAt,
        },
      }
    } catch (e) {
      return { ok: false, code: codeOf(e) }
    }
  }

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/sign-in" replace />} />
      <Route
        path="/sign-in"
        element={
          <SignInPage
            signIn={signIn}
            onSignedIn={setSession}
            onTemporaryPassword={(email, temporaryPassword) =>
              setPending({ email, temporaryPassword })
            }
          />
        }
      />
      <Route
        path="/new-password"
        element={
          <NewPasswordPage
            pending={pending}
            choosePassword={choosePassword}
            onSignedIn={(signedIn) => {
              setPending(undefined)
              setSession(signedIn)
            }}
          />
        }
      />
      <Route
        path="/organization/*"
        element={
          eventsApi ? (
            <OrganizerApp now={now} api={eventsApi} geocode={geocoder} />
          ) : (
            <Navigate to="/sign-in" replace />
          )
        }
      />
      <Route
        path="*"
        element={
          administrator ? (
            <AdminLayout>
              <Routes>
                <Route
                  path="/organizations"
                  element={
                    <>
                      {loadError && (
                        <p role="alert" className="mb-3 text-sm text-danger">
                          {loadError}
                        </p>
                      )}
                      <OrganizationsPage
                        organizations={organizations}
                        members={members}
                        now={now}
                        onCreateOrganization={createOrganization}
                        onCreateMember={createMember}
                        onChangeMember={changeMember}
                      />
                    </>
                  }
                />
                <Route
                  path="*"
                  element={
                    <Panel title="Administração">
                      <p className="text-sm text-ink-secondary">Disponível em breve.</p>
                    </Panel>
                  }
                />
              </Routes>
            </AdminLayout>
          ) : (
            <Navigate to="/sign-in" replace />
          )
        }
      />
    </Routes>
  )
}
