import { MARGIN, qrMatrix } from './qrMatrix'

/** QR code drawn as an SVG path, crisp at any size. */
export function QrCode({ text, size = 88 }: { text: string; size?: number }) {
  const matrix = qrMatrix(text)
  const total = matrix.length + MARGIN * 2
  const path = matrix
    .flatMap((row, y) => row.map((dark, x) => (dark ? `M${x + MARGIN} ${y + MARGIN}h1v1h-1z` : '')))
    .join('')
  return (
    <svg
      role="img"
      aria-label={`Código QR de ${text}`}
      width={size}
      height={size}
      viewBox={`0 0 ${total} ${total}`}
      shapeRendering="crispEdges"
      className="shrink-0"
    >
      <rect width={total} height={total} fill="#fff" />
      <path d={path} fill="#000" />
    </svg>
  )
}
