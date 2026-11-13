import defaults from './config.json'

/** The backoffice's settings: the values in `config.json`, each one replaced
 * by its environment variable when the build sets it. */
export type Config = typeof defaults

/** The variable that replaces a setting: `VITE_` and the key in upper snake
 * case, so `geocoderUrl` is `VITE_GEOCODER_URL`. Vite gives the app only the
 * variables that start with `VITE_`, when it builds or serves it. */
export function envName(key: string): string {
  return `VITE_${key.replace(/[A-Z]/g, (letter) => `_${letter}`).toUpperCase()}`
}

/** Reads every setting, preferring its variable; an empty one keeps the file's value. */
export function loadConfig(env: Record<string, unknown> = import.meta.env): Config {
  const entries = Object.entries(defaults).map(([key, value]) => {
    const fromEnv = env[envName(key)]
    return [key, typeof fromEnv === 'string' && fromEnv !== '' ? fromEnv : value]
  })
  return Object.fromEntries(entries) as Config
}

export const config: Config = loadConfig()
