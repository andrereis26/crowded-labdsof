import { useState } from 'react'
import { Navigate, Route, Routes, useLocation, useMatch, useNavigate } from 'react-router'
import {
  demoAlerts,
  demoConfirmationSettings,
  demoEvents,
  demoIncidents,
  demoLive,
  demoPolls,
  type CatalogEvent,
  type ConfirmationSettings,
  type Incident,
  type LiveState,
  type OrgEvent,
  type Poll,
  type SentAlert,
} from './data/organizer'
import { OrganizerLayout, type OrganizerScope } from './layout/OrganizerLayout'
import { AlertPage } from './pages/organizer/AlertPage'
import { ConfirmationsPage } from './pages/organizer/ConfirmationsPage'
import { CrowdPage } from './pages/organizer/CrowdPage'
import { EventDetailPage } from './pages/organizer/EventDetailPage'
import { EventFormPage } from './pages/organizer/EventFormPage'
import { EventsPage } from './pages/organizer/EventsPage'
import { IncidentDetailPage } from './pages/organizer/IncidentDetailPage'
import { IncidentsPage } from './pages/organizer/IncidentsPage'
import { LivePage } from './pages/organizer/LivePage'
import { ManagementRequestPage } from './pages/organizer/ManagementRequestPage'
import { MorePage } from './pages/organizer/MorePage'
import { PollsPage } from './pages/organizer/PollsPage'
import { RequestPage } from './pages/organizer/RequestPage'
import { SummaryPage } from './pages/organizer/SummaryPage'
import { Panel } from './ui/components'

const titles: [string, string][] = [
  ['/organizacao/ao-vivo/lotacao', 'Lotação e filas'],
  ['/organizacao/ao-vivo', 'Ao vivo'],
  ['/organizacao/incidentes', 'Incidentes'],
  ['/organizacao/alertas', 'Alertas'],
  ['/organizacao/sondagens', 'Sondagens'],
  ['/organizacao/pedidos', 'Pedidos'],
  ['/organizacao/definicoes', 'Definições'],
  ['/organizacao/api', 'API'],
  ['/organizacao/mais', 'Mais'],
]

/** Organizer area: owns the demo state shared by O2–O14. */
export function OrganizerApp({
  now,
  onManagementRequest,
}: {
  now: Date
  /** Sends a management request to the administrators' queue (A1). */
  onManagementRequest: (
    event: CatalogEvent,
    relation: string,
    document: { name: string; size: number },
  ) => void
}) {
  const [events, setEvents] = useState<OrgEvent[]>(demoEvents)
  const [live, setLive] = useState<Record<string, LiveState>>(() => demoLive(now))
  const [incidents, setIncidents] = useState<Incident[]>(() => demoIncidents(now))
  const [alerts, setAlerts] = useState<SentAlert[]>(() => demoAlerts(now))
  const [polls, setPolls] = useState<Poll[]>(demoPolls)
  const [settings, setSettings] = useState<ConfirmationSettings>(demoConfirmationSettings)
  const location = useLocation()
  const navigate = useNavigate()

  // Opening an event page makes it the current event of the top bar, and it
  // stays current on the other pages (adjusting state while rendering).
  const eventMatch = useMatch('/organizacao/eventos/:id/*')
  const routeEventId = eventMatch?.params.id
  const isEvent = (id?: string): id is string => !!id && events.some((e) => e.id === id)
  const [currentId, setCurrentId] = useState(
    isEvent(routeEventId) ? routeEventId : 'festival-margens-2026',
  )
  const [seenRouteId, setSeenRouteId] = useState(routeEventId)
  if (routeEventId !== seenRouteId) {
    setSeenRouteId(routeEventId)
    if (isEvent(routeEventId)) setCurrentId(routeEventId)
  }

  const current = events.find((e) => e.id === currentId) ?? events[0]
  const eventIncidents = incidents.filter((i) => i.eventId === current.id)
  const openIncidents = eventIncidents.filter((i) => i.status !== 'resolved').length

  const path = location.pathname
  const onEventPage = isEvent(routeEventId)
  const scope: OrganizerScope =
    path.startsWith('/organizacao/eventos') && !onEventPage
      ? { kind: 'organization', title: 'Eventos' }
      : {
          kind: 'event',
          title: onEventPage
            ? current.name
            : (titles.find(([p]) => path.startsWith(p))?.[1] ?? 'Crowded'),
        }
  const focused =
    path === '/organizacao/eventos/novo' ||
    path.endsWith('/editar') ||
    path === '/organizacao/eventos/pedir-gestao' ||
    path === '/organizacao/pedidos'

  const selectEvent = (id: string) => {
    setCurrentId(id)
    if (onEventPage) navigate(path.replace(`/eventos/${routeEventId}`, `/eventos/${id}`))
  }

  const saveEvent = (event: OrgEvent) =>
    setEvents((all) =>
      all.some((e) => e.id === event.id)
        ? all.map((e) => (e.id === event.id ? event : e))
        : [event, ...all],
    )

  const updateIncident = (updated: Incident) =>
    setIncidents((all) => all.map((i) => (i.id === updated.id ? updated : i)))

  return (
    <OrganizerLayout
      scope={scope}
      focused={focused}
      events={events}
      currentEvent={current}
      openIncidents={openIncidents}
      now={now}
      onSelectEvent={selectEvent}
    >
      <Routes>
        <Route index element={<Navigate to="eventos" replace />} />
        <Route path="eventos" element={<EventsPage events={events} alerts={alerts} now={now} />} />
        <Route path="eventos/novo" element={<EventFormPage events={events} onSave={saveEvent} />} />
        <Route
          path="eventos/pedir-gestao"
          element={<ManagementRequestPage now={now} onSubmit={onManagementRequest} />}
        />
        <Route
          path="eventos/:id"
          element={<EventDetailPage events={events} now={now} onSave={saveEvent} />}
        />
        <Route
          path="eventos/:id/editar"
          element={<EventFormPage events={events} onSave={saveEvent} />}
        />
        <Route path="eventos/:id/resumo" element={<SummaryPage events={events} />} />
        <Route
          path="ao-vivo"
          element={
            <LivePage
              event={current}
              live={live[current.id]}
              incidents={eventIncidents}
              now={now}
            />
          }
        />
        <Route
          path="ao-vivo/lotacao"
          element={
            <CrowdPage
              event={current}
              live={live[current.id]}
              now={now}
              onChange={(state) => setLive((all) => ({ ...all, [current.id]: state }))}
            />
          }
        />
        <Route path="incidentes" element={<IncidentsPage incidents={eventIncidents} now={now} />} />
        <Route
          path="incidentes/:id"
          element={<IncidentDetailPage incidents={incidents} now={now} onChange={updateIncident} />}
        />
        <Route
          path="alertas"
          element={
            <AlertPage
              event={current}
              alerts={alerts.filter((a) => a.eventId === current.id)}
              now={now}
              onSend={(alert) => setAlerts((all) => [alert, ...all])}
            />
          }
        />
        <Route
          path="sondagens"
          element={
            <PollsPage
              event={current}
              polls={polls.filter((p) => p.eventId === current.id)}
              now={now}
              onPublish={(poll) => setPolls((all) => [poll, ...all])}
            />
          }
        />
        <Route path="pedidos" element={<RequestPage event={current} />} />
        <Route
          path="definicoes"
          element={<ConfirmationsPage event={current} settings={settings} onSave={setSettings} />}
        />
        <Route path="mais" element={<MorePage />} />
        <Route
          path="*"
          element={
            <Panel title={scope.title}>
              <p className="text-sm text-ink-secondary">Disponível em breve.</p>
            </Panel>
          }
        />
      </Routes>
    </OrganizerLayout>
  )
}
