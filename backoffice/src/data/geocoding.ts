import { config } from '../config'

/** Where an address is on the map, in degrees (WGS 84). */
export interface Place {
  latitude: number
  longitude: number
}

/** Shown when the map service is down, so the member does not doubt the address. */
export const MAP_UNAVAILABLE =
  'Não foi possível consultar o mapa para confirmar a morada. Tenta novamente daqui a pouco.'

/** Looks an address up on the map; undefined when nothing matches. Throws
 * when the map service cannot be reached. */
export type Geocoder = (address: string) => Promise<Place | undefined>

/** Everything Portugal covers, mainland and islands: Photon filters by a box,
 * not by country, so the answers are checked for Portugal too. */
const PORTUGAL_BOX = '-31.6,32.3,-6.1,42.2'

interface PhotonFeature {
  geometry: { coordinates: [number, number] }
  properties: { countrycode?: string }
}

/** {@link Geocoder} on Photon, the OpenStreetMap geocoder at `url` (ADR 0018),
 * limited to Portugal, where every event takes place (its district selects the
 * IPMA warnings). Photon matches loosely, so an address pasted from elsewhere
 * ("R. de D Manuel II Porta 03") still finds its street. Its public instance
 * asks for fair use, so the form looks an address up only when the member
 * leaves the field. */
export function photonGeocoder(
  url: string = config.geocoderUrl,
  fetchImpl: typeof fetch = fetch,
): Geocoder {
  return async (address) => {
    const query = new URLSearchParams({ q: address, limit: '5', bbox: PORTUGAL_BOX })
    const response = await fetchImpl(`${url}?${query}`, {
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) throw new Error(`The geocoder answered ${response.status}`)
    const { features } = (await response.json()) as { features: PhotonFeature[] }
    const inPortugal = features.find((f) => f.properties.countrycode === 'PT')
    if (!inPortugal) return undefined
    const [longitude, latitude] = inPortugal.geometry.coordinates
    return { latitude, longitude }
  }
}

/** Half the side of the map around the pin, in degrees: a few streets. */
const SPAN = 0.004

/** OpenStreetMap's embeddable map, centred on `place` with its pin. */
export function osmEmbedUrl(place: Place): string {
  const { latitude: lat, longitude: lon } = place
  const bbox = [lon - SPAN, lat - SPAN, lon + SPAN, lat + SPAN].map((n) => n.toFixed(5)).join(',')
  return `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${lat.toFixed(5)},${lon.toFixed(5)}`
}

/** The same place on openstreetmap.org, to open in a new tab. */
export function osmLink(place: Place): string {
  const lat = place.latitude.toFixed(5)
  const lon = place.longitude.toFixed(5)
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=17/${lat}/${lon}`
}
