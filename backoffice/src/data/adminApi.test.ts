import { describe, expect, it } from 'vitest'
import { AdminApiError, adminErrorMessage, fromJson, httpAdminApi } from './adminApi'

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

const organization = {
  id: 'org-1',
  name: 'Ribeira Live',
  createdAt: '2026-09-12T10:00:00Z',
  eventCount: 4,
  members: [
    {
      userId: 'u-1',
      name: 'Rita Ferreira',
      email: 'rita@ribeira.pt',
      active: true,
      mustChangePassword: true,
      temporaryPasswordExpiresAt: '2026-11-19T10:00:00Z',
      lastSignedInAt: null,
    },
    {
      userId: 'u-2',
      name: null,
      email: 'antigo@ribeira.pt',
      active: false,
      mustChangePassword: false,
      temporaryPasswordExpiresAt: null,
      lastSignedInAt: '2026-10-03T18:05:00Z',
    },
  ],
}

describe('platform administration in Core', () => {
  it('lists the organizations and members in the shape A2 shows', async () => {
    const { calls, fetchImpl } = recording(200, [organization])

    const { organizations, members } = await httpAdminApi('jwt', fetchImpl, '/api/').list()

    expect(calls[0].url).toBe('/api/admin/organizations')
    expect(calls[0].init?.method).toBe('GET')
    expect(new Headers(calls[0].init?.headers).get('Authorization')).toBe('Bearer jwt')
    expect(organizations).toEqual([
      expect.objectContaining({
        id: 'org-1',
        name: 'Ribeira Live',
        createdAt: '2026-09-12',
        events: 4,
      }),
    ])
    expect(members).toEqual([
      expect.objectContaining({
        id: 'u-1',
        organizationId: 'org-1',
        status: 'temporary',
        temporaryExpiresAt: new Date('2026-11-19T10:00:00Z'),
        lastAccess: undefined,
      }),
      expect.objectContaining({
        id: 'u-2',
        name: 'antigo@ribeira.pt',
        status: 'disabled',
        lastAccess: new Date('2026-10-03T18:05:00Z'),
      }),
    ])
  })

  it('creates organizations and members, and changes members', async () => {
    const created = recording(201, { organizationId: 'org-2' })
    expect(await httpAdminApi('jwt', created.fetchImpl).createOrganization('Paredes')).toBe('org-2')
    expect(created.calls[0].url).toBe('/api/admin/organizations')
    expect(JSON.parse(String(created.calls[0].init?.body))).toEqual({ name: 'Paredes' })

    const added = recording(201, {
      userId: 'u-3',
      temporaryPassword: 'T7q-mK4-v9Px',
      expiresAt: '2026-11-19T10:00:00Z',
    })
    expect(await httpAdminApi('jwt', added.fetchImpl).addMember('org-2', 'Inês', 'i@p.pt')).toEqual(
      {
        userId: 'u-3',
        temporaryPassword: 'T7q-mK4-v9Px',
        expiresAt: new Date('2026-11-19T10:00:00Z'),
      },
    )
    expect(added.calls[0].url).toBe('/api/admin/organizations/org-2/members')

    const changed = recording(200, {
      userId: 'u-3',
      active: false,
      temporaryPassword: null,
      expiresAt: null,
    })
    expect(
      await httpAdminApi('jwt', changed.fetchImpl).changeMember('u-3', { active: false }),
    ).toEqual({ active: false, temporaryPassword: undefined, expiresAt: undefined })
    expect(changed.calls[0].init?.method).toBe('PATCH')
    expect(changed.calls[0].url).toBe('/api/admin/members/u-3')
  })

  it('turns refusals and network failures into codes and messages', async () => {
    const refused = recording(409, { status: 409, code: 'ORGANIZATION_NAME_TAKEN' })
    await expect(
      httpAdminApi('jwt', refused.fetchImpl).createOrganization('Ribeira Live'),
    ).rejects.toEqual(new AdminApiError('ORGANIZATION_NAME_TAKEN'))

    const broken = recording(502, undefined)
    await expect(httpAdminApi('jwt', broken.fetchImpl).list()).rejects.toMatchObject({
      code: 'HTTP_502',
    })

    const offline: typeof fetch = () => Promise.reject(new TypeError('Failed to fetch'))
    await expect(httpAdminApi('jwt', offline).list()).rejects.toMatchObject({ code: 'NETWORK' })

    expect(adminErrorMessage('ORGANIZATION_NAME_TAKEN')).toBe(
      'Já existe uma organização com este nome.',
    )
    expect(adminErrorMessage('ADMINISTRATOR_ONLY')).toBe(
      'Só o administrador do Crowded pode fazer isto.',
    )
    expect(adminErrorMessage('NETWORK')).toContain('Não foi possível ligar')
    expect(adminErrorMessage('OTHER')).toBe('Não foi possível guardar. Tenta novamente.')
  })

  it('gives each organization a thumbnail colour of its own', () => {
    const { organizations } = fromJson([
      { ...organization, id: 'a', members: [] },
      { ...organization, id: 'b', members: [] },
    ])
    expect(organizations[0].gradient).not.toEqual(organizations[1].gradient)
  })
})
