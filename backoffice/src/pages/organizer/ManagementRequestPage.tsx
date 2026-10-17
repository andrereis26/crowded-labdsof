import { ChevronLeft, CircleCheck, Clock, Info, RefreshCw, Search, Send } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import {
  catalog,
  eventDateLabel,
  relations,
  type CatalogEvent,
} from '../../data/organizer'
import {
  Breadcrumb,
  Button,
  EventThumb,
  PageHeader,
  Panel,
  SuccessBanner,
  Tag,
} from '../../ui/components'
import { Field, FileInput, RadioDot, RadioOption, Select, TextArea, TextInput } from '../../ui/forms'

const steps = [
  { icon: Clock, text: 'Um administrador revê o pedido, normalmente em até 2 dias úteis.' },
  {
    icon: RefreshCw,
    text: 'O evento continua sincronizado: datas e cancelamentos vêm sempre da bilheteira oficial.',
  },
  { icon: CircleCheck, text: 'Depois de aprovado, podes publicar alertas, lotação, filas e pedidos.' },
]

/** O13 · Pedir gestão de evento sincronizado. */
export function ManagementRequestPage({
  now,
  onSubmit,
}: {
  now: Date
  onSubmit: (event: CatalogEvent, relation: string, document: { name: string; size: number }) => void
}) {
  const [query, setQuery] = useState('Jazz no Parque')
  const [eventId, setEventId] = useState<string>('jazz-no-parque-sabado')
  const [relation, setRelation] = useState<string>(relations[0])
  const [proof, setProof] = useState<{ name: string; size: number }>()
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [sent, setSent] = useState('')

  const results = catalog.filter((e) =>
    e.name.toLowerCase().includes(query.trim().toLowerCase()),
  )
  const chosen = catalog.find((e) => e.id === eventId)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const found: Record<string, string> = {}
    if (!chosen) found.event = 'Escolhe o evento.'
    if (!proof) found.document = 'Carrega um comprovativo da tua relação com o evento.'
    setErrors(found)
    if (!chosen || !proof) return
    onSubmit(chosen, relation, proof)
    setSent(`Pedido enviado para ${chosen.name}. Vais receber a resposta por email.`)
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4 lg:gap-5">
      <Link to="/organizacao/eventos" className="-ml-1.5 flex items-center text-[17px] text-primary lg:hidden">
        <ChevronLeft size={26} aria-hidden />
        Eventos
      </Link>
      <div className="max-lg:hidden">
        <Breadcrumb items={[{ label: 'Eventos', to: '/organizacao/eventos' }, { label: 'Pedir gestão' }]} />
      </div>
      <PageHeader
        title="Pedir gestão de um evento"
        description={
          <>
            <span className="max-sm:hidden">
              Para eventos que chegam ao Crowded a partir da bilheteira. Depois de aprovado, podes
              publicar alertas e informação no recinto.
            </span>
            <span className="sm:hidden">Para eventos sincronizados a partir da bilheteira.</span>
          </>
        }
      />
      <SuccessBanner>{sent}</SuccessBanner>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <Panel title="1. Encontra o evento" titleClassName="text-base" className="gap-3 max-sm:[&>div:first-child]:hidden">
            <Field label={<span className="sr-only">Procurar no catálogo</span>}>
              {(p) => (
                <TextInput
                  {...p}
                  type="search"
                  icon={Search}
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Nome do evento"
                />
              )}
            </Field>
            <div role="radiogroup" aria-label="Eventos encontrados" className="flex flex-col gap-2.5">
              {results.length === 0 && (
                <p className="text-sm text-ink-secondary">Nenhum evento sincronizado com esse nome.</p>
              )}
              {results.map((e) => (
                <RadioOption
                  key={e.id}
                  name="catalog-event"
                  value={e.id}
                  checked={eventId === e.id}
                  onChange={setEventId}
                  card
                  className="!gap-3"
                >
                  <RadioDot checked={eventId === e.id} />
                  <EventThumb gradient={e.gradient} size={48} />
                  <span className="min-w-0 flex-1">
                    <span className="block text-[15px] font-semibold">{e.name}</span>
                    <span className="mt-[3px] block text-xs text-ink-secondary">
                      {eventDateLabel(e, now)} · {e.venue}
                    </span>
                    <span className="mt-1 sm:hidden">
                      <Tag tone="info" icon={RefreshCw}>
                        Sincronizado · bilheteira
                      </Tag>
                    </span>
                  </span>
                  <span className="max-sm:hidden">
                    <Tag tone="info" icon={RefreshCw}>
                      Sincronizado · bilheteira
                    </Tag>
                  </span>
                </RadioOption>
              ))}
              {errors.event && <p className="text-xs text-danger">{errors.event}</p>}
            </div>
          </Panel>

          <Panel
            title="2. Mostra a tua relação com o evento"
            titleClassName="text-base"
            className="gap-3.5 max-sm:[&>div:first-child]:hidden"
          >
            <Field label="Relação">
              {(p) => (
                <Select {...p} value={relation} onChange={(e) => setRelation(e.target.value)} options={relations} />
              )}
            </Field>
            <FileInput
              label="Comprovativo"
              file={proof}
              onChange={setProof}
              detail="Comprovativo"
              help="Contrato, fatura da bilheteira ou outro documento que mostre a tua relação com o evento."
              error={errors.document}
            />
            <Field label="Mensagem para o administrador" optional className="max-sm:hidden">
              {(p) => (
                <TextArea
                  {...p}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Ex.: Somos a organização responsável pelo evento desde 2022."
                />
              )}
            </Field>
          </Panel>

          <p className="flex gap-2.5 rounded-[14px] bg-surface px-4 py-3.5 text-xs text-ink lg:hidden">
            <Info size={16} className="shrink-0 text-primary" aria-hidden />
            Revisão em até 2 dias úteis. As datas continuam a vir da bilheteira oficial.
          </p>

          <div>
            <Button type="submit" icon={Send} disabled={!!sent} className="max-sm:w-full">
              Enviar pedido
            </Button>
          </div>
        </div>

        <Panel title="O que acontece a seguir" className="max-lg:hidden lg:w-[380px] lg:shrink-0">
          <ul className="flex flex-col gap-2.5">
            {steps.map(({ icon: Icon, text }) => (
              <li key={text} className="flex gap-2.5 text-[13px] leading-[1.4]">
                <Icon size={18} className="shrink-0 text-primary" aria-hidden />
                {text}
              </li>
            ))}
          </ul>
        </Panel>
      </div>
    </form>
  )
}
