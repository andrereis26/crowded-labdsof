import { useState } from 'react'
import { Navigate, Route, Routes, useLocation, useParams } from 'react-router'
import { organization } from './data/organizer'
import {
  demoRequests,
  isOpen,
  managementRequest,
  registrationRequest,
  type VerificationRequest,
} from './data/verifications'
import { AdminLayout } from './layout/AdminLayout'
import { OrganizerApp } from './OrganizerApp'
import { RegisterPage, RegistrationPendingPage } from './pages/organizer/RegisterPage'
import { Panel } from './ui/components'
import { VerificationsPage } from './pages/VerificationsPage'

const titles: Record<string, string> = {
  '/organizacoes': 'Organizações',
  '/pedidos-de-gestao': 'Pedidos de gestão',
  '/auditoria': 'Auditoria',
  '/definicoes': 'Definições',
}

/** `now` is fixed at start-up so demo times stay stable between renders. */
export default function App({ now }: { now: Date }) {
  const [requests, setRequests] = useState<VerificationRequest[]>(() => demoRequests(now))

  const update = (updated: VerificationRequest) =>
    setRequests((all) => all.map((r) => (r.id === updated.id ? updated : r)))
  // Registrations (O1) and management requests (O13) reach the admin queue.
  const add = (request: VerificationRequest) => setRequests((all) => [request, ...all])

  return (
    <Routes>
      <Route
        path="/registo"
        element={<RegisterPage onSubmit={(form) => add(registrationRequest(form, new Date()))} />}
      />
      <Route path="/registo/pendente" element={<RegistrationPendingPage />} />
      <Route
        path="/organizacao/*"
        element={
          <OrganizerApp
            now={now}
            onManagementRequest={(event, relation, document) =>
              add(
                managementRequest(
                  { ...organization, user: organization.user.name },
                  event.name,
                  relation,
                  document,
                  new Date(),
                ),
              )
            }
          />
        }
      />
      <Route path="*" element={<AdminArea requests={requests} now={now} onChange={update} />} />
    </Routes>
  )
}

function AdminArea({
  requests,
  now,
  onChange,
}: {
  requests: VerificationRequest[]
  now: Date
  onChange: (updated: VerificationRequest) => void
}) {
  const location = useLocation()
  const open = requests.filter(isOpen)
  const title = location.pathname.startsWith('/verificacoes')
    ? 'Verificações'
    : (titles[location.pathname] ?? 'Crowded')

  const verifications = <Verifications requests={requests} now={now} onChange={onChange} />

  return (
    <AdminLayout
      title={title}
      pendingVerifications={open.length}
      pendingManagement={open.filter((r) => r.kind === 'management').length}
    >
      <Routes>
        <Route path="/" element={<Navigate to="/verificacoes" replace />} />
        <Route path="/verificacoes" element={verifications} />
        <Route path="/verificacoes/:id" element={verifications} />
        <Route
          path="*"
          element={
            <Panel title={title}>
              <p className="text-sm text-ink-secondary">Disponível em breve.</p>
            </Panel>
          }
        />
      </Routes>
    </AdminLayout>
  )
}

function Verifications(props: {
  requests: VerificationRequest[]
  now: Date
  onChange: (updated: VerificationRequest) => void
}) {
  const { id } = useParams()
  return <VerificationsPage {...props} selectedId={id} />
}
