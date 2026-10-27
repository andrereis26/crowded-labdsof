import { Lock } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  categories,
  reportsToday,
  type ConfirmationSettings,
  type IncidentCategory,
  type OrgEvent,
} from '../../data/organizer'
import { Button, IconTile, PageHeader, Panel, SuccessBanner, Tag } from '../../ui/components'
import { Field, Select, TextInput, Toggle } from '../../ui/forms'
import { categoryIcon } from '../../ui/categories'

const radii = ['200 m', '500 m', '1 km']
const limits = ['1 por hora', '3 por hora', '5 por hora']

/** O10 · Categorias que pedem confirmação. */
export function ConfirmationsPage({
  event,
  settings,
  onSave,
}: {
  event: OrgEvent
  settings: ConfirmationSettings
  onSave: (settings: ConfirmationSettings) => void
}) {
  const [draft, setDraft] = useState(settings)
  const [answers, setAnswers] = useState(String(settings.highConfidenceAnswers))
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const n = Number(answers)
    if (!Number.isInteger(n) || n < 2 || n > 50) {
      setError('Escolhe um número entre 2 e 50.')
      return
    }
    setError('')
    onSave({ ...draft, highConfidenceAnswers: n })
    setMessage('Alterações guardadas.')
  }

  const toggle = (c: IncidentCategory, on: boolean) =>
    setDraft((d) => ({ ...d, categories: { ...d.categories, [c]: on } }))

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4 lg:gap-5">
      <PageHeader
        title={
          <>
            <span className="max-sm:hidden">Reportes e confirmações</span>
            <span className="sm:hidden">Confirmações</span>
          </>
        }
        description="Escolhe que categorias pedem confirmação a outros participantes no recinto."
      />
      <SuccessBanner>{message}</SuccessBanner>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <Panel
          title="Pedir confirmação para…"
          titleClassName="text-base"
          className="min-w-0 flex-1 gap-0 !px-5 !py-3.5 max-sm:[&>div:first-child]:hidden"
          action={<span className="text-xs text-ink-secondary">{event.name}</span>}
        >
          <ul>
            {(Object.keys(categories) as IncidentCategory[]).map((c) => {
              const category = categories[c]
              return (
                <li
                  key={c}
                  className="flex items-center gap-3 border-b border-border py-3.5 last:border-b-0"
                >
                  <IconTile icon={categoryIcon[c]} />
                  <div className="min-w-0 flex-1">
                    <p
                      className={`flex items-center gap-1.5 text-[15px] font-semibold ${category.personal ? 'text-ink-secondary' : ''}`}
                    >
                      {category.label}
                      {category.personal && <Tag icon={Lock}>Pessoal</Tag>}
                    </p>
                    <p className="mt-0.5 text-xs text-ink-secondary">
                      {category.personal ? (
                        category.example
                      ) : (
                        <>
                          <span className="max-sm:hidden">{category.example} · </span>
                          {reportsToday[c]} reportes hoje
                        </>
                      )}
                    </p>
                  </div>
                  <Toggle
                    checked={!category.personal && draft.categories[c]}
                    onChange={(on) => toggle(c, on)}
                    label={`Pedir confirmação para ${category.label}`}
                    disabled={category.personal}
                  />
                </li>
              )
            })}
          </ul>
        </Panel>

        <Panel
          title="Regras"
          titleClassName="text-base"
          className="gap-3.5 lg:w-[400px] lg:shrink-0 max-sm:[&>div:first-child]:hidden"
        >
          <Field
            label="Raio do recinto"
            help="Só pede confirmação a quem permitiu a localização e está dentro deste raio."
          >
            {(p) => (
              <Select
                {...p}
                value={draft.radius}
                onChange={(e) => setDraft({ ...draft, radius: e.target.value })}
                options={radii}
              />
            )}
          </Field>
          <Field label="Respostas para confiança alta" error={error} className="max-sm:hidden">
            {(p) => (
              <TextInput
                {...p}
                inputMode="numeric"
                value={answers}
                onChange={(e) => setAnswers(e.target.value.replace(/\D/g, ''))}
              />
            )}
          </Field>
          <Field label="Máximo de pedidos por participante">
            {(p) => (
              <Select
                {...p}
                value={draft.maxRequests}
                onChange={(e) => setDraft({ ...draft, maxRequests: e.target.value })}
                options={limits}
              />
            )}
          </Field>
          <Button type="submit">
            <span className="max-sm:hidden">Guardar alterações</span>
            <span className="sm:hidden">Guardar</span>
          </Button>
        </Panel>
      </div>
    </form>
  )
}
