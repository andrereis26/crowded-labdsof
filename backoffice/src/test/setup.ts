import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => cleanup())

// jsdom has no object URLs; the pages make them for chosen and fetched images.
let objectUrls = 0
URL.createObjectURL ??= () => `blob:test/${++objectUrls}`
URL.revokeObjectURL ??= () => undefined
