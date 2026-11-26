#!/usr/bin/env node
// The principal journeys across the backoffice's and the participant app's APIs, against a
// running system with the demo data: real sign-in, roles and organizations, connections by
// ticket and access code, what participants see, and the notifications that reach them.
//
// Usage: node scripts/ci/test-journeys.mjs [api-url]
//   api-url        default $CROWDED_API_URL, else http://localhost:8080/api
//                  (scripts/local/stack-up.sh)
//   DEMO_PASSWORD  password of the demo accounts, default crowded-demo
//
// It creates a public event of Ribeira Live and cancels it at the end, and sends an alert to
// Festival Margens do Douro: run it against a local stack or a test environment, never one
// with real participants. Needs Node 22 or later (fetch). Exits 1 on the first failure.

const api = (process.argv[2] ?? process.env.CROWDED_API_URL ?? 'http://localhost:8080/api')
  .replace(/\/$/, '')
const password = process.env.DEMO_PASSWORD ?? 'crowded-demo'

// The demo data's fixed rows (database/README.md).
const MARGENS = '30000000-0000-4000-8000-000000000001'
const accounts = {
  organizer: 'equipa@ribeira-live.test',
  otherOrganization: 'geral@sons-do-minho.test',
  ticketHolder: 'ana@crowded.test',
  participant: 'bruno@crowded.test',
}

let checks = 0

function ok(what) {
  checks += 1
  console.log(`ok: ${what}`)
}

function fail(what, detail) {
  console.error(`FAIL: ${what}`)
  if (detail !== undefined) console.error(`      ${JSON.stringify(detail)}`)
  process.exit(1)
}

async function call(token, method, path, body) {
  let response
  try {
    response = await fetch(`${api}${path}`, {
      method,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (e) {
    fail(`${method} ${path}: cannot reach ${api}`, String(e))
  }
  const text = await response.text()
  let json
  try {
    json = text ? JSON.parse(text) : undefined
  } catch {
    json = text
  }
  return { status: response.status, json }
}

function expectStatus(result, statuses, what) {
  const accepted = [statuses].flat()
  if (!accepted.includes(result.status)) fail(`${what}: HTTP ${result.status}`, result.json)
  ok(what)
  return result.json
}

/** Waits up to `seconds` for `find` to return something, asking again every second. */
async function eventually(what, find, seconds = 30) {
  for (let i = 0; i < seconds; i += 1) {
    const found = await find()
    if (found) {
      ok(what)
      return found
    }
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  fail(`${what}: not within ${seconds} s`)
}

async function signIn(email) {
  const body = expectStatus(
    await call(undefined, 'POST', '/auth/login', { email, password }),
    200,
    `${email} signs in with Core`,
  )
  return body.accessToken
}

const day = 24 * 3600 * 1000
const start = new Date(Date.now() + 2 * day)
start.setUTCMinutes(0, 0, 0)
const end = new Date(start.getTime() + 5 * 3600 * 1000)
const accessCode = `E2E-${Date.now().toString(36).toUpperCase()}`
const newEvent = {
  name: 'Concerto de teste (jornadas)',
  description: 'Criado pelo teste das jornadas principais.',
  type: 'PUBLIC',
  accessCode,
  startDateTime: start.toISOString(),
  endDateTime: end.toISOString(),
  venue: {
    name: 'Parque da Cidade',
    address: 'Estrada Interior da Circunvalação',
    district: 'Porto',
    latitude: 41.17,
    longitude: -8.678,
  },
  capacity: 5000,
  queuePoints: ['Porta A', 'Bar principal'],
}

console.log(`Journeys against ${api}`)

// 1. Everyone signs in with Core: no fixed user.
const organizer = await signIn(accounts.organizer)
const otherOrganization = await signIn(accounts.otherOrganization)
const ticketHolder = await signIn(accounts.ticketHolder)
const participant = await signIn(accounts.participant)
expectStatus(await call(undefined, 'GET', '/me'), 401, 'a request without a token is refused')

// 2. A participant cannot use the backoffice's endpoints.
expectStatus(
  await call(participant, 'GET', '/organizations/me/events'),
  403,
  'a participant cannot list an organization’s events',
)
expectStatus(
  await call(participant, 'POST', '/events', { ...newEvent, accessCode: `${accessCode}-P` }),
  403,
  'a participant cannot create an event',
)
expectStatus(
  await call(participant, 'PUT', `/events/${MARGENS}/crowd`, { level: 'FULL' }),
  403,
  'a participant cannot publish a crowd level',
)
expectStatus(
  await call(participant, 'GET', '/admin/organizations'),
  403,
  'a participant cannot open the administration',
)

// 3. The organizer creates a public event, in their own organization.
const created = expectStatus(
  await call(organizer, 'POST', '/events', newEvent),
  201,
  'the organizer creates and publishes a public event',
)
const eventId = created.id
const mine = expectStatus(
  await call(organizer, 'GET', '/organizations/me/events'),
  200,
  'the organizer lists their organization’s events',
)
if (!mine.some((e) => e.id === eventId)) fail('the new event is among the organization’s', mine)
ok('the new event is among the organization’s')

// 4. Another organization's member can neither see nor change it.
expectStatus(
  await call(otherOrganization, 'GET', `/events/${eventId}`),
  403,
  'another organization’s member cannot open the event',
)
expectStatus(
  await call(otherOrganization, 'PUT', `/events/${eventId}/crowd`, { level: 'FULL' }),
  403,
  'another organization’s member cannot publish its crowd level',
)
expectStatus(
  await call(otherOrganization, 'PUT', `/events/${eventId}/queues`, {
    queues: [{ pointName: 'Porta A', waitMinutes: 90 }],
  }),
  403,
  'another organization’s member cannot publish its queue times',
)
expectStatus(
  await call(otherOrganization, 'POST', `/events/${eventId}/alerts`, {
    type: 'OTHER',
    message: 'Não devia chegar.',
  }),
  403,
  'another organization’s member cannot send it an alert',
)
expectStatus(
  await call(otherOrganization, 'POST', `/events/${eventId}/changes`, {
    type: 'CANCELLATION',
    message: 'Não devia chegar.',
  }),
  403,
  'another organization’s member cannot change it',
)

// 5. Participants connect: one with the access code, one already holds a ticket.
expectStatus(
  await call(participant, 'GET', `/events/${eventId}`),
  403,
  'a participant does not see the event before connecting',
)
expectStatus(
  await call(participant, 'POST', '/connections', { accessCode: accessCode.toLowerCase() }),
  [200, 201],
  'a participant connects with the access code',
)
const byCode = expectStatus(
  await call(participant, 'GET', '/me/connections'),
  200,
  'the participant lists their events',
)
if (!byCode.connections.some((c) => c.event.id === eventId && c.type === 'ACCESS_CODE')) {
  fail('the event is in the participant’s events, by access code', byCode)
}
ok('the event is in the participant’s events, by access code')
const byTicket = expectStatus(
  await call(ticketHolder, 'GET', '/me/connections'),
  200,
  'the ticket holder lists their events',
)
if (!byTicket.connections.some((c) => c.event.id === MARGENS && c.type === 'TICKET')) {
  fail('Festival Margens do Douro is in the ticket holder’s events, by ticket', byTicket)
}
ok('Festival Margens do Douro is in the ticket holder’s events, by ticket')

// 6. The organizer publishes the crowd level, queue times and an alert; both sides see them.
expectStatus(
  await call(organizer, 'PUT', `/events/${eventId}/crowd`, { level: 'MODERATE' }),
  [200, 201],
  'the organizer publishes the crowd level',
)
expectStatus(
  await call(organizer, 'PUT', `/events/${eventId}/queues`, {
    queues: [{ pointName: 'Porta A', waitMinutes: 15 }],
  }),
  [200, 201],
  'the organizer publishes a queue time',
)
const alertMessage = `Porta A com fila longa (${accessCode}).`
expectStatus(
  await call(organizer, 'POST', `/events/${eventId}/alerts`, {
    type: 'SCHEDULE_DELAY',
    message: alertMessage,
  }),
  [200, 201],
  'the organizer sends an operational alert',
)
for (const [who, token] of [
  ['the connected participant', participant],
  ['the organizer', organizer],
]) {
  const page = expectStatus(
    await call(token, 'GET', `/events/${eventId}`),
    200,
    `${who} opens the event page`,
  )
  if (page.crowd?.level !== 'MODERATE') fail(`${who} sees the crowd level`, page.crowd)
  if (!page.queues.some((q) => q.pointName === 'Porta A' && q.waitMinutes === 15)) {
    fail(`${who} sees the queue time`, page.queues)
  }
  if (!page.activeAlerts.some((a) => a.message === alertMessage)) {
    fail(`${who} sees the alert`, page.activeAlerts)
  }
  ok(`${who} sees the crowd level, the queue time and the alert`)
}

// 7. An official change reaches the connected participant as a notification, with the alert.
const moved = new Date(start.getTime() + 3600 * 1000)
expectStatus(
  await call(organizer, 'POST', `/events/${eventId}/changes`, {
    type: 'TIME_CHANGE',
    newValue: { startDateTime: moved.toISOString() },
    message: 'Começa uma hora mais tarde.',
  }),
  [200, 201],
  'the organizer publishes an official change',
)
const notificationsOf = async (token) => {
  const result = await call(token, 'GET', '/notifications')
  return result.status === 200 ? result.json.notifications : []
}
await eventually('the change reaches the participant connected by access code', async () =>
  (await notificationsOf(participant)).find(
    (n) => n.eventId === eventId && n.type === 'OFFICIAL_CHANGE',
  ),
)
await eventually('the alert reaches the participant connected by access code', async () =>
  (await notificationsOf(participant)).find(
    (n) => n.eventId === eventId && n.type === 'OPERATIONAL_ALERT',
  ),
)

// 8. An alert of a ticketed event reaches its ticket holder, and nobody else.
const ticketAlert = `Teste das jornadas (${accessCode}).`
expectStatus(
  await call(organizer, 'POST', `/events/${MARGENS}/alerts`, {
    type: 'OTHER',
    message: ticketAlert,
  }),
  [200, 201],
  'the organizer sends an alert to Festival Margens do Douro',
)
await eventually('the alert reaches the participant connected by ticket', async () =>
  (await notificationsOf(ticketHolder)).find(
    (n) => n.eventId === MARGENS && n.type === 'OPERATIONAL_ALERT' && n.body?.includes(accessCode),
  ),
)
if (
  (await notificationsOf(participant)).some(
    (n) => n.eventId === MARGENS && n.body?.includes(accessCode),
  )
) {
  fail('a participant not connected to Festival Margens do Douro got its alert')
}
ok('a participant not connected to Festival Margens do Douro does not get its alert')

// 9. Clean up: the test event is cancelled, which participants are told too.
expectStatus(
  await call(organizer, 'POST', `/events/${eventId}/changes`, {
    type: 'CANCELLATION',
    ticketStillValid: false,
    message: 'Evento de teste cancelado.',
  }),
  [200, 201],
  'the organizer cancels the test event',
)

console.log(`\nAll ${checks} checks passed.`)
