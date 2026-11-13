import { describe, expect, it } from 'vitest'
import { fileSize, imageFileError, MAX_IMAGE_BYTES } from './eventImage'

describe('event image checks', () => {
  it('accepts a JPEG, PNG or WebP of up to 5 MB, as Core does', () => {
    expect(imageFileError({ type: 'image/jpeg', size: 420_000 })).toBeUndefined()
    expect(imageFileError({ type: 'image/webp', size: MAX_IMAGE_BYTES })).toBeUndefined()
    expect(imageFileError({ type: 'image/gif', size: 10 })).toBe(
      'Escolhe uma imagem JPEG, PNG ou WebP.',
    )
    expect(imageFileError({ type: 'image/png', size: MAX_IMAGE_BYTES + 1 })).toBe(
      'A imagem tem mais de 5 MB. Escolhe uma mais pequena.',
    )
  })

  it('shows a file size in kilobytes or megabytes', () => {
    expect(fileSize(300)).toBe('1 KB')
    expect(fileSize(430_080)).toBe('420 KB')
    expect(fileSize(1_258_291)).toBe('1,2 MB')
  })
})
