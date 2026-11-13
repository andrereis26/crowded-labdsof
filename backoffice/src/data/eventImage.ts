/** The event image's checks in O3, the same Core makes before storing it. */

/** The types and size Core accepts (ADR 0019). */
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** Why a file cannot be the event's image, or nothing when it can. */
export function imageFileError(file: Pick<File, 'type' | 'size'>): string | undefined {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) return 'Escolhe uma imagem JPEG, PNG ou WebP.'
  if (file.size > MAX_IMAGE_BYTES) return 'A imagem tem mais de 5 MB. Escolhe uma mais pequena.'
  return undefined
}

/** "420 KB" or "1,2 MB", as the panel shows a file's size. */
export function fileSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}
