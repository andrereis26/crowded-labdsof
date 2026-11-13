import { useState } from 'react'
import { Navigate, Route, Routes, useLocation, useMatch, useNavigate } from 'react-router'
import {
  demoAlerts,
  demoConfirmationSettings,
  demoEvents,
  demoIncidents,
  demoLive,
  type ConfirmationSettings,
  type Incident,
  type LiveState,
  type OrgEvent,
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
import { MorePage } from './pages/organizer/MorePage'
import { RequestPage } from './pages/organizer/RequestPage'
import { Panel } from './ui/components'

const titles: [string, string][] = [
  ['/organization/live/crowd', 'Lotação e filas'],
  ['/organization/live', 'Ao vivo'],
  ['/organization/incidents', 'Incidentes'],
  ['/organization/alerts', 'Alertas'],
  ['/organization/requests', 'Pedidos'],
  ['/organization/settings', 'Definições'],
  ['/organization/more', 'Mais'],
]

/** Organizer area: owns the demo state shared by O2–O12. */
export function OrganizerApp({ now }: { now: Date }) {
  const [events, setEvents] = useState<OrgEvent[]>(demoEvents)
  const [live, setLive] = useState<Record<string, LiveState>>(() => demoLive(now))
  const [incidents, setIncidents] = useState<Incident[]>(() => demoIncidents(now))
  const [alerts, setAlerts] = useState<SentAlert[]>(() => demoAlerts(now))
  const [settings, setSettings] = useState<ConfirmationSettings>(demoConfirmationSettings)
  const location = useLocation()
  const navigate = useNavigate()

  // Opening an event page makes it the current event of the top bar, and it
  // stays current on the other pages (adjusting state while rendering).
  const eventMatch = useMatch('/organization/events/:id/*')
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
    path.startsWith('/organization/events') && !onEventPage
      ? { kind: 'organization', title: 'Eventos' }
      : {
          kind: 'event',
          title: onEventPage
            ? current.name
            : (titles.find(([p]) => path.startsWith(p))?.[1] ?? 'Crowded'),
        }
  const focused =
    path === '/organization/events/new' ||
    path.endsWith('/edit') ||
    path === '/organization/requests'

  const selectEvent = (id: string) => {
    setCurrentId(id)
    if (onEventPage) navigate(path.replace(`/events/${routeEventId}`, `/events/${id}`))
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
        <Route index element={<Navigate to="events" replace />} />
        <Route path="events" element={<EventsPage events={events} alerts={alerts} now={now} />} />
        <Route path="events/new" element={<EventFormPage events={events} onSave={saveEvent} />} />
        <Route
          path="events/:id"
          element={<EventDetailPage events={events} now={now} onSave={saveEvent} />}
        />
        <Route
          path="events/:id/edit"
          element={<EventFormPage events={events} onSave={saveEvent} />}
        />
        <Route
          path="live"
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
          path="live/crowd"
          element={
            <CrowdPage
              event={current}
              live={live[current.id]}
              now={now}
              onChange={(state) => setLive((all) => ({ ...all, [current.id]: state }))}
            />
          }
        />
        <Route path="incidents" element={<IncidentsPage incidents={eventIncidents} now={now} />} />
        <Route
          path="incidents/:id"
          element={<IncidentDetailPage incidents={incidents} now={now} onChange={updateIncident} />}
        />
        <Route
          path="alerts"
          element={
            <AlertPage
              event={current}
              alerts={alerts.filter((a) => a.eventId === current.id)}
              now={now}
              onSend={(alert) => setAlerts((all) => [alert, ...all])}
            />
          }
        />
        <Route path="requests" element={<RequestPage event={current} />} />
        <Route
          path="settings"
          element={<ConfirmationsPage event={current} settings={settings} onSave={setSettings} />}
        />
        <Route path="more" element={<MorePage />} />
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
