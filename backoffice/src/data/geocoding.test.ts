import { describe, expect, it } from 'vitest'
import { nominatimGeocoder, osmEmbedUrl, osmLink } from './geocoding'

describe('addresses on the map', () => {
  it('looks an address up in Portugal with OpenStreetMap', async () => {
    const urls: string[] = []
    const fetchImpl: typeof fetch = (input) => {
      urls.push(String(input))
      return Promise.resolve(
        new Response(JSON.stringify([{ lat: '41.1496', lon: '-8.6110', name: 'Aliados' }])),
      )
    }

    const place = await nominatimGeocoder(fetchImpl)('Avenida dos Aliados, Porto')

    expect(place).toEqual({ latitude: 41.1496, longitude: -8.611 })
    const url = new URL(urls[0])
    expect(url.origin + url.pathname).toBe('https://nominatim.openstreetmap.org/search')
    expect(url.searchParams.get('q')).toBe('Avenida dos Aliados, Porto')
    expect(url.searchParams.get('countrycodes')).toBe('pt')
    expect(url.searchParams.get('limit')).toBe('1')
  })

  it('finds nothing for an unknown address and fails when the service does', async () => {
    const empty: typeof fetch = () => Promise.resolve(new Response('[]'))
    expect(await nominatimGeocoder(empty)('Lugar nenhum')).toBeUndefined()

    const down: typeof fetch = () => Promise.resolve(new Response('', { status: 503 }))
    await expect(nominatimGeocoder(down)('Porto')).rejects.toThrow('503')
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
