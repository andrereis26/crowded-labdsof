import { Mail, Plus, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  dayMonth,
  lastAccessLabel,
  longDate,
  memberStatusTag,
  validateMember,
  validateOrganizationName,
  type AdminOrganization,
  type Member,
} from '../../data/admin'
import { adminErrorMessage, type MemberChange } from '../../data/adminApi'
import {
  Button,
  Dialog,
  EventThumb,
  Notice,
  PageHeader,
  SuccessBanner,
  Tag,
} from '../../ui/components'
import { Field, Select, TextInput } from '../../ui/forms'

type Modal =
  | { kind: 'organization' }
  | { kind: 'member' }
  | { kind: 'password'; member: Member; created: boolean }
  | { kind: 'deactivate'; member: Member }

const headCell = 'py-2.5 text-left text-[11px] font-semibold tracking-[0.44px] uppercase'

/** What Core answered: the value, or the error code of its refusal. */
export type Outcome<T> = { ok: true; value: T } | { ok: false; code: string }

/** A2 · Administração da plataforma: organizations and their members, from
 * Core (`/admin/...`). */
export function OrganizationsPage({
  organizations,
  members,
  now,
  onCreateOrganization,
  onCreateMember,
  onChangeMember,
}: {
  organizations: AdminOrganization[]
  members: Member[]
  now: Date
  /** Resolves to the new organization's id. */
  onCreateOrganization: (name: string) => Promise<Outcome<string>>
  /** Resolves to the member with their temporary password, shown once. */
  onCreateMember: (organizationId: string, name: string, email: string) => Promise<Outcome<Member>>
  /** Resolves to the changed member, with a new temporary password if one was issued. */
  onChangeMember: (member: Member, change: MemberChange) => Promise<Outcome<Member>>
}) {
  const [selectedId, setSelectedId] = useState(organizations[0]?.id)
  const [modal, setModal] = useState<Modal>()
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const selected = organizations.find((o) => o.id === selectedId) ?? organizations[0]
  const orgMembers = members.filter((m) => m.organizationId === selected?.id)
  const close = () => setModal(undefined)
  const countOf = (o: AdminOrganization) => members.filter((m) => m.organizationId === o.id).length

  const change = async (member: Member, what: MemberChange, done: (m: Member) => void) => {
    setMessage('')
    setError('')
    const result = await onChangeMember(member, what)
    if (result.ok) done(result.value)
    else setError(adminErrorMessage(result.code))
  }

  const newPassword = (member: Member) =>
    change(member, { resetPassword: true }, (updated) =>
      setModal({ kind: 'password', member: updated, created: false }),
    )

  const reactivate = (member: Member) =>
    change(member, { active: true }, () => setMessage(`Conta de ${member.name} reativada.`))

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title="Organizações"
        description={`${organizations.length} organizações · ${members.length} membros`}
        actions={
          <Button size="lg" icon={Plus} onClick={() => setModal({ kind: 'organization' })}>
            Criar organização
          </Button>
        }
      />
      <SuccessBanner>{message}</SuccessBanner>
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}

      <div className="overflow-x-auto rounded-[14px] bg-surface py-1">
        <table className="w-full min-w-[640px] text-sm">
          <thead>
            <tr className="border-b border-border text-ink-secondary">
              <th className={`${headCell} px-5`}>Organização</th>
              <th className={`${headCell} w-[160px]`}>Criada</th>
              <th className={`${headCell} w-[120px]`}>Estado</th>
              <th className={`${headCell} w-[100px]`}>Membros</th>
              <th className={`${headCell} w-[100px] pr-5`}>Eventos</th>
            </tr>
          </thead>
          <tbody>
            {organizations.map((o) => (
              <tr
                key={o.id}
                className={`border-b border-border last:border-b-0 ${o.id === selected?.id ? 'bg-row-highlight' : ''}`}
              >
                <td className="px-5 py-3">
                  <button
                    type="button"
                    aria-pressed={o.id === selected?.id}
                    onClick={() => setSelectedId(o.id)}
                    className="flex items-center gap-2 text-left font-semibold hover:underline"
                  >
                    <EventThumb image={o.image} gradient={o.gradient} />
                    {o.name}
                  </button>
                </td>
                <td className="py-3">{longDate(o.createdAt)}</td>
                <td className="py-3">
                  <Tag tone="success" dot>
                    Ativa
                  </Tag>
                </td>
                <td className="py-3">{countOf(o)}</td>
                <td className="py-3 pr-5">{o.events}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selected && (
        <>
          <PageHeader
            title={<span className="text-[22px]">Membros · {selected.name}</span>}
            description="Os membros entram com o email e uma palavra-passe temporária, que alteram no primeiro acesso."
            actions={
              <Button size="lg" icon={Plus} onClick={() => setModal({ kind: 'member' })}>
                Criar membro
              </Button>
            }
          />
          <div className="overflow-x-auto rounded-[14px] bg-surface py-1">
            <table
              className="w-full min-w-[760px] text-sm"
              aria-label={`Membros de ${selected.name}`}
            >
              <thead>
                <tr className="border-b border-border text-ink-secondary">
                  <th className={`${headCell} px-5`}>Membro</th>
                  <th className={headCell}>Email</th>
                  <th className={headCell}>Estado</th>
                  <th className={`${headCell} w-[130px]`}>Último acesso</th>
                  <th className={`${headCell} pr-5`}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {orgMembers.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-5 py-4 text-ink-secondary">
                      Esta organização ainda não tem membros.
                    </td>
                  </tr>
                )}
                {orgMembers.map((m) => {
                  const tag = memberStatusTag(m, now)
                  const action = 'font-medium text-primary hover:underline'
                  return (
                    <tr key={m.id} className="border-b border-border last:border-b-0">
                      <td className="px-5 py-3 font-semibold">{m.name}</td>
                      <td className="py-3">{m.email}</td>
                      <td className="py-3">
                        <Tag tone={tag.tone} dot={tag.dot}>
                          {tag.label}
                        </Tag>
                      </td>
                      <td className="py-3">{lastAccessLabel(m.lastAccess, now)}</td>
                      <td className="py-3 pr-5 whitespace-nowrap">
                        {m.status === 'disabled' ? (
                          <button
                            type="button"
                            className={action}
                            aria-label={`Reativar ${m.name}`}
                            onClick={() => reactivate(m)}
                          >
                            Reativar
                          </button>
                        ) : (
                          <>
                            <button
                              type="button"
                              className={action}
                              aria-label={`Nova palavra-passe para ${m.name}`}
                              onClick={() => newPassword(m)}
                            >
                              Nova palavra-passe
                            </button>
                            <span className="text-primary"> · </span>
                            <button
                              type="button"
                              className={action}
                              aria-label={`Desativar ${m.name}`}
                              onClick={() => setModal({ kind: 'deactivate', member: m })}
                            >
                              Desativar
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </>
      )}

      {modal?.kind === 'organization' && (
        <CreateOrganizationDialog
          organizations={organizations}
          onClose={close}
          onCreate={async (name) => {
            const result = await onCreateOrganization(name)
            if (!result.ok) return adminErrorMessage(result.code)
            setSelectedId(result.value)
            setMessage(`${name.trim()} criada.`)
            close()
            return undefined
          }}
        />
      )}
      {modal?.kind === 'member' && selected && (
        <CreateMemberDialog
          organizations={organizations}
          organizationId={selected.id}
          members={members}
          onClose={close}
          onCreate={async (organizationId, name, email) => {
            const result = await onCreateMember(organizationId, name, email)
            if (!result.ok) {
              return result.code === 'EMAIL_ALREADY_REGISTERED'
                ? { email: adminErrorMessage(result.code) }
                : { name: adminErrorMessage(result.code) }
            }
            setSelectedId(organizationId)
            setMessage('')
            setModal({ kind: 'password', member: result.value, created: true })
            return {}
          }}
        />
      )}
      {modal?.kind === 'password' && (
        <PasswordDialog
          member={modal.member}
          created={modal.created}
          organization={organizations.find((o) => o.id === modal.member.organizationId)?.name ?? ''}
          onClose={close}
        />
      )}
      {modal?.kind === 'deactivate' && (
        <Dialog title={`Desativar ${modal.member.name}?`} backLabel="Cancelar" onClose={close}>
          <p className="text-sm leading-[1.45] text-ink/85">
            {modal.member.name} deixa de conseguir entrar no backoffice. Os eventos e os alertas de{' '}
            {selected?.name} mantêm-se. Podes reativar a conta mais tarde.
          </p>
          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <Button variant="secondary" size="lg" onClick={close} className="max-sm:hidden">
              Cancelar
            </Button>
            <Button
              variant="danger"
              size="lg"
              onClick={() => {
                const member = modal.member
                close()
                void change(member, { active: false }, () =>
                  setMessage(`Conta de ${member.name} desativada.`),
                )
              }}
            >
              Desativar
            </Button>
          </div>
        </Dialog>
      )}
    </div>
  )
}

function DialogActions({
  submit,
  busy,
  onClose,
}: {
  submit: string
  busy: boolean
  onClose: () => void
}) {
  return (
    <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
      <Button variant="secondary" size="lg" onClick={onClose} className="max-sm:hidden">
        Cancelar
      </Button>
      <Button type="submit" size="lg" disabled={busy}>
        {submit}
      </Button>
    </div>
  )
}

function CreateOrganizationDialog({
  organizations,
  onClose,
  onCreate,
}: {
  organizations: AdminOrganization[]
  onClose: () => void
  /** Resolves to an error for the name, or undefined once created. */
  onCreate: (name: string) => Promise<string | undefined>
}) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const found = validateOrganizationName(name, organizations)
    setError(found)
    if (found || busy) return
    setBusy(true)
    const refused = await onCreate(name.trim())
    setBusy(false)
    setError(refused)
  }

  return (
    <Dialog title="Criar organização" backLabel="Cancelar" onClose={onClose}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <Field label="Nome da organização" error={error}>
          {(p) => (
            <TextInput
              {...p}
              value={name}
              onChange={(e) => {
                setName(e.target.value)
                setError(undefined)
              }}
            />
          )}
        </Field>
        <Notice icon={Users} title="A organização começa sem membros">
          Depois de a criares, adiciona os membros que vão gerir os eventos.
        </Notice>
        <DialogActions submit="Criar organização" busy={busy} onClose={onClose} />
      </form>
    </Dialog>
  )
}

function CreateMemberDialog({
  organizations,
  organizationId,
  members,
  onClose,
  onCreate,
}: {
  organizations: AdminOrganization[]
  organizationId: string
  members: Member[]
  onClose: () => void
  /** Resolves to the errors of the fields Core refused; empty once created. */
  onCreate: (
    organizationId: string,
    name: string,
    email: string,
  ) => Promise<ReturnType<typeof validateMember>>
}) {
  const [orgId, setOrgId] = useState(organizationId)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [errors, setErrors] = useState<ReturnType<typeof validateMember>>({})
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const found = validateMember({ name, email }, members)
    setErrors(found)
    if (found.name || found.email || busy) return
    setBusy(true)
    const refused = await onCreate(orgId, name.trim(), email.trim().toLowerCase())
    setBusy(false)
    setErrors(refused)
  }

  return (
    <Dialog title="Criar membro" backLabel="Cancelar" onClose={onClose}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        <Field label="Organização">
          {(p) => (
            <Select
              {...p}
              value={orgId}
              onChange={(e) => setOrgId(e.target.value)}
              options={organizations.map((o) => ({ value: o.id, label: o.name }))}
            />
          )}
        </Field>
        <Field label="Nome" error={errors.name}>
          {(p) => (
            <TextInput
              {...p}
              value={name}
              onChange={(e) => {
                setName(e.target.value)
                setErrors((all) => ({ ...all, name: undefined }))
              }}
            />
          )}
        </Field>
        <Field label="Email" error={errors.email}>
          {(p) => (
            <TextInput
              {...p}
              type="email"
              icon={Mail}
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
                setErrors((all) => ({ ...all, email: undefined }))
              }}
            />
          )}
        </Field>
        <Notice icon={Users} title="Palavra-passe temporária">
          Geramos uma palavra-passe temporária, válida durante 7 dias. O membro define a sua no
          primeiro acesso.
        </Notice>
        <DialogActions submit="Criar membro" busy={busy} onClose={onClose} />
      </form>
    </Dialog>
  )
}

/** Shows a temporary password once: after "Concluído" it cannot be read
 * again, only replaced. */
function PasswordDialog({
  member,
  organization,
  created,
  onClose,
}: {
  member: Member
  organization: string
  created: boolean
  onClose: () => void
}) {
  const [copied, setCopied] = useState(false)
  const password = member.temporaryPassword ?? ''
  const expires = member.temporaryExpiresAt ? dayMonth(member.temporaryExpiresAt) : ''

  return (
    <Dialog
      title={created ? 'Membro criado' : 'Nova palavra-passe temporária'}
      backLabel="Fechar"
      onClose={onClose}
    >
      <p className="text-sm text-ink-secondary">
        {created
          ? `${member.name} já é membro de ${organization}.`
          : `Para ${member.name} · ${organization}.`}
      </p>
      <div className="flex items-center gap-3 rounded-xl border border-border bg-background px-3.5 py-3">
        <div className="min-w-0 flex-1">
          <p className="text-xs text-ink-secondary">Palavra-passe temporária</p>
          <p
            className="font-mono text-[22px] font-semibold tracking-[0.12em]"
            aria-label="Palavra-passe temporária"
          >
            {password}
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            void navigator.clipboard?.writeText(password)
            setCopied(true)
          }}
          className="text-[13px] font-semibold text-primary hover:underline"
        >
          {copied ? 'Copiada' : 'Copiar'}
        </button>
      </div>
      <Field label="Email">
        {(p) => <TextInput {...p} icon={Mail} value={member.email} readOnly />}
      </Field>
      <Notice tone="warning" icon={Users} title="Só mostramos esta palavra-passe agora">
        {created
          ? `Expira a ${expires}, daqui a 7 dias. Envia-a a ${member.name} por um canal seguro. No primeiro acesso, define uma nova.`
          : `A anterior deixou de funcionar. Esta expira a ${expires}, daqui a 7 dias, e ${member.name} define uma nova no primeiro acesso.`}
      </Notice>
      <div className="flex justify-end">
        <Button size="lg" onClick={onClose}>
          Concluído
        </Button>
      </div>
    </Dialog>
  )
}
