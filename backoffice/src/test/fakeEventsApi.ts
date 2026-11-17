import { EventsApiError, type EventsApi } from '../data/eventsApi'
import type { Geocoder } from '../data/geocoding'
import { applyChange, demoEvents, slug, type OrgEvent } from '../data/organizer'

/** {@link EventsApi} in memory, seeded with the demo events, for the pages'
 * tests. `refuse` makes the next call fail with that code, as Core would. */
export function fakeEventsApi(events: OrgEvent[] = demoEvents()) {
  let stored = events
  const images = new Map<string, Blob>()
  const fake = {
    refuse: undefined as string | undefined,
    calls: [] as string[],
    get events() {
      return stored
    },
    get images() {
      return images
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
    async publishCrowd(eventId, level) {
      check('publishCrowd')
      const crowd = { level, publishedBy: 'Ribeira Live', publishedAt: new Date() }
      stored = stored.map((e) => (e.id === eventId ? { ...e, crowd } : e))
      return crowd
    },
    async image(eventId) {
      check('image')
      const image = images.get(eventId)
      if (!image) throw new EventsApiError('EVENT_IMAGE_NOT_FOUND')
      return image
    },
    async uploadImage(eventId, file) {
      check('uploadImage')
      images.set(eventId, file)
      stored = stored.map((e) =>
        e.id === eventId ? { ...e, imageUpdatedAt: new Date().toISOString() } : e,
      )
    },
    async removeImage(eventId) {
      check('removeImage')
      images.delete(eventId)
      stored = stored.map((e) => (e.id === eventId ? { ...e, imageUpdatedAt: undefined } : e))
    },
  }
  return Object.assign(fake, { api })
}

/** Finds every address that names a street, at a fixed place in Porto. */
export const fakeGeocoder: Geocoder = async (address) =>
  /rua|avenida|estrada|praça/i.test(address) ? { latitude: 41.1496, longitude: -8.611 } : undefined
