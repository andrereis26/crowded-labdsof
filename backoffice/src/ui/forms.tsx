import { Check, ChevronDown, Info, Minus, Plus } from 'lucide-react'
import {
  useId,
  useState,
  type ComponentProps,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'
import type { Icon } from './components'

/** Form controls shared by the backoffice screens, matching the Figma
 * "Campo", "Interruptor", "Caixa marcada" and "Controlo segmentado". */

export interface ControlProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
}

/** Label, control, help text and error message, linked for screen readers. */
export function Field({
  label,
  optional = false,
  help,
  hint,
  error,
  className = '',
  children,
}: {
  label: ReactNode
  optional?: boolean
  help?: ReactNode
  /** Like `help`, but shown in an ⓘ tooltip next to the label. */
  hint?: ReactNode
  error?: string
  className?: string
  children: (props: ControlProps) => ReactNode
}) {
  const id = useId()
  const helpId = `${id}-help`
  const errorId = `${id}-error`
  const describedBy = [(help || hint) && helpId, error && errorId].filter(Boolean).join(' ')
  return (
    <div className={`flex min-w-0 flex-col gap-1.5 ${className}`}>
      <div className="flex items-center gap-1">
        <label htmlFor={id} className="text-[13px] font-medium">
          {label}
          {optional && (
            <span className="ml-1.5 text-xs font-normal text-ink-secondary">(opcional)</span>
          )}
        </label>
        {hint && <InfoTip id={helpId}>{hint}</InfoTip>}
      </div>
      {children({
        id,
        'aria-describedby': describedBy || undefined,
        'aria-invalid': error ? true : undefined,
      })}
      {help && !hint && (
        <p id={helpId} className="text-xs leading-[1.35] text-ink-secondary">
          {help}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

/**
 * ⓘ button with a short explanation that opens on hover, focus or tap and closes
 * with Escape. The text stays in the accessibility tree while closed, so controls
 * can point `aria-describedby` at `id`.
 */
export function InfoTip({ id, children }: { id?: string; children: ReactNode }) {
  const ownId = useId()
  const tipId = id ?? ownId
  const [open, setOpen] = useState(false)
  return (
    <span
      className="relative inline-flex"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        aria-label="Mais informação"
        aria-describedby={tipId}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onClick={() => setOpen(true)}
        onKeyDown={(e) => e.key === 'Escape' && setOpen(false)}
        className="inline-flex size-5 items-center justify-center rounded-full text-ink-secondary hover:text-ink focus-visible:outline-2 focus-visible:outline-primary"
      >
        <Info size={14} aria-hidden />
      </button>
      <span
        id={tipId}
        role="tooltip"
        className={
          open
            ? 'absolute top-full left-0 z-20 mt-1 w-max max-w-64 rounded-lg bg-ink px-2.5 py-1.5 text-xs leading-[1.35] font-normal text-white shadow-lg'
            : 'sr-only'
        }
      >
        {children}
      </span>
    </span>
  )
}

const boxClass = (invalid?: boolean) =>
  `relative flex w-full items-center gap-2 rounded-[10px] border bg-surface px-3.5 focus-within:outline-2 focus-within:outline-primary ${
    invalid ? 'border-danger' : 'border-border'
  }`

/** Text box with an optional leading icon and a unit after the value, such
 * as "m"; the label names the unit for screen readers. */
export function TextInput({
  icon: Icon,
  unit,
  className = '',
  ...props
}: ComponentProps<'input'> & { icon?: Icon; unit?: string }) {
  return (
    <div className={`${boxClass(!!props['aria-invalid'])} ${className}`}>
      {Icon && <Icon size={18} className="shrink-0 text-ink" aria-hidden />}
      <input
        className="min-w-0 flex-1 bg-transparent py-3 text-[15px] outline-none placeholder:text-ink-secondary"
        {...props}
      />
      {unit && (
        <span className="shrink-0 text-[15px] text-ink-secondary" aria-hidden>
          {unit}
        </span>
      )}
    </div>
  )
}

export function TextArea({
  className = '',
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      rows={2}
      className={`w-full resize-y rounded-[10px] border bg-surface px-3.5 pt-3 pb-5 text-[15px] leading-[1.4] placeholder:text-ink-secondary focus:outline-2 focus:outline-primary ${
        props['aria-invalid'] ? 'border-danger' : 'border-border'
      } ${className}`}
      {...props}
    />
  )
}

/** Native select with the chevron of the mockups. `sm` is the compact
 * toolbar filter (e.g. "Todas as categorias"), with an optional icon. Where
 * the browser allows it, the option list is styled too (see `.ui-select` in
 * index.css). */
export function Select({
  options,
  icon: Icon,
  size = 'md',
  className = '',
  ...props
}: Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> & {
  options: readonly (string | { value: string; label: string })[]
  icon?: Icon
  size?: 'sm' | 'md'
}) {
  const small = size === 'sm'
  return (
    <div className={`relative ${className}`}>
      {Icon && (
        <Icon
          size={small ? 16 : 18}
          className={`pointer-events-none absolute top-1/2 -translate-y-1/2 ${small ? 'left-3' : 'left-3.5'}`}
          aria-hidden
        />
      )}
      <select
        className={`ui-select w-full cursor-pointer appearance-none border border-border bg-surface text-left focus:outline-2 focus:outline-primary ${
          small
            ? `rounded-[10px] py-2 pr-8 text-[13px] font-medium ${Icon ? 'pl-[34px]' : 'pl-3'}`
            : `rounded-[10px] py-3 pr-10 text-[15px] ${Icon ? 'pl-10' : 'pl-3.5'}`
        }`}
        {...props}
      >
        {options.map((o) => {
          const { value, label } = typeof o === 'string' ? { value: o, label: o } : o
          return (
            <option key={value} value={value}>
              {label}
            </option>
          )
        })}
      </select>
      <ChevronDown
        size={small ? 16 : 18}
        className={`pointer-events-none absolute top-1/2 -translate-y-1/2 ${small ? 'right-2.5' : 'right-3.5'}`}
        aria-hidden
      />
    </div>
  )
}

/** On/off switch. Its accessible name comes from `label` (visible nearby). */
export function Toggle({
  checked,
  onChange,
  label,
  describedBy,
  disabled = false,
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  describedBy?: string
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      aria-describedby={describedBy}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative h-7 w-[46px] shrink-0 rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-45 ${
        checked ? 'bg-switch-on' : 'bg-switch-off'
      }`}
    >
      <span
        aria-hidden
        className={`absolute top-0.5 size-6 rounded-full bg-white shadow-sm transition-[left] ${
          checked ? 'left-5' : 'left-0.5'
        }`}
      />
    </button>
  )
}

/** Title and description on the left, switch on the right. */
export function ToggleRow({
  title,
  description,
  hint,
  checked,
  onChange,
  action,
  disabled,
}: {
  title: string
  description?: ReactNode
  /** Like `description`, but shown in an ⓘ tooltip next to the title. */
  hint?: ReactNode
  checked: boolean
  onChange: (checked: boolean) => void
  action?: ReactNode
  disabled?: boolean
}) {
  const hintId = useId()
  return (
    <div className="flex items-center gap-3">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1">
          <p className="text-sm font-semibold">{title}</p>
          {hint && <InfoTip id={hintId}>{hint}</InfoTip>}
        </div>
        {description && <p className="mt-0.5 text-xs text-ink-secondary">{description}</p>}
      </div>
      {action}
      <Toggle
        checked={checked}
        onChange={onChange}
        label={title}
        describedBy={hint ? hintId : undefined}
        disabled={disabled}
      />
    </div>
  )
}

export function Checkbox({
  checked,
  onChange,
  children,
  error,
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  children: ReactNode
  error?: string
}) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1">
      <label className="flex cursor-pointer items-center gap-2.5 text-[13px] lg:text-sm">
        <span className="relative inline-flex size-[22px] shrink-0">
          <input
            type="checkbox"
            checked={checked}
            onChange={(e) => onChange(e.target.checked)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? id : undefined}
            className={`peer size-[22px] cursor-pointer appearance-none rounded-md border-[1.5px] bg-surface checked:border-primary checked:bg-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
              error ? 'border-danger' : 'border-border-strong'
            }`}
          />
          <Check
            size={16}
            strokeWidth={2.5}
            className="pointer-events-none absolute top-[3px] left-[3px] hidden text-white peer-checked:block"
            aria-hidden
          />
        </span>
        <span className="flex-1">{children}</span>
      </label>
      {error && (
        <p id={id} className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

/** The round radio of the mockups. Use inside a label with a native radio. */
export function RadioDot({ checked }: { checked: boolean }) {
  return (
    <span
      aria-hidden
      className={`inline-flex size-[22px] shrink-0 items-center justify-center rounded-full border-[1.5px] ${
        checked ? 'border-primary bg-primary' : 'border-border-strong bg-surface'
      }`}
    >
      {checked && <span className="size-2 rounded-full bg-white" />}
    </span>
  )
}

/** Radio option: a hidden native input keeps keyboard and screen reader
 * behaviour, the dot and the card follow the mockups. */
export function RadioOption({
  name,
  value,
  checked,
  onChange,
  children,
  card = false,
  disabled = false,
  className = '',
}: {
  name: string
  value: string
  checked: boolean
  onChange: (value: string) => void
  children: ReactNode
  /** Bordered card, highlighted in blue when selected. */
  card?: boolean
  disabled?: boolean
  className?: string
}) {
  return (
    <label
      className={`flex items-center ${disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'} gap-2.5 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-primary ${
        card
          ? `rounded-xl px-3.5 py-3 ${checked ? 'border-2 border-primary bg-primary-soft' : 'border border-border bg-surface'}`
          : 'rounded-md'
      } ${className}`}
    >
      <input
        type="radio"
        name={name}
        value={value}
        checked={checked}
        disabled={disabled}
        onChange={() => onChange(value)}
        className="sr-only"
      />
      {children}
    </label>
  )
}

/** iOS-like segmented control backed by radio inputs. */
export function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: readonly { value: T; label: string }[]
  value: T
  onChange: (value: T) => void
}) {
  const name = useId()
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className="flex gap-0.5 rounded-[10px] bg-segment-track p-[3px]"
    >
      {options.map((o) => (
        <label
          key={o.value}
          className={`flex flex-1 cursor-pointer items-center justify-center rounded-lg p-2 text-[13px] whitespace-nowrap has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-primary ${
            value === o.value
              ? 'bg-surface font-semibold text-ink shadow-sm'
              : 'font-medium text-ink-secondary'
          }`}
        >
          <input
            type="radio"
            name={name}
            value={o.value}
            checked={value === o.value}
            onChange={() => onChange(o.value)}
            className="sr-only"
          />
          {o.label}
        </label>
      ))}
    </div>
  )
}

/** Minus, value and plus, as in "Seletor de minutos". */
export function Stepper({
  value,
  onChange,
  label,
  unit = 'min',
  step = 1,
  min = 0,
}: {
  value: number
  onChange: (value: number) => void
  label: string
  unit?: string
  step?: number
  min?: number
}) {
  const round = 'rounded-full bg-background p-1.5 hover:bg-border disabled:opacity-40'
  return (
    <div className="flex items-center gap-1.5" role="group" aria-label={label}>
      <button
        type="button"
        className={round}
        aria-label={`Diminuir ${label}`}
        disabled={value <= min}
        onClick={() => onChange(Math.max(min, value - step))}
      >
        <Minus size={16} aria-hidden />
      </button>
      <output
        aria-live="polite"
        className="min-w-[58px] rounded-lg border border-border px-2.5 py-1.5 text-center text-sm font-semibold"
      >
        {value} {unit}
      </output>
      <button
        type="button"
        className={round}
        aria-label={`Aumentar ${label}`}
        onClick={() => onChange(value + step)}
      >
        <Plus size={16} aria-hidden />
      </button>
    </div>
  )
}
