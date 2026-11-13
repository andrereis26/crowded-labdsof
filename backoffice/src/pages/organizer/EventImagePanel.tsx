import { ImagePlus } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { ACCEPTED_IMAGE_TYPES, fileSize, imageFileError } from '../../data/eventImage'
import { Panel } from '../../ui/components'

/** What the member chose for the event's image in O3, applied when the event is saved. */
export type ImageChoice =
  { kind: 'keep' } | { kind: 'new'; file: File; url: string } | { kind: 'remove' }

/** O3's "Imagem do evento": the current image with "Substituir" and "Remover",
 * or a button to add one. The image is sent when the event is saved. */
export function EventImagePanel({
  current,
  choice,
  onChange,
}: {
  /** The image the event has now, if any. */
  current?: string
  choice: ImageChoice
  onChange: (choice: ImageChoice) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const errorId = useId()
  const [error, setError] = useState<string>()
  const [measured, setMeasured] = useState<{ url: string; width: number; height: number }>()
  const shown = choice.kind === 'new' ? choice.url : choice.kind === 'remove' ? undefined : current
  const size = choice.kind === 'new' && measured?.url === choice.url ? measured : undefined

  // The chosen picture's dimensions, read from the browser once it loads.
  useEffect(() => {
    if (choice.kind !== 'new') return
    const picture = new Image()
    picture.onload = () =>
      setMeasured({ url: choice.url, width: picture.naturalWidth, height: picture.naturalHeight })
    picture.src = choice.url
  }, [choice])

  const choose = (file: File | undefined) => {
    if (!file) return
    const refusal = imageFileError(file)
    setError(refusal)
    if (refusal) return
    onChange({ kind: 'new', file, url: URL.createObjectURL(file) })
  }

  const details =
    choice.kind === 'new'
      ? [size && `${size.width} × ${size.height}`, fileSize(choice.file.size)]
          .filter(Boolean)
          .join(' · ')
      : 'Mostrada na lista de eventos e na página do evento.'

  return (
    <Panel title="Imagem do evento" className="gap-3">
      <input
        ref={input}
        type="file"
        accept={ACCEPTED_IMAGE_TYPES.join(',')}
        aria-label="Escolher a imagem do evento"
        aria-describedby={error ? errorId : undefined}
        className="sr-only"
        tabIndex={-1}
        onChange={(e) => {
          choose(e.target.files?.[0])
          e.target.value = ''
        }}
      />
      {shown ? (
        <div className="flex items-center gap-3">
          <img src={shown} alt="" className="size-14 shrink-0 rounded-lg object-cover" />
          <div className="flex min-w-0 flex-1 flex-col">
            <p className="truncate text-sm font-semibold">
              {choice.kind === 'new' ? choice.file.name : 'Imagem atual'}
            </p>
            <p className="text-xs text-ink-secondary">{details}</p>
          </div>
          <div className="flex shrink-0 flex-col items-end gap-1">
            <button
              type="button"
              onClick={() => input.current?.click()}
              className="text-sm font-semibold text-primary hover:underline"
            >
              Substituir
            </button>
            <button
              type="button"
              onClick={() => {
                setError(undefined)
                onChange(current ? { kind: 'remove' } : { kind: 'keep' })
              }}
              className="text-sm font-semibold text-danger hover:underline"
            >
              Remover
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => input.current?.click()}
          className="flex items-center justify-center gap-2 rounded-xl border border-dashed border-border-strong px-4 py-5 text-sm font-semibold text-primary hover:bg-primary-soft"
        >
          <ImagePlus size={18} aria-hidden />
          Adicionar imagem
        </button>
      )}
      <p className="text-xs text-ink-secondary">JPEG, PNG ou WebP, até 5 MB.</p>
      {error && (
        <p id={errorId} role="alert" className="text-xs text-danger">
          {error}
        </p>
      )}
    </Panel>
  )
}
