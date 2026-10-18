import {
  ChevronLeft,
  CircleCheck,
  CircleX,
  ExternalLink,
  FileText,
  Mail,
  TriangleAlert,
} from 'lucide-react'
import { useId, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  decide,
  initials,
  isOpen,
  maskNipc,
  submittedLabel,
  timeAgo,
  type Decision,
  type VerificationRequest,
  type VerificationStatus,
} from '../data/verifications'
import { Avatar, Button, Chip, Panel, Tag, type TagTone } from '../ui/components'

type Filter = 'open' | 'approved' | 'rejected'

const statusTag: Record<VerificationStatus, { label: string; tone: TagTone }> = {
  pending: { label: 'Pendente', tone: 'warning' },
  'info-requested': { label: 'Aguarda informação', tone: 'info' },
  approved: { label: 'Aprovada', tone: 'success' },
  rejected: { label: 'Rejeitada', tone: 'danger' },
}

function matches(r: VerificationRequest, filter: Filter): boolean {
  if (filter === 'open') return isOpen(r)
  return r.status === filter
}

/** A1 · Fila de verificação de organizações e detalhe. */
export function VerificationsPage({
  requests,
  selectedId,
  now,
  onChange,
}: {
  requests: VerificationRequest[]
  /** From the URL; on small screens the page shows either the list or one
   * request. */
  selectedId?: string
  now: Date
  onChange: (updated: VerificationRequest) => void
}) {
  const [filter, setFilter] = useState<Filter>('open')
  const [message, setMessage] = useState('')
  const navigate = useNavigate()

  const visible = requests.filter((r) => matches(r, filter))
  const selected =
    requests.find((r) => r.id === selectedId) ?? (selectedId ? undefined : visible[0])

  const handleDecision = (updated: VerificationRequest, decision: Decision) => {
    onChange(updated)
    const verb = {
      approve: 'aprovada',
      'request-info': 'notificada para enviar mais informação',
      reject: 'rejeitada',
    }[decision]
    setMessage(`${updated.organization} ${verb}.`)
    const next = requests.find((r) => r.id !== updated.id && matches(r, filter))
    navigate(next ? `/verificacoes/${next.id}` : '/verificacoes')
  }

  const counts = {
    open: requests.filter(isOpen).length,
    approved: requests.filter((r) => r.status === 'approved').length,
    rejected: requests.filter((r) => r.status === 'rejected').length,
  }

  return (
    <div className="flex flex-col gap-5">
      <div
        className={`flex flex-wrap items-center justify-between gap-4 ${selectedId ? 'max-lg:hidden' : ''}`}
      >
        <div>
          <h2 className="text-[28px] leading-tight font-bold tracking-[-0.28px]">
            Verificação de organizações
          </h2>
          <p className="text-sm text-ink-secondary">
            Só organizações verificadas podem publicar informação oficial e
            alertas.
          </p>
        </div>
        <div className="flex gap-2.5" role="group" aria-label="Filtrar pedidos">
          <Chip selected={filter === 'open'} onClick={() => setFilter('open')}>
            Pendentes · {counts.open}
          </Chip>
          <Chip
            selected={filter === 'approved'}
            onClick={() => setFilter('approved')}
          >
            Aprovadas
          </Chip>
          <Chip
            selected={filter === 'rejected'}
            onClick={() => setFilter('rejected')}
          >
            Rejeitadas
          </Chip>
        </div>
      </div>

      <p role="status" className="sr-only">
        {message}
      </p>

      <div className="flex items-start gap-6">
        <ul
          aria-label="Pedidos"
          className={`flex w-full flex-col gap-1.5 rounded-[14px] bg-surface p-2.5 lg:w-[380px] lg:shrink-0 ${selectedId ? 'max-lg:hidden' : ''}`}
        >
          {visible.length === 0 && (
            <li className="p-3 text-sm text-ink-secondary">
              Sem pedidos nesta lista.
            </li>
          )}
          {visible.map((r) => (
            <li key={r.id}>
              <Link
                to={`/verificacoes/${r.id}`}
                aria-current={selected?.id === r.id ? 'true' : undefined}
                className={`flex items-center gap-3 rounded-[10px] border p-3 focus-visible:outline-2 focus-visible:outline-primary ${
                  selected?.id !== r.id
                    ? 'border-transparent hover:bg-background'
                    : selectedId
                      ? 'border-primary-border bg-primary-soft'
                      : // Without an id in the URL, only desktop shows the detail.
                        'border-transparent lg:border-primary-border lg:bg-primary-soft'
                }`}
              >
                <Avatar initials={initials(r.organization)} />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold">{r.organization}</p>
                  <p className="text-xs text-ink-secondary">
                    {r.kind === 'management'
                      ? `Pedido de gestão · ${r.event}`
                      : `Registo · ${timeAgo(r.submittedAt, now)}`}
                  </p>
                </div>
                {r.kind === 'management' && isOpen(r) ? (
                  <Tag tone="info">Gestão</Tag>
                ) : (
                  <Tag tone={statusTag[r.status].tone}>
                    {statusTag[r.status].label}
                  </Tag>
                )}
              </Link>
            </li>
          ))}
        </ul>

        {selected ? (
          <RequestDetail
            key={selected.id}
            request={selected}
            now={now}
            hiddenOnMobile={!selectedId}
            onDecision={handleDecision}
          />
        ) : (
          selectedId && (
            <p className="text-sm text-ink-secondary">
              Não encontrámos este pedido.{' '}
              <Link to="/verificacoes" className="font-semibold text-primary">
                Voltar à lista
              </Link>
            </p>
          )
        )}
      </div>
    </div>
  )
}

function RequestDetail({
  request: r,
  now,
  hiddenOnMobile,
  onDecision,
}: {
  request: VerificationRequest
  now: Date
  hiddenOnMobile: boolean
  onDecision: (updated: VerificationRequest, decision: Decision) => void
}) {
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const noteId = useId()
  const errorId = useId()
  const tag = statusTag[r.status]

  const submit = (decision: Decision) => {
    const result = decide(r, decision, note)
    if ('error' in result) {
      setError(result.error)
      return
    }
    onDecision(result, decision)
  }

  const fields = [
    ['NIPC', maskNipc(r.nipc)],
    ['Email', r.email],
    ['Site', r.website],
  ]

  return (
    <div
      className={`flex min-w-0 flex-1 flex-col gap-4 ${hiddenOnMobile ? 'max-lg:hidden' : ''}`}
    >
      <Link
        to="/verificacoes"
        className="-ml-1.5 flex items-center text-[17px] text-primary lg:hidden"
      >
        <ChevronLeft size={26} aria-hidden />
        Verificações
      </Link>

      <Panel className="gap-3.5">
        <div className="flex items-center gap-3.5">
          <Avatar initials={initials(r.organization)} size={48} />
          <div className="min-w-0 flex-1">
            <h2 className="text-lg leading-tight font-bold lg:text-xl">{r.organization}</h2>
            <p className="text-[13px] text-ink-secondary">
              {r.kind === 'management'
                ? `Pedido de gestão de “${r.event}” · `
                : ''}
              Submetido {submittedLabel(r.submittedAt, now)} por {r.submittedBy}
            </p>
          </div>
          <Tag tone={tag.tone} dot>
            {tag.label}
          </Tag>
        </div>
        <dl className="grid gap-2 sm:grid-cols-3 sm:gap-4">
          {fields.map(([label, value]) => (
            <div
              key={label}
              className="flex justify-between gap-2 rounded-[10px] sm:flex-col sm:justify-start sm:bg-background sm:px-3 sm:py-2.5"
            >
              <dt className="text-[13px] text-ink-secondary sm:text-[11px] sm:font-semibold sm:tracking-[0.44px] sm:uppercase">
                {label}
              </dt>
              <dd className="text-[13px] font-semibold break-all sm:text-sm sm:font-medium">
                {value}
              </dd>
            </div>
          ))}
        </dl>
      </Panel>

      <div className="flex flex-col gap-4 xl:flex-row xl:items-start">
        <Panel title="Verificações automáticas" className="flex-1">
          <ul className="flex flex-col gap-2.5">
            {r.checks.map((c) => (
              <li key={c.label} className="flex items-center gap-2.5 text-sm font-medium">
                {c.passed ? (
                  <CircleCheck size={20} className="shrink-0 text-success" aria-label="Passou" />
                ) : (
                  <TriangleAlert size={20} className="shrink-0 text-warning" aria-label="A confirmar" />
                )}
                {c.label}
              </li>
            ))}
          </ul>
        </Panel>
        <Panel title="Documento" className="flex-1">
          <a
            href="#"
            onClick={(e) => e.preventDefault()}
            className="flex items-center gap-2.5 rounded-[10px] border border-border px-3.5 py-3 hover:bg-background"
          >
            <FileText size={22} className="shrink-0 text-danger" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-semibold">
                {r.document.name}
              </span>
              <span className="block text-xs text-ink-secondary">
                {r.document.size} · {r.document.pages} páginas
              </span>
            </span>
            <span className="text-[13px] font-semibold text-primary">Abrir</span>
            <ExternalLink size={16} className="text-primary" aria-hidden />
          </a>
        </Panel>
      </div>

      {isOpen(r) ? (
        <Panel title="Decisão" className="gap-3">
          <label htmlFor={noteId} className="text-[13px] font-medium">
            Nota para a organização (obrigatória se pedires mais informação ou
            rejeitares)
          </label>
          <textarea
            id={noteId}
            value={note}
            onChange={(e) => {
              setNote(e.target.value)
              setError('')
            }}
            rows={2}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            placeholder="Ex.: Envia um documento com o nome “Organização Margens”."
            className={`w-full resize-y rounded-[10px] border bg-surface px-3.5 py-3 text-[15px] placeholder:text-ink-secondary focus:outline-2 focus:outline-primary ${
              error ? 'border-danger' : 'border-border'
            }`}
          />
          {error && (
            <p id={errorId} className="text-xs text-danger">
              {error}
            </p>
          )}
          <div className="flex flex-col gap-2.5 sm:flex-row">
            <Button variant="approve" icon={CircleCheck} onClick={() => submit('approve')}>
              Aprovar
            </Button>
            <Button variant="secondary" icon={Mail} onClick={() => submit('request-info')}>
              Pedir mais informação
            </Button>
            <Button variant="danger" icon={CircleX} onClick={() => submit('reject')}>
              Rejeitar
            </Button>
          </div>
        </Panel>
      ) : (
        r.note && (
          <Panel title="Nota enviada">
            <p className="text-sm">{r.note}</p>
          </Panel>
        )
      )}
    </div>
  )
}
