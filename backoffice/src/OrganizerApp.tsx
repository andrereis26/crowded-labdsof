import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation, useMatch, useNavigate } from 'react-router'
import type { EventsApi } from './data/eventsApi'
import type { CrowdLevel } from './ui/styles'
import type { Geocoder } from './data/geocoding'
import {
  demoAlerts,
  demoIncidents,
  demoLive,
  type EventChange,
  type Incident,
  type LiveState,
  type OrgEvent,
  type SentAlert,
} from './data/organizer'
import { OrganizerLayout, type OrganizerScope } from './layout/OrganizerLayout'
import { AlertPage } from './pages/organizer/AlertPage'
import { CrowdPage } from './pages/organizer/CrowdPage'
import { EventDetailPage } from './pages/organizer/EventDetailPage'
import { EventFormPage } from './pages/organizer/EventFormPage'
import { EventsPage } from './pages/organizer/EventsPage'
import { IncidentDetailPage } from './pages/organizer/IncidentDetailPage'
import { IncidentsPage } from './pages/organizer/IncidentsPage'
import { LivePage } from './pages/organizer/LivePage'
import { MorePage } from './pages/organizer/MorePage'
import { RequestPage } from './pages/organizer/RequestPage'
import { Button, Panel } from './ui/components'

const titles: [string, string][] = [
  ['/organization/live/crowd', 'Lotação e filas'],
  ['/organization/live', 'Ao vivo'],
  ['/organization/incidents', 'Incidentes'],
  ['/organization/alerts', 'Alertas'],
  ['/organization/requests', 'Pedidos'],
  ['/organization/more', 'Mais'],
]

/** The demo event whose live state, incidents and alerts stand in for any
 * event until those pages read Core. */
const DEMO_EVENT = 'festival-margens-2026'

/** Which upload of the event's image to show; none for a demo asset. */
const imageKey = (e: OrgEvent) => (e.imageUpdatedAt ? `${e.id}@${e.imageUpdatedAt}` : undefined)

/** Organizer area: the member's events come from Core; the live operations,
 * incidents and alerts of O5–O12 are still demo state. */
export function OrganizerApp({
  now,
  api,
  geocode,
  onSignOut,
}: {
  now: Date
  api: EventsApi
  geocode: Geocoder
  onSignOut: () => void
}) {
  const [loaded, setLoaded] = useState<OrgEvent[]>()
  const [loadError, setLoadError] = useState(false)
  const [live, setLive] = useState<Record<string, LiveState>>(() => demoLive(now))
  const [incidents, setIncidents] = useState<Incident[]>(() => demoIncidents(now))
  const [alerts, setAlerts] = useState<SentAlert[]>(() => demoAlerts(now))
  // Images fetched from Core, by event and upload time, as object URLs.
  const [imageUrls, setImageUrls] = useState<Record<string, string>>({})
  const location = useLocation()
  const navigate = useNavigate()

  const load = useCallback(async () => {
    try {
      setLoaded(await api.list())
      setLoadError(false)
    } catch {
      setLoadError(true)
    }
  }, [api])

  useEffect(() => {
    let current = true
    api.list().then(
      (found) => {
        if (!current) return
        setLoaded(found)
        setLoadError(false)
      },
      () => current && setLoadError(true),
    )
    return () => {
      current = false
    }
  }, [api])

  // Fetches each event's image once per upload; a failure leaves its gradient.
  useEffect(() => {
    let current = true
    for (const e of loaded ?? []) {
      const key = imageKey(e)
      if (!key || imageUrls[key]) continue
      api.image(e.id).then(
        (blob) => current && setImageUrls((all) => ({ ...all, [key]: URL.createObjectURL(blob) })),
        () => undefined,
      )
    }
    return () => {
      current = false
    }
  }, [api, loaded, imageUrls])

  // Opening an event page makes it the current event of the top bar, and it
  // stays current on the other pages (adjusting state while rendering).
  const eventMatch = useMatch('/organization/events/:id/*')
  const routeEventId = eventMatch?.params.id
  const isEvent = (id?: string): id is string => !!id && !!loaded?.some((e) => e.id === id)
  const [currentId, setCurrentId] = useState<string>()
  const [seenRouteId, setSeenRouteId] = useState<string>()
  if (routeEventId !== seenRouteId && loaded) {
    setSeenRouteId(routeEventId)
    if (isEvent(routeEventId)) setCurrentId(routeEventId)
  }

  if (!loaded) {
    return (
      <main className="mx-auto max-w-md p-6">
        {loadError ? (
          <div role="alert" className="flex flex-col gap-3">
            <p className="text-sm text-danger">
              Não foi possível carregar os eventos. Verifica a ligação e tenta novamente.
            </p>
            <Button variant="secondary" onClick={() => void load()}>
              Tentar novamente
            </Button>
          </div>
        ) : (
          <p role="status" className="text-sm text-ink-secondary">
            A carregar os eventos…
          </p>
        )}
      </main>
    )
  }

  const events = loaded.map((e) => {
    const key = imageKey(e)
    return key ? { ...e, image: imageUrls[key] } : e
  })
  const current = events.find((e) => e.id === currentId) ?? events[0]
  const eventIncidents = current ? incidents.filter((i) => i.eventId === current.id) : []
  const openIncidents = eventIncidents.filter((i) => i.status !== 'resolved').length
  const currentLive = current ? (live[current.id] ?? live[DEMO_EVENT]) : undefined

  const path = location.pathname
  const onEventPage = isEvent(routeEventId)
  const scope: OrganizerScope =
    path.startsWith('/organization/events') && !onEventPage
      ? { kind: 'organization', title: 'Eventos' }
      : {
          kind: 'event',
          title:
            onEventPage && current
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

  const keep = (saved: OrgEvent) => {
    setLoaded((all = []) =>
      all.some((e) => e.id === saved.id)
        ? all.map((e) => (e.id === saved.id ? saved : e))
        : [saved, ...all],
    )
    setCurrentId(saved.id)
    return saved
  }

  const create = async (event: OrgEvent) => keep(await api.create(event))
  const correct = async (before: OrgEvent, after: OrgEvent) =>
    keep(await api.correct(before, after))
  const remove = async (eventId: string) => {
    await api.remove(eventId)
    setLoaded((all = []) => all.filter((e) => e.id !== eventId))
    setCurrentId((id) => (id === eventId ? undefined : id))
  }
  /** Uploads or removes the event's image, after the event is saved. */
  const changeImage = async (eventId: string, file: File | null) => {
    if (file) {
      await api.uploadImage(eventId, file)
      const updatedAt = new Date().toISOString()
      setImageUrls((all) => ({
        ...all,
        [`${eventId}@${updatedAt}`]: URL.createObjectURL(file),
      }))
      setLoaded((all = []) =>
        all.map((e) => (e.id === eventId ? { ...e, imageUpdatedAt: updatedAt } : e)),
      )
    } else {
      await api.removeImage(eventId)
      setLoaded((all = []) =>
        all.map((e) =>
          e.id === eventId ? { ...e, imageUpdatedAt: undefined, image: undefined } : e,
        ),
      )
    }
  }
  /** After an official change, the event as Core has it now. */
  const publishChange = async (event: OrgEvent, change: EventChange) => {
    await api.publishChange(event, change)
    await load()
  }

  /** Publishes the level in Core and keeps it on the event. */
  const publishCrowd = async (event: OrgEvent, level: CrowdLevel) => {
    const crowd = await api.publishCrowd(event.id, level)
    setLoaded((all = []) => all.map((e) => (e.id === event.id ? { ...e, crowd } : e)))
  }

  const updateIncident = (updated: Incident) =>
    setIncidents((all) => all.map((i) => (i.id === updated.id ? updated : i)))

  /** Pages about the current event need one: without events, O2 explains how
   * to create the first. */
  const forEvent = (page: (event: OrgEvent, state: LiveState) => ReactNode) =>
    current && currentLive ? page(current, currentLive) : <Navigate to="/organization/events" />

  return (
    <OrganizerLayout
      scope={scope}
      focused={focused}
      events={events}
      currentEvent={current}
      openIncidents={openIncidents}
      now={now}
      onSelectEvent={selectEvent}
      onSignOut={onSignOut}
    >
      <Routes>
        <Route index element={<Navigate to="events" replace />} />
        <Route path="events" element={<EventsPage events={events} alerts={alerts} now={now} />} />
        <Route
          path="events/new"
          element={
            <EventFormPage
              events={events}
              geocode={geocode}
              onCreate={create}
              onCorrect={correct}
              onImage={changeImage}
            />
          }
        />
        <Route
          path="events/:id"
          element={
            <EventDetailPage
              events={events}
              now={now}
              geocode={geocode}
              onPublishChange={publishChange}
            />
          }
        />
        <Route
          path="events/:id/edit"
          element={
            // Switching events in the top bar keeps this route: a new key starts
            // the form over, so it never shows or saves the previous event.
            <EventFormPage
              key={routeEventId}
              events={events}
              geocode={geocode}
              onCreate={create}
              onCorrect={correct}
              onImage={changeImage}
              onDelete={remove}
            />
          }
        />
        <Route
          path="live"
          element={forEvent((event, state) => (
            <LivePage event={event} live={state} incidents={eventIncidents} now={now} />
          ))}
        />
        <Route
          path="live/crowd"
          element={forEvent((event, state) => (
            <CrowdPage
              key={event.id}
              event={event}
              live={state}
              now={now}
              onPublishLevel={(level) => publishCrowd(event, level)}
              onChange={(next) => setLive((all) => ({ ...all, [event.id]: next }))}
            />
          ))}
        />
        <Route path="incidents" element={<IncidentsPage incidents={eventIncidents} now={now} />} />
        <Route
          path="incidents/:id"
          element={<IncidentDetailPage incidents={incidents} now={now} onChange={updateIncident} />}
        />
        <Route
          path="alerts"
          element={forEvent((event) => (
            <AlertPage
              event={event}
              alerts={alerts.filter((a) => a.eventId === event.id)}
              now={now}
              onSend={(alert) => setAlerts((all) => [alert, ...all])}
            />
          ))}
        />
        <Route
          path="requests"
          element={forEvent((event) => (
            <RequestPage event={event} />
          ))}
        />
        <Route path="more" element={<MorePage onSignOut={onSignOut} />} />
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
