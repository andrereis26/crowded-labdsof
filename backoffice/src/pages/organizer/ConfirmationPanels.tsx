import { Lock } from 'lucide-react'
import { categories, type ConfirmationSettings, type IncidentCategory } from '../../data/organizer'
import { IconTile, Panel, Tag } from '../../ui/components'
import { categoryIcon } from '../../ui/categories'
import { Field, Select, TextInput, Toggle } from '../../ui/forms'

const radii = [
  { value: '200', label: '200 m' },
  { value: '500', label: '500 m' },
  { value: '1000', label: '1 km' },
]
const limits = [
  { value: '1', label: '1 por hora' },
  { value: '3', label: '3 por hora' },
  { value: '5', label: '5 por hora' },
]

/** "Pedir confirmação para…": the report categories that ask other
 * participants at the venue to confirm a report. Personal ones never do. */
export function ConfirmationCategoriesPanel({
  value,
  onChange,
}: {
  value: ConfirmationSettings
  onChange: (value: ConfirmationSettings) => void
}) {
  return (
    <Panel title="Pedir confirmação para…" titleClassName="text-base" className="gap-0">
      <p className="mb-1 text-[13px] text-ink-secondary">
        Outros participantes no recinto confirmam com Sim ou Não.
      </p>
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
                <p className="mt-0.5 text-xs text-ink-secondary">{category.example}</p>
              </div>
              <Toggle
                checked={!category.personal && value.categories[c]}
                onChange={(on) =>
                  onChange({ ...value, categories: { ...value.categories, [c]: on } })
                }
                label={`Pedir confirmação para ${category.label}`}
                disabled={category.personal}
              />
            </li>
          )
        })}
      </ul>
    </Panel>
  )
}

/** "Regras": how far from the venue, how many answers make a report's
 * confidence high, and how often one participant is asked. */
export function ConfirmationRulesPanel({
  value,
  onChange,
  answers,
  onAnswersChange,
  answersError,
}: {
  value: ConfirmationSettings
  onChange: (value: ConfirmationSettings) => void
  /** The answers field as typed, checked when the form is saved. */
  answers: string
  onAnswersChange: (answers: string) => void
  answersError?: string
}) {
  return (
    <Panel title="Regras" titleClassName="text-base" className="gap-3.5">
      <Field
        label="Raio do recinto"
        help="Só pede confirmação a quem permitiu a localização e está dentro deste raio."
      >
        {(p) => (
          <Select
            {...p}
            value={String(value.radiusMeters)}
            onChange={(e) => onChange({ ...value, radiusMeters: Number(e.target.value) })}
            options={radii}
          />
        )}
      </Field>
      <Field label="Respostas para confiança alta" error={answersError} hint="De 2 a 50.">
        {(p) => (
          <TextInput
            {...p}
            inputMode="numeric"
            value={answers}
            onChange={(e) => onAnswersChange(e.target.value.replace(/\D/g, ''))}
          />
        )}
      </Field>
      <Field label="Máximo de pedidos por participante">
        {(p) => (
          <Select
            {...p}
            value={String(value.maxPromptsPerHour)}
            onChange={(e) => onChange({ ...value, maxPromptsPerHour: Number(e.target.value) })}
            options={limits}
          />
        )}
      </Field>
    </Panel>
  )
}
