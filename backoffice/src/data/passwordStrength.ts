/** The password rule Core checks, as the requirements C4 lists, and how
 * strong a password is. The participant app has the same rule
 * (participant-app/lib/data/password_strength.dart). */

export interface PasswordRequirement {
  id: 'length' | 'uppercase' | 'lowercase' | 'digit' | 'symbol'
  label: string
  isMetBy: (password: string) => boolean
}

export const MIN_PASSWORD_LENGTH = 8

const isLetter = (c: string) => c.toLowerCase() !== c.toUpperCase()
const isDigit = (c: string) => c >= '0' && c <= '9'

export const passwordRequirements: PasswordRequirement[] = [
  {
    id: 'length',
    label: 'Pelo menos 8 caracteres',
    isMetBy: (p) => [...p].length >= MIN_PASSWORD_LENGTH,
  },
  {
    id: 'uppercase',
    label: 'Uma letra maiúscula (A–Z)',
    isMetBy: (p) => [...p].some((c) => c !== c.toLowerCase()),
  },
  {
    id: 'lowercase',
    label: 'Uma letra minúscula (a–z)',
    isMetBy: (p) => [...p].some((c) => c !== c.toUpperCase()),
  },
  { id: 'digit', label: 'Um algarismo (0–9)', isMetBy: (p) => [...p].some(isDigit) },
  {
    id: 'symbol',
    label: 'Um símbolo, como ! @ # $ % & * ? ou -',
    // Anything that is not a letter, a digit or a space, such as -, ! or €.
    isMetBy: (p) => [...p].some((c) => !isLetter(c) && !isDigit(c) && c.trim() !== ''),
  },
]

export type PasswordStrength = 'very-weak' | 'weak' | 'moderate' | 'strong' | 'very-strong'

/** The levels from weakest to strongest, with their label. */
export const strengthLevels: { strength: PasswordStrength; label: string }[] = [
  { strength: 'very-weak', label: 'Muito fraca' },
  { strength: 'weak', label: 'Fraca' },
  { strength: 'moderate', label: 'Moderada' },
  { strength: 'strong', label: 'Forte' },
  { strength: 'very-strong', label: 'Muito forte' },
]

/** The requirements the password does not meet yet. */
export function missingRequirements(password: string): PasswordRequirement[] {
  return passwordRequirements.filter((r) => !r.isMetBy(password))
}

/** Below moderate, Core refuses the password; then it grows stronger with
 * its length. */
export function passwordStrength(password: string): PasswordStrength {
  const missing = missingRequirements(password).length
  if (missing >= 3) return 'very-weak'
  if (missing > 0) return 'weak'
  const length = [...password].length
  if (length >= 16) return 'very-strong'
  return length >= 12 ? 'strong' : 'moderate'
}

export function meetsPasswordRule(password: string): boolean {
  return missingRequirements(password).length === 0
}
