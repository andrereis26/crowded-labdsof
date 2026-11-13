import { describe, expect, it } from 'vitest'
import { meetsPasswordRule, missingRequirements, passwordStrength } from './passwordStrength'

describe('password strength', () => {
  it('rates each password on the five levels', () => {
    const expected = {
      curta: 'very-weak',
      abcdefgh: 'very-weak',
      'semmaiusculas1!': 'weak', // ggignore
      Semsimbolos1: 'weak', // ggignore
      'Com espaco1': 'weak', // ggignore
      'Segr3do!': 'moderate', // ggignore
      'Çeia-2026': 'moderate', // ggignore
      'Segr3do!-Forte': 'strong', // ggignore
      'Palavra-secr3ta!': 'very-strong', // ggignore
    }
    for (const [password, strength] of Object.entries(expected)) {
      expect(passwordStrength(password), password).toBe(strength)
    }
  })

  it('lists what is missing, as Core checks it', () => {
    expect(missingRequirements('abc').map((r) => r.id)).toEqual([
      'length',
      'uppercase',
      'digit',
      'symbol',
    ])
    expect(meetsPasswordRule('Pass €2026')).toBe(true) // ggignore
    expect(meetsPasswordRule('Pass 2026')).toBe(false) // ggignore
  })
})
