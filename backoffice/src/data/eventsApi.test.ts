import { describe, expect, it } from 'vitest'
import {
  errorField,
  eventErrorMessage,
  EventsApiError,
  fromJson,
  httpEventsApi,
  instantOf,
  nearestDate,
  scheduleOf,
  toChangeBody,
  toCorrectionBody,
  toCreateBody,
} from './eventsApi'
import { defaultConfirmations, demoEvents, type OrgEvent } from './organizer'

function recording(status: number, body: unknown) {
  const calls: { url: string; init?: RequestInit }[] = []
  const fetchImpl: typeof fetch = (input, init) => {
    calls.push({ url: String(input), init })
    return Promise.resolve(
      new Response(body === undefined ? null : JSON.stringify(body), { status }),
    )
  }
  return { calls, fetchImpl }
}

const iso = (date: string, time: string) => instantOf(date, time).toISOString()

const json = {
  id: 'e-1',
  name: 'São João no Parque',
  description: 'Concerto gratuito.',
  type: 'PUBLIC' as const,
  status: 'POSTPONED' as const,
  ticketEventCode: null,
  accessCode: 'SAOJOAO26',
  startDateTime: iso('2026-06-23', '21:00'),
  doorsOpenAt: iso('2026-06-23', '20:00'),
  endDateTime: iso('2026-06-24', '02:00'),
  venue: {
    name: 'Parque da Cidade',
    address: 'Estrada Interior',
    district: 'Porto',
    latitude: 41.17,
    longitude: -8.678,
  },
  capacity: 20000,
  connectedUsers: 312,
  queuePoints: ['Porta A', 'Bar'],
  confirmations: {
    categories: ['CROWD_LEVEL', 'ON_SITE_PROBLEM'],
    venueRadiusMeters: 1000,
    highConfidenceAnswers: 12,
    maxPromptsPerHour: 1,
  },
}

const margens = (): OrgEvent => demoEvents()[2]

describe('events in Core', () => {
  it('reads an event in the local time of the organization', () => {
    expect(fromJson(json)).toMatchObject({
      id: 'e-1',
      date: '2026-06-23',
      doors: '20:00',
      start: '21:00',
      end: '02:00',
      venue: 'Parque da Cidade',
      address: 'Estrada Interior',
      latitude: 41.17,
      district: 'Porto',
      capacity: 20000,
      connected: 312,
      status: 'published',
      kind: 'public',
      code: 'SAOJOAO26',
      entrances: ['Porta A', 'Bar'],
      confirmations: {
        categories: { crowd: true, queue: false, accessibility: false, venue: true, lost: false },
        radiusMeters: 1000,
        highConfidenceAnswers: 12,
        maxPromptsPerHour: 1,
      },
    })
    expect(
      fromJson({
        ...json,
        type: 'TICKETED',
        status: 'ONGOING',
        ticketEventCode: 'MARG-2026',
        accessCode: null,
        doorsOpenAt: null,
        capacity: null,
        venue: { ...json.venue, latitude: null, longitude: null },
      }),
    ).toMatchObject({ kind: 'ticketed', status: 'live', code: 'MARG-2026', doors: '', capacity: 0 })
  })

  it('puts an end before the start on the next day and late doors on the day before', () => {
    expect(
      scheduleOf({ date: '2026-12-12', doors: '20:00', start: '21:00', end: '02:00' }),
    ).toEqual({
      startDateTime: iso('2026-12-12', '21:00'),
      doorsOpenAt: iso('2026-12-12', '20:00'),
      endDateTime: iso('2026-12-13', '02:00'),
    })
    expect(
      scheduleOf({ date: '2026-12-12', doors: '23:30', start: '00:30', end: '05:00' }),
    ).toEqual({
      startDateTime: iso('2026-12-12', '00:30'),
      doorsOpenAt: iso('2026-12-11', '23:30'),
      endDateTime: iso('2026-12-12', '05:00'),
    })
    expect(
      scheduleOf({ date: '2026-12-12', doors: '', start: '21:00', end: '23:00' }).doorsOpenAt,
    ).toBeNull()
  })

  it('creates an event with the code of its type and its setup', () => {
    const body = toCreateBody({
      ...margens(),
      name: ' Margens Inverno ',
      code: 'marginv26 ',
      confirmations: {
        ...defaultConfirmations(),
        categories: { crowd: true, queue: false, accessibility: false, venue: false, lost: true },
      },
    })
    expect(body).toMatchObject({
      name: 'Margens Inverno',
      type: 'TICKETED',
      ticketEventCode: 'MARGINV26',
      venue: {
        name: 'Pavilhão Municipal, Porto',
        address: 'Rua de Dom Manuel II, 4050-346 Porto',
        district: 'Porto',
        latitude: 41.148,
      },
      capacity: 4500,
      queuePoints: ['Porta A', 'Porta B', 'Bar principal'],
      // A personal category never asks for confirmations, even when switched on.
      confirmations: { categories: ['CROWD_LEVEL'], venueRadiusMeters: 500 },
    })
    expect(body).not.toHaveProperty('accessCode')
    expect(toCreateBody({ ...margens(), kind: 'public', code: 'abc1' })).toMatchObject({
      type: 'PUBLIC',
      accessCode: 'ABC1',
    })
  })

  it('corrects only what changed', () => {
    const before = margens()
    expect(toCorrectionBody(before, before)).toEqual({})
    expect(
      toCorrectionBody(before, {
        ...before,
        name: 'Margens de Inverno ',
        capacity: 5000,
        code: 'inverno26',
        entrances: ['Porta A'],
        confirmations: { ...before.confirmations, maxPromptsPerHour: 5 },
      }),
    ).toEqual({
      name: 'Margens de Inverno',
      capacity: 5000,
      ticketEventCode: 'INVERNO26',
      queuePoints: ['Porta A'],
      confirmations: {
        categories: ['CROWD_LEVEL', 'QUEUE_LENGTH', 'ACCESSIBILITY_BARRIER', 'ON_SITE_PROBLEM'],
        venueRadiusMeters: 500,
        highConfidenceAnswers: 8,
        maxPromptsPerHour: 5,
      },
    })
  })

  it('turns each official change into its request', () => {
    const e = margens()
    expect(
      toChangeBody(e, {
        kind: 'postpone',
        date: '2026-12-19',
        start: '21:00',
        doors: '20:00',
        message: ' Muda de data. ',
        ticketValid: true,
        refund: ' ',
      }),
    ).toEqual({
      type: 'POSTPONEMENT',
      newValue: {
        startDateTime: iso('2026-12-19', '21:00'),
        doorsOpenAt: iso('2026-12-19', '20:00'),
      },
      ticketStillValid: true,
      refundInfo: undefined,
      message: 'Muda de data.',
    })
    expect(toChangeBody(e, { kind: 'time', start: '21:30', doors: '20:00', message: '' })).toEqual({
      type: 'TIME_CHANGE',
      newValue: { startDateTime: iso('2026-12-12', '21:30'), doorsOpenAt: undefined },
      message: undefined,
    })
    expect(
      toChangeBody(e, {
        kind: 'venue',
        venue: 'Coliseu',
        address: 'Rua de Passos Manuel 137',
        latitude: 41.146,
        longitude: -8.606,
        message: '',
      }),
    ).toMatchObject({
      type: 'VENUE_CHANGE',
      newValue: { venueName: 'Coliseu', address: 'Rua de Passos Manuel 137', latitude: 41.146 },
    })
    expect(
      toChangeBody(e, { kind: 'cancel', message: '', ticketValid: false, refund: 'No site' }),
    ).toEqual({
      type: 'CANCELLATION',
      ticketStillValid: false,
      refundInfo: 'No site',
      message: undefined,
    })
  })

  it('puts a new start time on the day nearest to the current start', () => {
    const late = { date: '2026-12-12', start: '23:00' }
    expect(nearestDate(late, '23:30')).toBe('2026-12-12')
    expect(nearestDate(late, '00:30')).toBe('2026-12-13')
    expect(nearestDate({ date: '2026-12-13', start: '01:00' }, '22:00')).toBe('2026-12-12')
    expect(nearestDate({ date: '2026-12-12', start: '21:00' }, '18:00')).toBe('2026-12-12')

    const body = toChangeBody(
      { ...margens(), start: '23:00', doors: '22:00', end: '02:00' },
      { kind: 'time', start: '00:30', doors: '22:00', message: '' },
    )
    // Later the same night, not 22 h earlier.
    expect(body).toEqual({
      type: 'TIME_CHANGE',
      newValue: { startDateTime: iso('2026-12-13', '00:30'), doorsOpenAt: undefined },
      message: undefined,
    })
  })

  it('calls Core with the member token', async () => {
    const { calls, fetchImpl } = recording(200, [json])
    const api = httpEventsApi('jwt', fetchImpl, '/api/')

    expect(await api.list()).toHaveLength(1)
    expect(calls[0].url).toBe('/api/organizations/me/events')
    expect(new Headers(calls[0].init?.headers).get('Authorization')).toBe('Bearer jwt')

    const created = recording(201, json)
    expect((await httpEventsApi('jwt', created.fetchImpl, '/api').create(margens())).id).toBe('e-1')
    expect(created.calls[0].init?.method).toBe('POST')
    expect(created.calls[0].url).toBe('/api/events')

    const corrected = recording(200, json)
    const before = margens()
    const correctApi = httpEventsApi('jwt', corrected.fetchImpl, '/api')
    expect(await correctApi.correct(before, before)).toBe(before)
    expect(corrected.calls).toHaveLength(0)
    await correctApi.correct(before, { ...before, capacity: 1 })
    expect(corrected.calls[0]).toMatchObject({ url: `/api/events/${before.id}` })
    expect(corrected.calls[0].init?.method).toBe('PATCH')
    expect(JSON.parse(String(corrected.calls[0].init?.body))).toEqual({ capacity: 1 })

    const changed = recording(201, { changeId: 'c-1', severity: 'CRITICAL' })
    await httpEventsApi('jwt', changed.fetchImpl, '/api').publishChange(before, {
      kind: 'cancel',
      message: '',
    })
    expect(changed.calls[0].url).toBe(`/api/events/${before.id}/changes`)
  })

  it('reports Core refusals and lost connections by their code', async () => {
    const refused = recording(409, { code: 'EVENT_CODE_TAKEN' })
    await expect(httpEventsApi('jwt', refused.fetchImpl).create(margens())).rejects.toMatchObject({
      code: 'EVENT_CODE_TAKEN',
    })

    const failed = recording(500, undefined)
    await expect(httpEventsApi('jwt', failed.fetchImpl).list()).rejects.toMatchObject({
      code: 'HTTP_500',
    })

    const offline: typeof fetch = () => Promise.reject(new TypeError('offline'))
    await expect(httpEventsApi('jwt', offline).list()).rejects.toBeInstanceOf(EventsApiError)
  })

  it('places each refusal on its field, in European Portuguese', () => {
    expect(errorField('EVENT_CODE_TAKEN')).toBe('code')
    expect(errorField('CODE_INVALID')).toBe('code')
    expect(errorField('NAME_TOO_LONG')).toBe('name')
    expect(errorField('DESCRIPTION_REQUIRED')).toBe('description')
    expect(errorField('CAPACITY_OUT_OF_RANGE')).toBe('capacity')
    expect(errorField('QUEUE_POINT_REPEATED')).toBe('entrances')
    expect(errorField('START_IN_PAST')).toBe('start')
    expect(errorField('END_BEFORE_START')).toBe('end')
    expect(errorField('DISTRICT_UNKNOWN')).toBe('district')
    expect(errorField('LOCATION_REQUIRED')).toBe('address')
    expect(errorField('VENUE_REQUIRED')).toBe('venue')
    expect(errorField('NOT_ORGANIZATION_MEMBER')).toBeUndefined()

    for (const code of [
      'EVENT_CODE_TAKEN',
      'CODE_INVALID',
      'START_IN_PAST',
      'DOORS_AFTER_START',
      'END_BEFORE_START',
      'POSTPONEMENT_NOT_LATER',
      'NOTHING_CHANGES',
      'DISTRICT_UNKNOWN',
      'LOCATION_REQUIRED',
      'CAPACITY_REQUIRED',
      'QUEUE_POINT_REPEATED',
      'EVENT_CLOSED',
      'NOT_EVENT_ORGANIZATION',
      'NETWORK',
    ]) {
      expect(eventErrorMessage(code)).not.toBe(eventErrorMessage('UNKNOWN'))
    }
  })
})
