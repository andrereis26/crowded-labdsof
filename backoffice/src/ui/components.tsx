import type { ButtonHTMLAttributes, ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'

/** Shared building blocks of the backoffice, matching the Figma components. */

type ButtonVariant = 'primary' | 'secondary' | 'approve' | 'danger'

const buttonStyles: Record<ButtonVariant, string> = {
  primary: 'bg-ink text-white',
  secondary: 'bg-surface text-ink border border-border',
  approve: 'bg-approve text-white',
  danger: 'bg-danger-soft text-danger',
}

export function Button({
  variant = 'primary',
  icon: Icon,
  children,
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  icon?: LucideIcon
}) {
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-1.5 rounded-xl px-[18px] py-[13px] text-[15px] font-semibold transition-opacity hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-40 ${buttonStyles[variant]} ${className}`}
      {...props}
    >
      {Icon && <Icon size={18} aria-hidden />}
      {children}
    </button>
  )
}

export type TagTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info'

const tagStyles: Record<TagTone, string> = {
  neutral: 'bg-background text-ink-secondary',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  info: 'bg-primary-soft text-primary',
}

/** Pill with text: colour is never the only cue. */
export function Tag({
  tone = 'neutral',
  dot = false,
  children,
}: {
  tone?: TagTone
  dot?: boolean
  children: ReactNode
}) {
  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-[3px] text-[11px] font-semibold whitespace-nowrap ${tagStyles[tone]}`}
    >
      {dot && <span className="size-1.5 rounded-full bg-current" aria-hidden />}
      {children}
    </span>
  )
}

/** White rounded block on the grey background. */
export function Panel({
  title,
  children,
  className = '',
}: {
  title?: string
  children: ReactNode
  className?: string
}) {
  return (
    <section
      className={`flex flex-col gap-2.5 rounded-[14px] bg-surface px-[18px] py-4 ${className}`}
    >
      {title && <h3 className="text-[15px] font-semibold">{title}</h3>}
      {children}
    </section>
  )
}

/** Round avatar with initials. */
export function Avatar({
  initials,
  size = 36,
  className = 'bg-organization text-white',
}: {
  initials: string
  size?: number
  className?: string
}) {
  return (
    <span
      aria-hidden
      style={{ width: size, height: size, fontSize: size * 0.38 }}
      className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold ${className}`}
    >
      {initials}
    </span>
  )
}

/** Filter chip: dark when selected. */
export function Chip({
  selected,
  children,
  onClick,
}: {
  selected: boolean
  children: ReactNode
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`rounded-full px-3.5 py-[7px] text-[13px] whitespace-nowrap focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
        selected
          ? 'bg-ink font-semibold text-white'
          : 'border border-border bg-surface font-medium text-ink'
      }`}
    >
      {children}
    </button>
  )
}
