import { EventsApiError, type EventsApi } from '../data/eventsApi'
import type { Geocoder } from '../data/geocoding'
import { applyChange, demoEvents, slug, type OrgEvent } from '../data/organizer'

/** {@link EventsApi} in memory, seeded with the demo events, for the pages'
 * tests. `refuse` makes the next call fail with that code, as Core would. */
export function fakeEventsApi(events: OrgEvent[] = demoEvents()) {
  let stored = events
  const fake = {
    refuse: undefined as string | undefined,
    calls: [] as string[],
    get events() {
      return stored
    },
  }
  const check = (call: string) => {
    fake.calls.push(call)
    const code = fake.refuse
    fake.refuse = undefined
    if (code) throw new EventsApiError(code)
  }
  const api: EventsApi = {
    async list() {
      check('list')
      return stored
    },
    async create(event) {
      check('create')
      const created: OrgEvent = {
        ...event,
        id: slug(event.name) || 'evento',
        name: event.name.trim(),
        code: event.code.trim().toUpperCase(),
        status: 'published',
      }
      stored = [created, ...stored]
      return created
    },
    async correct(before, after) {
      check('correct')
      const corrected = { ...after, name: after.name.trim(), code: after.code.toUpperCase() }
      stored = stored.map((e) => (e.id === before.id ? corrected : e))
      return corrected
    },
    async publishChange(event, change) {
      check('publishChange')
      stored = stored.map((e) => (e.id === event.id ? applyChange(e, change) : e))
    },
  }
  return Object.assign(fake, { api })
}

/** Finds every address that names a street, at a fixed place in Porto. */
export const fakeGeocoder: Geocoder = async (address) =>
  /rua|avenida|estrada|praça/i.test(address) ? { latitude: 41.1496, longitude: -8.611 } : undefined
