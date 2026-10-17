import { useState } from 'react'
import { Navigate, Route, Routes, useLocation, useParams } from 'react-router'
import { demoRequests, isOpen, type VerificationRequest } from './data/verifications'
import { AdminLayout } from './layout/AdminLayout'
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
  const [requests, setRequests] = useState<VerificationRequest[]>(() =>
    demoRequests(now),
  )
  const location = useLocation()

  const update = (updated: VerificationRequest) =>
    setRequests((all) => all.map((r) => (r.id === updated.id ? updated : r)))

  const open = requests.filter(isOpen)
  const title = location.pathname.startsWith('/verificacoes')
    ? 'Verificações'
    : (titles[location.pathname] ?? 'Crowded')

  const verifications = (
    <Verifications requests={requests} now={now} onChange={update} />
  )

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
