import {
  useEffect,
  useId,
  useRef,
  type ButtonHTMLAttributes,
  type ComponentType,
  type ReactNode,
} from 'react'
import { ChevronRight, Sparkles, X, type LucideProps } from 'lucide-react'
import { Link, type LinkProps } from 'react-router'
import {
  buttonClass,
  crowdLevels,
  type ButtonSize,
  type ButtonVariant,
  type CrowdLevel,
} from './styles'

/** Shared building blocks of the backoffice, matching the Figma components. */

export type Icon = ComponentType<LucideProps>

export function Button({
  variant = 'primary',
  size = 'md',
  icon: Icon,
  children,
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  icon?: Icon
}) {
  return (
    <button type="button" className={buttonClass(variant, size, className)} {...props}>
      {Icon && <Icon size={size === 'sm' ? 16 : 18} aria-hidden />}
      {children}
    </button>
  )
}

/** A router link that looks like a button. */
export function ButtonLink({
  variant = 'primary',
  size = 'md',
  icon: Icon,
  children,
  className = '',
  ...props
}: LinkProps & { variant?: ButtonVariant; size?: ButtonSize; icon?: Icon }) {
  return (
    <Link className={buttonClass(variant, size, className)} {...props}>
      {Icon && <Icon size={size === 'sm' ? 16 : 18} aria-hidden />}
      {children}
    </Link>
  )
}

/** Blue text action, as in "Escolher categorias" or "Ver todos". */
export function TextLink({ className = '', ...props }: LinkProps) {
  return (
    <Link
      className={`text-[13px] font-semibold whitespace-nowrap text-primary hover:underline ${className}`}
      {...props}
    />
  )
}

export type TagTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'ai'

const tagStyles: Record<TagTone, string> = {
  neutral: 'bg-background text-ink-secondary',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  info: 'bg-primary-soft text-primary',
  ai: 'bg-ai-soft text-ai',
}

/** Pill with text: colour is never the only cue. */
export function Tag({
  tone = 'neutral',
  dot = false,
  icon: Icon,
  children,
}: {
  tone?: TagTone
  dot?: boolean
  icon?: Icon
  children: ReactNode
}) {
  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-[3px] text-[11px] font-semibold whitespace-nowrap ${tagStyles[tone]}`}
    >
      {dot && <span className="size-1.5 rounded-full bg-current" aria-hidden />}
      {Icon && <Icon size={12} aria-hidden />}
      {children}
    </span>
  )
}

/** "Rascunho gerado por IA": AI content is always labelled. */
export function AiTag({ children = 'Rascunho gerado por IA' }: { children?: ReactNode }) {
  return (
    <Tag tone="ai" icon={Sparkles}>
      {children}
    </Tag>
  )
}

/** White rounded block on the grey background. */
export function Panel({
  title,
  action,
  children,
  className = '',
  titleClassName = 'text-[15px]',
}: {
  title?: ReactNode
  /** Shown on the right of the title: a link, a tag or a short note. */
  action?: ReactNode
  children: ReactNode
  className?: string
  titleClassName?: string
}) {
  return (
    <section
      className={`flex flex-col gap-2.5 rounded-[14px] bg-surface px-[18px] py-4 ${className}`}
    >
      {(title || action) && (
        <div className="flex flex-wrap items-center justify-between gap-2">
          {title && <h3 className={`font-semibold ${titleClassName}`}>{title}</h3>}
          {action}
        </div>
      )}
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

/** Page title with a short description and actions on the right. */
export function PageHeader({
  title,
  description,
  actions,
  className = '',
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  className?: string
}) {
  return (
    <div className={`flex flex-wrap items-center justify-between gap-4 ${className}`}>
      <div className="min-w-0 flex-1">
        <h2 className="text-[26px] leading-tight font-bold tracking-[-0.28px] lg:text-[28px]">
          {title}
        </h2>
        {description && (
          <p className="mt-1 text-sm leading-[1.4] text-ink-secondary">{description}</p>
        )}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2.5">{actions}</div>}
    </div>
  )
}

/** "Eventos › Novo evento". */
export function Breadcrumb({ items }: { items: { label: string; to?: string }[] }) {
  return (
    <nav aria-label="Caminho" className="flex items-center gap-1.5 text-[13px]">
      {items.map((item, i) => (
        <span key={item.label} className="flex items-center gap-1.5">
          {i > 0 && <ChevronRight size={14} className="text-ink-secondary" aria-hidden />}
          {item.to ? (
            <Link to={item.to} className="text-primary hover:underline">
              {item.label}
            </Link>
          ) : (
            <span className="font-medium" aria-current="page">
              {item.label}
            </span>
          )}
        </span>
      ))}
    </nav>
  )
}

/** Indicator card: icon and label, a big value and a short note. */
export function StatCard({
  icon: Icon,
  iconClassName = 'text-ink',
  label,
  value,
  note,
  noteClassName = 'text-ink-secondary',
  className = '',
  children,
}: {
  icon: Icon
  iconClassName?: string
  label: string
  value: ReactNode
  note?: ReactNode
  noteClassName?: string
  className?: string
  children?: ReactNode
}) {
  return (
    <div
      className={`flex min-w-0 flex-1 flex-col gap-1.5 rounded-[14px] bg-surface px-[18px] py-4 ${className}`}
    >
      <p className="flex items-center gap-2 text-[13px] font-medium text-ink-secondary">
        <Icon size={18} className={`shrink-0 ${iconClassName}`} aria-hidden />
        {label}
      </p>
      <p className="text-[26px] leading-tight font-bold tracking-[-0.26px]">{value}</p>
      {note && <p className={`text-xs ${noteClassName}`}>{note}</p>}
      {children}
    </div>
  )
}

/** Small grey block with a number and a label ("9 reportes"). */
export function Metric({ value, label }: { value: ReactNode; label: string }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-0.5 rounded-[10px] bg-background px-3 py-2.5">
      <span className="text-xl font-bold">{value}</span>
      <span className="text-xs text-ink-secondary">{label}</span>
    </div>
  )
}

/** Square with a soft background holding a category icon. */
export function IconTile({
  icon: Icon,
  className = 'bg-background text-ink',
  size = 18,
}: {
  icon: Icon
  className?: string
  size?: number
}) {
  return (
    <span className={`inline-flex shrink-0 rounded-[10px] p-2 ${className}`}>
      <Icon size={size} aria-hidden />
    </span>
  )
}

export type NoticeTone = 'info' | 'warning' | 'success' | 'ai'

const noticeStyles: Record<NoticeTone, string> = {
  info: 'border-primary-border bg-primary-soft',
  warning: 'border-warning-border bg-warning-soft',
  success: 'border-transparent bg-success-soft',
  ai: 'border-ai-border bg-ai-soft',
}

const noticeIcon: Record<NoticeTone, string> = {
  info: 'text-primary',
  warning: 'text-warning',
  success: 'text-success',
  ai: 'text-ai',
}

/** Highlighted message with an icon, a title and optional action. */
export function Notice({
  tone = 'info',
  icon: Icon,
  title,
  children,
  action,
  className = '',
}: {
  tone?: NoticeTone
  icon: Icon
  title?: ReactNode
  children?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div
      className={`flex items-center gap-3 rounded-[14px] border px-3.5 py-3 ${noticeStyles[tone]} ${className}`}
    >
      <Icon size={22} className={`shrink-0 ${noticeIcon[tone]}`} aria-hidden />
      <div className="min-w-0 flex-1">
        {title && <p className="text-[15px] font-semibold">{title}</p>}
        {children && (
          <div className={`text-[13px] leading-[1.4] text-ink/85 ${title ? 'mt-[3px]' : ''}`}>
            {children}
          </div>
        )}
      </div>
      {action}
    </div>
  )
}

export type Confidence = 'high' | 'medium' | 'low'

const confidenceStyle: Record<Confidence, { label: string; tag: string; bars: number }> = {
  high: { label: 'Confiança alta', tag: 'bg-success-soft text-success', bars: 3 },
  medium: { label: 'Confiança média', tag: 'bg-warning-soft text-warning', bars: 2 },
  low: { label: 'Confiança baixa', tag: 'bg-danger-soft text-danger', bars: 1 },
}

/** Confidence of participant information: bars and text, never colour alone. */
export function ConfidenceBadge({ level }: { level: Confidence }) {
  const style = confidenceStyle[level]
  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1.5 rounded-full px-2 py-[3px] text-[11px] font-semibold whitespace-nowrap ${style.tag}`}
    >
      <span className="flex items-end gap-0.5" aria-hidden>
        {[6, 9, 12].map((h, i) => (
          <span
            key={h}
            style={{ height: h }}
            className={`w-[3px] rounded-[1px] ${i < style.bars ? 'bg-current' : 'bg-inactive'}`}
          />
        ))}
      </span>
      {style.label}
    </span>
  )
}

/** Four-segment crowd meter, always shown next to the level name. */
export function CrowdMeter({
  level,
  stretch = false,
}: {
  level: CrowdLevel
  /** Segments fill the available width instead of 14 px each. */
  stretch?: boolean
}) {
  const { color, segments } = crowdLevels[level]
  return (
    <span className={`flex ${stretch ? 'w-full gap-1' : 'gap-[3px]'}`} aria-hidden>
      {[0, 1, 2, 3].map((i) => (
        <span
          key={i}
          className={`h-1.5 rounded-[3px] ${stretch ? 'flex-1' : 'w-3.5'} ${i < segments ? color : 'bg-border'}`}
        />
      ))}
    </span>
  )
}

/** Horizontal bar on a grey track. */
export function ProgressBar({
  value,
  className = 'bg-primary',
  trackClassName = 'bg-background',
  height = 8,
  label,
}: {
  /** 0 to 1. */
  value: number
  className?: string
  trackClassName?: string
  height?: number
  label?: string
}) {
  const pct = Math.max(0, Math.min(1, value)) * 100
  return (
    <div
      className={`w-full overflow-hidden rounded ${trackClassName}`}
      style={{ height }}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <div className={`h-full rounded ${className}`} style={{ width: `${pct}%` }} />
    </div>
  )
}

/** Event picture, or a gradient when the event has none. */
export function EventThumb({
  image,
  gradient,
  size = 40,
  rounded = 'rounded-lg',
}: {
  image?: string
  gradient: [string, string]
  size?: number
  rounded?: string
}) {
  return (
    <span
      aria-hidden
      className={`block shrink-0 bg-cover bg-center ${rounded}`}
      style={{
        width: size,
        height: size,
        backgroundImage: image
          ? `url(${image})`
          : `linear-gradient(135deg, ${gradient[0]} 14%, ${gradient[1]} 86%)`,
      }}
    />
  )
}

/** Wallet card as participants see it (component "Cartão da carteira"). The
 * status is always written, never only a colour. */
export function TicketCard({
  name,
  dateLabel,
  venue,
  fields,
  status,
  source,
  image,
  gradient,
}: {
  name: string
  dateLabel: string
  venue: string
  fields: { label: string; value: string }[]
  status: string
  source: string
  image?: string
  gradient: [string, string]
}) {
  return (
    <div
      className="relative flex h-[206px] w-full max-w-[324px] flex-col gap-1 overflow-hidden rounded-2xl bg-cover bg-center px-[18px] pt-[18px] pb-4 text-white shadow-[0_-2px_10px_rgba(0,0,0,0.18)]"
      style={{
        backgroundImage: image
          ? `url(${image})`
          : `linear-gradient(148deg, ${gradient[0]} 14%, ${gradient[1]} 86%)`,
      }}
    >
      <div
        aria-hidden
        className="absolute inset-0"
        style={{
          backgroundImage:
            'linear-gradient(180deg, rgba(0,0,0,0.65) 0%, rgba(0,0,0,0.15) 30%, rgba(0,0,0,0.3) 50%, rgba(0,0,0,0.72) 72%, rgba(0,0,0,0.9) 100%)',
        }}
      />
      <div className="relative flex items-center justify-between gap-2">
        <p className="truncate text-[19px] font-bold tracking-[-0.19px]">
          {name || 'Nome do evento'}
        </p>
        <p className="shrink-0 text-[13px] font-semibold tracking-[0.78px]">{dateLabel}</p>
      </div>
      <p className="relative truncate text-[13px] text-white/85">{venue}</p>
      <span className="flex-1" />
      <dl className="relative flex gap-7">
        {fields.map((f) => (
          <div key={f.label} className="flex flex-col gap-0.5">
            <dt className="text-[10px] font-medium tracking-[0.8px] text-white/75">{f.label}</dt>
            <dd className="text-base font-semibold">{f.value}</dd>
          </div>
        ))}
      </dl>
      <div className="relative flex items-center justify-between gap-2 pt-2.5">
        <span className="rounded-full bg-wallet-status-soft px-2.5 py-1 text-xs font-semibold text-wallet-status">
          {status}
        </span>
        <span className="truncate text-[11px] text-white/80">Fonte: {source}</span>
      </div>
    </div>
  )
}

/** Modal dialog. Full screen on phones, centred card on larger screens. */
export function Dialog({
  title,
  onClose,
  children,
  backLabel = 'Voltar',
  phoneTitle = title,
}: {
  title: string
  onClose: () => void
  children: ReactNode
  /** Text of the back link on phones. */
  backLabel?: string
  /** Shorter title for the phone header. */
  phoneTitle?: string
}) {
  const titleId = useId()
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    ref.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      previous?.focus()
    }
  }, [onClose])

  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center sm:bg-black/45 sm:p-6 sm:pt-[12vh]">
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="flex h-full w-full flex-col gap-4 overflow-y-auto bg-surface p-4 outline-none sm:h-auto sm:max-h-[80vh] sm:max-w-[620px] sm:rounded-[20px] sm:p-7 sm:shadow-[0_12px_40px_rgba(0,0,0,0.22)]"
      >
        <div className="grid grid-cols-[1fr_auto_1fr] items-center sm:hidden">
          <button
            type="button"
            onClick={onClose}
            className="justify-self-start text-[17px] text-primary"
          >
            {backLabel}
          </button>
          <p className="text-[17px] font-semibold" aria-hidden>
            {phoneTitle}
          </p>
        </div>
        <div className="flex items-center gap-2 max-sm:sr-only">
          <h2 id={titleId} className="flex-1 text-[22px] font-bold">
            {title}
          </h2>
          <button
            type="button"
            aria-label="Fechar"
            onClick={onClose}
            className="rounded-md p-1 hover:bg-background"
          >
            <X size={20} aria-hidden />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

/** Visible confirmation after an action, also announced to screen readers.
 * The live region is always rendered so the first message is announced. */
export function SuccessBanner({ children }: { children: ReactNode }) {
  return (
    <p
      role="status"
      className={
        children
          ? 'rounded-xl bg-success-soft px-3.5 py-2.5 text-[13px] font-medium text-success'
          : 'sr-only'
      }
    >
      {children}
    </p>
  )
}
