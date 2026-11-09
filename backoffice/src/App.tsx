import { useState } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { demoMembers, demoOrganizations, type AdminOrganization, type Member } from './data/admin'
import { AdminLayout } from './layout/AdminLayout'
import { OrganizerApp } from './OrganizerApp'
import { OrganizationsPage } from './pages/admin/OrganizationsPage'
import { NewPasswordPage, SignInPage } from './pages/SignInPage'
import { Panel } from './ui/components'

/** `now` is fixed at start-up so demo times stay stable between renders. */
export default function App({ now }: { now: Date }) {
  const [organizations, setOrganizations] = useState<AdminOrganization[]>(demoOrganizations)
  const [members, setMembers] = useState<Member[]>(() => demoMembers(now))

  const updateMember = (updated: Member) =>
    setMembers((all) => all.map((m) => (m.id === updated.id ? updated : m)))

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/entrar" replace />} />
      <Route path="/entrar" element={<SignInPage members={members} now={now} />} />
      <Route
        path="/definir-palavra-passe"
        element={<NewPasswordPage members={members} now={now} onChange={updateMember} />}
      />
      <Route path="/organizacao/*" element={<OrganizerApp now={now} />} />
      <Route
        path="*"
        element={
          <AdminLayout>
            <Routes>
              <Route
                path="/organizacoes"
                element={
                  <OrganizationsPage
                    organizations={organizations}
                    members={members}
                    now={now}
                    onAddOrganization={(o) => setOrganizations((all) => [...all, o])}
                    onAddMember={(m) => setMembers((all) => [...all, m])}
                    onChangeMember={updateMember}
                  />
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
        }
      />
    </Routes>
  )
}
