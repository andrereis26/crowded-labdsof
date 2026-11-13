import { describe, expect, it } from 'vitest'
import { envName, loadConfig } from './config'

describe('configuration', () => {
  it('names the variable of each setting', () => {
    expect(envName('geocoderUrl')).toBe('VITE_GEOCODER_URL')
    expect(envName('apiBaseUrl')).toBe('VITE_API_BASE_URL')
  })

  it('takes the file values unless a variable replaces them', () => {
    expect(loadConfig({})).toEqual({
      apiBaseUrl: '/api',
      geocoderUrl: 'https://photon.komoot.io/api',
    })

    const config = loadConfig({
      VITE_GEOCODER_URL: 'https://geocoder.example.test/api',
      VITE_API_BASE_URL: '',
    })

    expect(config.geocoderUrl).toBe('https://geocoder.example.test/api')
    expect(config.apiBaseUrl).toBe('/api')
  })
})
