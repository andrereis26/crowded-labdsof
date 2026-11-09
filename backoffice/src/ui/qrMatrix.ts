import QRCode from 'qrcode'

/** Dark modules of the QR code of `text`, as a square matrix. */
export function qrMatrix(text: string): boolean[][] {
  const { modules } = QRCode.create(text, { errorCorrectionLevel: 'M' })
  return Array.from({ length: modules.size }, (_, y) =>
    Array.from({ length: modules.size }, (_, x) => !!modules.get(y, x)),
  )
}

/** Quiet zone around the code, in modules. */
export const MARGIN = 2

/** Saves the QR code of `text` as a PNG file, for posters and the entrance. */
export function downloadQrPng(text: string, fileName: string, scale = 16) {
  const matrix = qrMatrix(text)
  const total = (matrix.length + MARGIN * 2) * scale
  const canvas = document.createElement('canvas')
  canvas.width = total
  canvas.height = total
  const context = canvas.getContext('2d')
  if (!context) return
  context.fillStyle = '#fff'
  context.fillRect(0, 0, total, total)
  context.fillStyle = '#000'
  matrix.forEach((row, y) =>
    row.forEach((dark, x) => {
      if (dark) context.fillRect((x + MARGIN) * scale, (y + MARGIN) * scale, scale, scale)
    }),
  )
  const link = document.createElement('a')
  link.href = canvas.toDataURL('image/png')
  link.download = fileName
  link.click()
}
