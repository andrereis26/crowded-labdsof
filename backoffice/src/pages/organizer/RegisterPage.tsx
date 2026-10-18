import { ChartNoAxesColumn, CircleCheck, Clock, Info, Megaphone, Plus, ShieldCheck, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import {
  validateRegistration,
  type RegistrationForm,
} from '../../data/verifications'
import { Logo } from '../../layout/Shell'
import { Button, ButtonLink, Notice, type Icon } from '../../ui/components'
import { Checkbox, Field, FileInput, TextInput } from '../../ui/forms'

const benefits: { icon: Icon; title: string; text: string }[] = [
  { icon: Megaphone, title: 'Alterações oficiais e alertas', text: 'Chegam a quem tem bilhete ou segue o evento.' },
  { icon: Users, title: 'O que se passa no recinto', text: 'Reportes dos participantes, agrupados e com confiança.' },
  { icon: ChartNoAxesColumn, title: 'Planeamento com dados', text: 'Sondagens de presença e contador de entradas.' },
]

const empty: RegistrationForm = {
  organization: '',
  nipc: '',
  website: '',
  responsible: '',
  email: '',
  confirmed: false,
}

/** O1 · Registo da organização. */
export function RegisterPage({ onSubmit }: { onSubmit: (form: RegistrationForm) => void }) {
  const [form, setForm] = useState<RegistrationForm>(empty)
  const [errors, setErrors] = useState<ReturnType<typeof validateRegistration>>({})
  const navigate = useNavigate()

  const set = <K extends keyof RegistrationForm>(key: K, value: RegistrationForm[K]) => {
    setForm((f) => ({ ...f, [key]: value }))
    setErrors((e) => ({ ...e, [key]: undefined }))
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const found = validateRegistration(form)
    setErrors(found)
    if (Object.keys(found).length) return
    onSubmit(form)
    navigate('/registo/pendente', { state: { organization: form.organization.trim(), at: new Date() } })
  }

  return (
    <div className="min-h-screen bg-surface lg:flex">
      <aside className="hidden w-[560px] shrink-0 flex-col gap-7 bg-sidebar p-14 lg:sticky lg:top-0 lg:flex lg:h-screen">
        <Logo />
        <span className="h-[60px]" />
        <p className="text-4xl leading-[1.15] font-bold tracking-[-0.36px] text-white">
          Mantém o teu público informado quando algo muda
        </p>
        {benefits.map(({ icon: Icon, title, text }) => (
          <div key={title} className="flex gap-3.5">
            <span className="inline-flex h-fit rounded-xl bg-sidebar-card p-2.5 text-white">
              <Icon size={20} aria-hidden />
            </span>
            <div>
              <p className="text-base font-semibold text-white">{title}</p>
              <p className="mt-[3px] text-sm text-sidebar-muted">{text}</p>
            </div>
          </div>
        ))}
        <span className="flex-1" />
        <Link to="/organizacao/eventos" className="text-sm font-medium text-sidebar-accent hover:underline">
          Já tens conta de organização? Entrar
        </Link>
      </aside>

      <main className="flex flex-1 justify-center px-4 pt-6 pb-10 lg:items-start lg:px-10 lg:pt-[71px]">
        <form onSubmit={submit} noValidate className="flex w-full max-w-[560px] flex-col gap-4">
          <div className="lg:hidden">
            <Logo dark />
          </div>
          <p className="flex items-center gap-2">
            <span className="rounded-full bg-primary-soft px-2 py-[3px] text-[11px] font-semibold text-primary">
              PASSO 1 DE 2
            </span>
            <span className="text-xs text-ink-secondary max-sm:hidden">Depois verificamos a organização</span>
          </p>
          <div>
            <h1 className="text-[26px] font-bold tracking-[-0.3px] lg:text-[30px]">Registar organização</h1>
            <p className="mt-2 text-[15px] text-ink-secondary">
              Só organizações verificadas podem publicar informação oficial e alertas.
            </p>
          </div>

          <Field label="Nome da organização" error={errors.organization}>
            {(p) => (
              <TextInput
                {...p}
                autoComplete="organization"
                value={form.organization}
                onChange={(e) => set('organization', e.target.value)}
              />
            )}
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="NIPC" error={errors.nipc}>
              {(p) => (
                <TextInput
                  {...p}
                  inputMode="numeric"
                  placeholder="500 000 000"
                  value={form.nipc}
                  onChange={(e) => set('nipc', e.target.value)}
                />
              )}
            </Field>
            <Field label="Site" error={errors.website}>
              {(p) => (
                <TextInput
                  {...p}
                  inputMode="url"
                  placeholder="organizacao.pt"
                  value={form.website}
                  onChange={(e) => set('website', e.target.value)}
                />
              )}
            </Field>
            <Field label="Responsável" error={errors.responsible}>
              {(p) => (
                <TextInput
                  {...p}
                  autoComplete="name"
                  value={form.responsible}
                  onChange={(e) => set('responsible', e.target.value)}
                />
              )}
            </Field>
            <Field label="Email profissional" error={errors.email}>
              {(p) => (
                <TextInput
                  {...p}
                  type="email"
                  autoComplete="email"
                  value={form.email}
                  onChange={(e) => set('email', e.target.value)}
                />
              )}
            </Field>
          </div>
          <FileInput
            label="Comprovativo da organização"
            file={form.document}
            onChange={(file) => set('document', file)}
            detail="carregado"
            help="Certidão permanente, declaração de início de atividade ou documento equivalente."
            error={errors.document}
          />
          <Checkbox checked={form.confirmed} onChange={(v) => set('confirmed', v)} error={errors.confirmed}>
            Confirmo que represento esta organização e que os dados são verdadeiros.
          </Checkbox>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <Button type="submit" size="lg">
              Submeter para verificação
            </Button>
            <p className="text-xs text-ink-secondary">Normalmente demora até 2 dias úteis.</p>
          </div>
          <Link to="/organizacao/eventos" className="text-center text-sm font-medium text-primary lg:hidden">
            Já tens conta de organização? Entrar
          </Link>
        </form>
      </main>
    </div>
  )
}

/** O1 · Verificação pendente, depois de submeter o registo. */
export function RegistrationPendingPage() {
  const location = useLocation()
  const state = location.state as { organization?: string; at?: Date } | null
  const at = state?.at ? new Date(state.at) : undefined
  const time = at?.toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' })
  const steps: { icon: Icon; className: string; title: string; text: string }[] = [
    { icon: CircleCheck, className: 'text-success', title: 'Pedido submetido', text: time ? `Hoje · ${time}` : 'Recebido' },
    { icon: Clock, className: 'text-warning', title: 'Em análise por um administrador', text: 'Verificamos o NIPC, o site e o documento' },
    { icon: ShieldCheck, className: 'text-ink-secondary', title: 'Organização verificada', text: 'Podes publicar eventos, alertas e informação oficial' },
  ]

  return (
    <main className="flex min-h-screen justify-center bg-surface px-4 pt-[72px] pb-10">
      <div className="flex w-full max-w-[420px] flex-col gap-[18px]">
        <div className="flex flex-col items-center gap-3 text-center">
          <span className="inline-flex rounded-full bg-warning-soft p-[18px] text-warning">
            <ShieldCheck size={36} aria-hidden />
          </span>
          <h1 className="text-[26px] font-bold">Verificação em curso</h1>
          <p className="text-[15px] leading-[1.4] text-ink-secondary">
            Recebemos o pedido{state?.organization ? ` da ${state.organization}` : ''}. Normalmente
            demora até 2 dias úteis.
          </p>
        </div>
        <ol className="rounded-[14px] bg-background px-4 py-1.5">
          {steps.map(({ icon: Icon, className, title, text }) => (
            <li key={title} className="flex gap-3 border-b border-border py-3 last:border-b-0">
              <Icon size={22} className={`shrink-0 ${className}`} aria-hidden />
              <div>
                <p className="text-sm font-semibold">{title}</p>
                <p className="mt-0.5 text-xs text-ink-secondary">{text}</p>
              </div>
            </li>
          ))}
        </ol>
        <Notice icon={Info} title="Enquanto esperas">
          Podes preparar eventos em rascunho. Só ficam públicos depois da verificação.
        </Notice>
        <ButtonLink to="/organizacao/eventos/novo" size="lg" icon={Plus}>
          Criar evento em rascunho
        </ButtonLink>
        <p className="text-center text-sm text-ink-secondary">
          Vais receber a decisão por email.
        </p>
      </div>
    </main>
  )
}
