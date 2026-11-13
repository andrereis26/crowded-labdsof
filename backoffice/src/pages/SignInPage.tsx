import { Building2, Check, CircleAlert, Eye, EyeOff, Lock, Mail } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Navigate, useNavigate } from 'react-router'
import festivalImage from '../assets/festival-margens-2026.jpg'
import { contactEmail, validateNewPassword } from '../data/admin'
import type { ChoosePassword, Session, SignIn } from '../data/auth'
import {
  missingRequirements,
  passwordRequirements,
  passwordStrength,
  strengthLevels,
  type PasswordStrength,
} from '../data/passwordStrength'
import { Logo } from '../layout/Shell'
import { Button } from '../ui/components'
import { Field, TextInput, type ControlProps } from '../ui/forms'

/** Split screen of C1 and C4: the photo on the left (on top on phones) and
 * the form on the right. */
function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-surface lg:flex-row">
      <div className="relative flex h-[260px] flex-col justify-between overflow-hidden bg-ink p-5 text-white lg:h-auto lg:w-1/2 lg:p-10">
        <img src={festivalImage} alt="" className="absolute inset-0 size-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-b from-black/30 via-black/20 to-black/80" />
        <div className="relative">
          <Logo />
        </div>
        <div className="relative flex flex-col gap-2 lg:max-w-[440px]">
          <p className="text-[26px] leading-tight font-bold tracking-[-0.3px] lg:text-[36px]">
            Sabe o que se passa nos teus eventos
          </p>
          <p className="text-[15px] leading-[1.4] text-white/85 max-lg:hidden">
            Alterações oficiais, alertas da organização e informação no recinto, num só lugar.
          </p>
        </div>
      </div>
      <main className="flex flex-1 items-start justify-center px-4 py-6 lg:items-center lg:px-10">
        <div className="flex w-full max-w-[400px] flex-col gap-4">{children}</div>
      </main>
    </div>
  )
}

function PasswordInput({
  value,
  onChange,
  autoComplete,
  ...props
}: ControlProps & {
  value: string
  onChange: (value: string) => void
  autoComplete: string
}) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="relative">
      <TextInput
        {...props}
        type={visible ? 'text' : 'password'}
        icon={Lock}
        autoComplete={autoComplete}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="pr-10"
      />
      <button
        type="button"
        aria-label={visible ? 'Esconder palavra-passe' : 'Mostrar palavra-passe'}
        aria-pressed={visible}
        onClick={() => setVisible((v) => !v)}
        className="absolute top-1/2 right-3 -translate-y-1/2 rounded-md p-1 text-ink-secondary hover:text-ink"
      >
        {visible ? <EyeOff size={18} aria-hidden /> : <Eye size={18} aria-hidden />}
      </button>
    </div>
  )
}

function ErrorBox({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div
      role="alert"
      className="flex gap-2.5 rounded-xl border border-danger-border bg-danger-soft px-3.5 py-3 text-danger"
    >
      <CircleAlert size={18} className="mt-px shrink-0" aria-hidden />
      <div>
        <p className="text-sm font-semibold">{title}</p>
        <p className="text-[13px] leading-[1.4]">{children}</p>
      </div>
    </div>
  )
}

/** C1 · Entrar, for organization members and administrators, with Core
 * (`POST /auth/login`). */
export function SignInPage({
  signIn,
  onSignedIn,
  onTemporaryPassword,
}: {
  signIn: SignIn
  onSignedIn: (session: Session) => void
  /** A member signed in with a temporary password: C4 replaces it. */
  onTemporaryPassword: (email: string, temporaryPassword: string) => void
}) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<{ title: string; text: string }>()
  const [forgot, setForgot] = useState(false)
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    const result = await signIn(email, password)
    setBusy(false)
    if (result.kind === 'signed-in') {
      onSignedIn(result.session)
      navigate(result.session.role === 'ADMINISTRATOR' ? '/organizations' : '/organization/events')
    } else if (result.kind === 'new-password') {
      onTemporaryPassword(email.trim(), password)
      navigate('/new-password')
    } else setError({ title: result.title, text: result.message })
  }

  return (
    <AuthLayout>
      <h1 className="text-[26px] font-bold tracking-[-0.3px]">Entrar no Crowded</h1>
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <Field label="Email">
          {(p) => (
            <TextInput
              {...p}
              type="email"
              icon={Mail}
              autoComplete="username"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
                setError(undefined)
              }}
            />
          )}
        </Field>
        <Field label="Palavra-passe">
          {(p) => (
            <PasswordInput
              {...p}
              aria-invalid={error ? true : undefined}
              autoComplete="current-password"
              value={password}
              onChange={(v) => {
                setPassword(v)
                setError(undefined)
              }}
            />
          )}
        </Field>
        <button
          type="button"
          onClick={() => setForgot(true)}
          className="self-start text-[13px] font-semibold text-primary hover:underline"
        >
          Esqueci-me da palavra-passe
        </button>
        {forgot && (
          <p className="rounded-xl bg-primary-soft px-3.5 py-3 text-[13px] leading-[1.4]">
            Pede uma nova palavra-passe temporária ao administrador do Crowded.
          </p>
        )}
        {error && <ErrorBox title={error.title}>{error.text}</ErrorBox>}
        <Button type="submit" size="lg" disabled={busy}>
          Entrar
        </Button>
      </form>

      <div className="flex items-center gap-3 text-xs text-ink-secondary max-lg:hidden">
        <span className="h-px flex-1 bg-border" />
        ou
        <span className="h-px flex-1 bg-border" />
      </div>
      <div className="flex gap-3 rounded-xl border border-border px-3.5 py-3 max-lg:hidden">
        <Building2 size={20} className="mt-1 shrink-0" aria-hidden />
        <div className="flex flex-col gap-1">
          <p className="text-sm font-semibold">Organizas eventos?</p>
          <p className="text-[13px] leading-[1.4] text-ink-secondary">
            Mantém o teu público informado com alterações oficiais e alertas em tempo real. Fala com
            a nossa equipa para criarmos o espaço da tua organização.
          </p>
          <a
            href={`mailto:${contactEmail}`}
            className="text-[13px] font-semibold text-primary hover:underline"
          >
            Contactar a equipa Crowded
          </a>
        </div>
      </div>
      <p className="text-center text-sm text-ink-secondary lg:hidden">
        Organizas eventos?{' '}
        <a href={`mailto:${contactEmail}`} className="font-semibold text-primary">
          Fala com a nossa equipa
        </a>
      </p>
    </AuthLayout>
  )
}

/** The strength of the new password, from Muito fraca to Muito forte, and
 * the requirements of the rule, each ticked once met. */
const strengthColour: Record<PasswordStrength, { bar: string; text: string }> = {
  'very-weak': { bar: 'bg-danger', text: 'text-danger' },
  weak: { bar: 'bg-orange', text: 'text-orange' },
  moderate: { bar: 'bg-warning', text: 'text-warning' },
  strong: { bar: 'bg-success', text: 'text-success' },
  'very-strong': { bar: 'bg-wallet-status', text: 'text-wallet-status' },
}

function PasswordStrengthMeter({ password, id }: { password: string; id: string }) {
  const strength = passwordStrength(password)
  const level = strengthLevels.findIndex((l) => l.strength === strength)
  const missing = missingRequirements(password)
  const colour = strengthColour[strength]
  return (
    <div id={id} className="flex flex-col gap-1.5 text-xs">
      {password && (
        <>
          <div className="flex gap-1" aria-hidden>
            {strengthLevels.map((l, i) => (
              <span
                key={l.strength}
                className={`h-1 flex-1 rounded-full ${i <= level ? colour.bar : 'bg-border'}`}
              />
            ))}
          </div>
          <p className={`font-semibold ${colour.text}`} aria-live="polite">
            Força: {strengthLevels[level].label}
          </p>
        </>
      )}
      <p className="text-ink-secondary">A palavra-passe deve conter:</p>
      <ul className="flex flex-col gap-1">
        {passwordRequirements.map((r) => {
          const met = !missing.includes(r)
          return (
            <li
              key={r.id}
              className={`flex items-center gap-1.5 ${met ? 'text-success' : 'text-ink-secondary'}`}
            >
              {met ? (
                <span className="flex size-3.5 items-center justify-center rounded-full bg-success text-white">
                  <Check size={10} strokeWidth={3} aria-hidden />
                </span>
              ) : (
                <span className="size-3.5 rounded-full border-[1.5px] border-ink-secondary" />
              )}
              <span>
                <span className="sr-only">{met ? 'Cumprido: ' : 'Em falta: '}</span>
                {r.label}
              </span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** C4 · Primeiro acesso: a member signed in with a temporary password must
 * choose a new one before going on. Core replaces it and signs them in
 * (`POST /auth/password`). */
export function NewPasswordPage({
  pending,
  choosePassword,
  onSignedIn,
}: {
  /** The email and temporary password C1 was given; kept in memory only. */
  pending?: { email: string; temporaryPassword: string }
  choosePassword: ChoosePassword
  onSignedIn: (session: Session) => void
}) {
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [errors, setErrors] = useState<ReturnType<typeof validateNewPassword>>({})
  const [refusal, setRefusal] = useState<{ title: string; text: string }>()
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)

  // Once saved, App forgets the temporary password while this page leaves.
  if (!pending) return saved ? null : <Navigate to="/sign-in" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (busy) return
    const found = validateNewPassword(password, confirmation, pending.temporaryPassword)
    setErrors(found)
    setRefusal(undefined)
    if (found.password || found.confirmation) return
    setBusy(true)
    const result = await choosePassword(pending.email, pending.temporaryPassword, password)
    setBusy(false)
    if (result.kind === 'signed-in') {
      setSaved(true)
      onSignedIn(result.session)
      navigate(result.session.role === 'ADMINISTRATOR' ? '/organizations' : '/organization/events')
    } else if (result.kind === 'error') setRefusal({ title: result.title, text: result.message })
  }

  return (
    <AuthLayout>
      <div className="flex flex-col gap-2">
        <h1 className="text-[26px] leading-tight font-bold tracking-[-0.3px]">
          Define a tua nova palavra-passe
        </h1>
        <p className="text-sm leading-[1.4] text-ink-secondary">
          Entraste com uma palavra-passe temporária. Para continuar, escolhe uma nova, que só tu
          vais saber.
        </p>
      </div>
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <Field label="Nova palavra-passe" error={errors.password}>
            {(p) => (
              <PasswordInput
                {...p}
                aria-describedby={[p['aria-describedby'], 'requisitos-palavra-passe']
                  .filter(Boolean)
                  .join(' ')}
                autoComplete="new-password"
                value={password}
                onChange={setPassword}
              />
            )}
          </Field>
          <PasswordStrengthMeter password={password} id="requisitos-palavra-passe" />
        </div>
        <Field label="Confirmar palavra-passe" error={errors.confirmation}>
          {(p) => (
            <PasswordInput
              {...p}
              autoComplete="new-password"
              value={confirmation}
              onChange={setConfirmation}
            />
          )}
        </Field>
        {refusal && <ErrorBox title={refusal.title}>{refusal.text}</ErrorBox>}
        <Button type="submit" size="lg" disabled={busy}>
          Guardar e continuar
        </Button>
      </form>
    </AuthLayout>
  )
}
