import { describe, expect, it } from 'vitest'
import { photonGeocoder, osmEmbedUrl, osmLink } from './geocoding'

describe('addresses on the map', () => {
  it('looks an address up in Portugal with OpenStreetMap', async () => {
    const urls: string[] = []
    const fetchImpl: typeof fetch = (input) => {
      urls.push(String(input))
      return Promise.resolve(
        new Response(
          JSON.stringify({
            features: [
              feature('ES', -8.7, 42.1),
              feature('PT', -8.611, 41.1496),
              feature('PT', -9.13, 38.7),
            ],
          }),
        ),
      )
    }

    const place = await photonGeocoder(
      'https://geocoder.test/api',
      fetchImpl,
    )('Avenida dos Aliados, Porto')

    expect(place).toEqual({ latitude: 41.1496, longitude: -8.611 })
    const url = new URL(urls[0])
    expect(url.origin + url.pathname).toBe('https://geocoder.test/api')
    expect(url.searchParams.get('q')).toBe('Avenida dos Aliados, Porto')
    expect(url.searchParams.get('bbox')).toBe('-31.6,32.3,-6.1,42.2')
  })

  it('finds nothing outside Portugal and fails when the service does', async () => {
    const empty: typeof fetch = () =>
      Promise.resolve(new Response(JSON.stringify({ features: [feature('ES', -8.7, 42.1)] })))
    expect(await photonGeocoder('https://geocoder.test/api', empty)('Vigo')).toBeUndefined()

    const down: typeof fetch = () => Promise.resolve(new Response('', { status: 503 }))
    await expect(photonGeocoder('https://geocoder.test/api', down)('Porto')).rejects.toThrow('503')
  })

  it('uses the configured geocoder', async () => {
    const urls: string[] = []
    const fetchImpl: typeof fetch = (input) => {
      urls.push(String(input))
      return Promise.resolve(new Response(JSON.stringify({ features: [] })))
    }

    await photonGeocoder(undefined, fetchImpl)('Porto')

    expect(urls[0]).toMatch(/^https:\/\/photon\.komoot\.io\/api\?/)
  })

  it('shows the place on an embedded map and links to it', () => {
    const place = { latitude: 41.1496, longitude: -8.611 }
    expect(osmEmbedUrl(place)).toBe(
      'https://www.openstreetmap.org/export/embed.html?bbox=-8.61500,41.14560,-8.60700,41.15360&layer=mapnik&marker=41.14960,-8.61100',
    )
    expect(osmLink(place)).toBe(
      'https://www.openstreetmap.org/?mlat=41.14960&mlon=-8.61100#map=17/41.14960/-8.61100',
    )
  })
})

function feature(countrycode: string, longitude: number, latitude: number) {
  return { geometry: { coordinates: [longitude, latitude] }, properties: { countrycode } }
}
