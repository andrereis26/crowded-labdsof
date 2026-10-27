# Crowded backoffice

Web app for organizers and administrators: React, Vite and Tailwind CSS ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)).

## Running

Needs Node.js 20.19 or later.

```bash
npm install
npm run dev
npm test
npm run lint
npm run build
```

## Screens

| Route                                                          | Mockup                                                           |
| -------------------------------------------------------------- | ---------------------------------------------------------------- |
| `/verificacoes`, `/verificacoes/:id`                           | A1 · Organization verification queue and detail                  |
| `/registo`, `/registo/pendente`                                | O1 · Organization registration and pending verification          |
| `/organizacao/eventos`                                         | O2 · Events list                                                 |
| `/organizacao/eventos/novo`, `/organizacao/eventos/:id/editar` | O3 · Create / edit event                                         |
| `/organizacao/eventos/:id`                                     | O4 · Event page with the postpone, change venue or cancel dialog |
| `/organizacao/ao-vivo/lotacao`                                 | O5 · Update crowd level and queues                               |
| `/organizacao/alertas`                                         | O6 · Send alert, manual or AI draft                              |
| `/organizacao/ao-vivo`                                         | O7 · Live dashboard                                              |
| `/organizacao/incidentes`                                      | O8 · Incidents grouped by AI                                     |
| `/organizacao/incidentes/:id`                                  | O9 · Incident detail                                             |
| `/organizacao/definicoes`                                      | O10 · Categories that ask for confirmation                       |
| `/organizacao/sondagens`                                       | O11 · Create poll and see results                                |
| `/organizacao/pedidos`                                         | O12 · Publish request                                            |
| `/organizacao/eventos/pedir-gestao`                            | O13 · Request management of a synced event                       |
| `/organizacao/eventos/:id/resumo`                              | O14 · Post-event summary (demo data for Festival Margens 2025)   |

Sections without a mockup show "Disponível em breve". Data comes from `src/data/verifications.ts` and `src/data/organizer.ts` (demo data) until the Core API exists. Registrations (O1) and management requests (O13) are added to the A1 queue. The "Gerar rascunho com IA" button in O6 uses a local stand-in (`draftAlert`) until the AI drafting service exists. "Ver página pública" opens the participant app at `VITE_PARTICIPANT_URL` (default `http://localhost:8765`).

The organizer pages work on the event chosen in the top bar. Lotação, filas and the live dashboard only have data for Festival Margens 2026 (live), and polls only for Margens Inverno.

## Design system

Colours and fonts follow the [Crowded — Mockups](https://www.figma.com/design/EdgpgHGNgfRGewcW5kvbES/Crowded-%E2%80%94-Mockups) Figma file and match the participant app's tokens. They are defined once as Tailwind theme variables in `src/index.css` (`bg-primary`, `text-ink-secondary`, `bg-warning-soft`, …), and Inter is bundled in `public/fonts`.

Shared components:

- `src/ui/components.tsx`: `Button` and `ButtonLink` (primary, secondary, approve, danger, ai; sizes sm, md, lg), `Tag`, `AiTag`, `Panel`, `Avatar`, `Chip`, `PageHeader`, `Breadcrumb`, `StatCard`, `Metric`, `IconTile`, `Notice`, `ProgressBar`, `EventThumb`, `Dialog`, `SuccessBanner`, and the base components of the mockups: `ConfidenceBadge`, `CrowdMeter` and `TicketCard` (wallet card).
- `src/ui/forms.tsx`: `Field` (label, help and error linked to the control), `TextInput`, `TextArea`, `Select`, `Toggle`, `ToggleRow`, `Checkbox`, `RadioOption`, `RadioDot`, `SegmentedControl`, `FileInput`, `FileRow` and `Stepper`.
- `src/layout/Shell.tsx`: side menu and top bar shared by `AdminLayout` and `OrganizerLayout` (which adds the event selector and the phone tab bar).

Icons come from `lucide-react`, which matches the icons in the mockups (`src/ui/icons.tsx` adds the poll icon).
