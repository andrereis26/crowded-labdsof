/** Class names and labels shared by components and pages. Kept apart from
 * components.tsx so fast refresh keeps working. */

export type ButtonVariant = 'primary' | 'secondary' | 'approve' | 'danger' | 'ai'
export type ButtonSize = 'sm' | 'md' | 'lg'

const buttonStyles: Record<ButtonVariant, string> = {
  primary: 'bg-ink text-white',
  secondary: 'bg-surface text-ink border border-border',
  approve: 'bg-approve text-white',
  danger: 'bg-danger-soft text-danger',
  ai: 'bg-ai-soft text-ai',
}

const buttonSizes: Record<ButtonSize, string> = {
  sm: 'gap-1.5 rounded-[10px] px-3.5 py-2 text-[13px]',
  md: 'gap-1.5 rounded-xl px-[18px] py-[13px] text-[15px]',
  lg: 'gap-1.5 rounded-xl px-5 py-3.5 text-base',
}

export function buttonClass(
  variant: ButtonVariant = 'primary',
  size: ButtonSize = 'md',
  className = '',
) {
  return `inline-flex items-center justify-center font-semibold whitespace-nowrap transition-opacity hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:pointer-events-none disabled:opacity-40 ${buttonStyles[variant]} ${buttonSizes[size]} ${className}`
}

export type CrowdLevel = 'low' | 'moderate' | 'high' | 'full'

export const crowdLevels: Record<
  CrowdLevel,
  { label: string; hint: string; color: string; segments: number }
> = {
  low: { label: 'Baixa', hint: 'Entra-se sem esperar', color: 'bg-crowd-low', segments: 1 },
  moderate: { label: 'Moderada', hint: 'Algumas filas', color: 'bg-crowd-moderate', segments: 2 },
  high: { label: 'Elevada', hint: 'Filas longas', color: 'bg-crowd-high', segments: 3 },
  full: { label: 'Lotado', hint: 'Entradas fechadas', color: 'bg-crowd-full', segments: 4 },
}

