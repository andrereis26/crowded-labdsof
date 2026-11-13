import { ExternalLink, Map as MapIcon } from 'lucide-react'
import { useRef, useState } from 'react'
import { osmEmbedUrl, osmLink, type Geocoder, type Place } from '../../data/geocoding'
import { Field, TextInput } from '../../ui/forms'

type Lookup = 'idle' | 'searching' | 'found' | 'not-found' | 'failed'

/** Address of a venue: when the member leaves the field, it is looked up on
 * the map and shown below, so they can check the pin is the right place. The
 * location is cleared while the address is edited, so a stale one is never
 * saved. */
export function AddressField({
  address,
  place,
  geocode,
  onChange,
  error,
  disabled = false,
  label = 'Morada',
}: {
  address: string
  place?: Place
  geocode: Geocoder
  onChange: (address: string, place?: Place) => void
  error?: string
  disabled?: boolean
  label?: string
}) {
  const [lookup, setLookup] = useState<Lookup>(place ? 'found' : 'idle')
  const looked = useRef(place ? address.trim() : '')
  const latest = useRef(0)

  const find = async () => {
    const wanted = address.trim()
    if (!wanted || wanted === looked.current) return
    looked.current = wanted
    const request = ++latest.current
    setLookup('searching')
    try {
      const found = await geocode(wanted)
      // A slower answer for an address typed before is dropped.
      if (request !== latest.current) return
      setLookup(found ? 'found' : 'not-found')
      onChange(address, found)
    } catch {
      if (request !== latest.current) return
      looked.current = ''
      setLookup('failed')
    }
  }

  const status = {
    idle: undefined,
    searching: 'A procurar a morada no mapa…',
    found: undefined,
    'not-found': error
      ? undefined
      : 'Não encontrámos esta morada no mapa. Confirma a rua, o número e a localidade.',
    failed: 'Não foi possível abrir o mapa. Tenta novamente daqui a pouco.',
  }[lookup]

  return (
    <div className="flex flex-col gap-2">
      <Field
        label={label}
        error={error}
        help={
          disabled
            ? undefined
            : 'Ao sair do campo, mostramos a morada no mapa para confirmares o local.'
        }
      >
        {(p) => (
          <TextInput
            {...p}
            icon={MapIcon}
            value={address}
            disabled={disabled}
            autoComplete="street-address"
            onChange={(e) => {
              looked.current = ''
              latest.current++
              setLookup('idle')
              onChange(e.target.value, undefined)
            }}
            onBlur={find}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                void find()
              }
            }}
          />
        )}
      </Field>
      <p aria-live="polite" className="text-xs text-ink-secondary empty:hidden">
        {status}
      </p>
      {place && (
        <div className="flex flex-col gap-1.5">
          <iframe
            title={`Mapa com a localização de ${address}`}
            src={osmEmbedUrl(place)}
            loading="lazy"
            className="h-48 w-full rounded-[10px] border border-border"
          />
          <a
            href={osmLink(place)}
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-1 self-start text-xs font-semibold text-primary hover:underline"
          >
            Abrir no OpenStreetMap
            <ExternalLink size={12} aria-hidden />
            <span className="sr-only">(abre noutro separador)</span>
          </a>
        </div>
      )}
    </div>
  )
}
