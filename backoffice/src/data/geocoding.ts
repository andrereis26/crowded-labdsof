/** Where an address is on the map, in degrees (WGS 84). */
export interface Place {
  latitude: number
  longitude: number
}

/** Looks an address up on the map; undefined when nothing matches. Throws
 * when the map service cannot be reached. */
export type Geocoder = (address: string) => Promise<Place | undefined>

/** OpenStreetMap's geocoder (ADR 0018). Its usage policy allows at most one
 * request per second, so the form looks an address up only when the member
 * leaves the field. */
const nominatimUrl = 'https://nominatim.openstreetmap.org/search'

/** {@link Geocoder} on OpenStreetMap Nominatim, limited to Portugal, where
 * every event takes place (its district selects the IPMA warnings). */
export function nominatimGeocoder(fetchImpl: typeof fetch = fetch): Geocoder {
  return async (address) => {
    const query = new URLSearchParams({
      q: address,
      format: 'jsonv2',
      limit: '1',
      countrycodes: 'pt',
    })
    const response = await fetchImpl(`${nominatimUrl}?${query}`, {
      headers: { Accept: 'application/json', 'Accept-Language': 'pt-PT' },
    })
    if (!response.ok) throw new Error(`Nominatim answered ${response.status}`)
    const results = (await response.json()) as { lat: string; lon: string }[]
    const [first] = results
    if (!first) return undefined
    return { latitude: Number(first.lat), longitude: Number(first.lon) }
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
