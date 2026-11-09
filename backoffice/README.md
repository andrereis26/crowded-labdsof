# Crowded backoffice

Web app for organizers and administrators: React, Vite and Tailwind CSS ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)).

## Setup

**Prerequisites:** Node.js 22.12 or later (CI uses 24). [`scripts/local/setup.sh`](../scripts/local/setup.sh) (`setup.ps1` on Windows) checks it and creates `.env` ([Getting Started](../README.md#getting-started)). The backoffice needs no backend yet: it runs on demo data.

**Configuration from `.env`:** `BACKOFFICE_PORT` (5173), and `VITE_PARTICIPANT_URL`, the address "Ver página pública" opens, from `PARTICIPANT_APP_PORT`.

**Running it alone,** from source, without the whole Docker stack:

| Step                                                                                     | Debian, macOS                       | Windows (PowerShell 7)               |
| ---------------------------------------------------------------------------------------- | ----------------------------------- | ------------------------------------ |
| Run `npm run dev` on `http://localhost:5173/` (installs the dependencies the first time) | `scripts/local/start.sh backoffice` | `scripts\local\start.ps1 backoffice` |
| Follow its log                                                                           | `scripts/local/logs.sh backoffice`  | `scripts\local\logs.ps1 backoffice`  |
| Stop it                                                                                  | `scripts/local/stop.sh backoffice`  | `scripts\local\stop.ps1 backoffice`  |

Or in the foreground, from this folder, the same on every OS:

```bash
npm install
npm run dev
```

**Tests and checks,** from this folder, the same on every OS:

```bash
npm test               # unit tests (npm run test:coverage: fails below 80% line coverage)
npm run format:check
npm run lint
npm run build
```

## Screens

| Route                                                          | Mockup                                                                 |
| -------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `/entrar`                                                      | C1 · Sign in, with the expired temporary password error (C4)           |
| `/definir-palavra-passe`                                       | C4 · First sign-in: choose a new password                              |
| `/organizacoes`                                                | A2 · Organizations and members, with the create and password dialogs   |
| `/organizacao/eventos`                                         | O2 · Events list                                                       |
| `/organizacao/eventos/novo`, `/organizacao/eventos/:id/editar` | O3 · Create / edit event, ticketed or public (access code and QR code) |
| `/organizacao/eventos/:id`                                     | O4 · Event page with the "Alterar evento" dialog                       |
| `/organizacao/ao-vivo/lotacao`                                 | O5 · Update crowd level and queues                                     |
| `/organizacao/alertas`                                         | O6 · Send alert, manual or AI draft                                    |
| `/organizacao/ao-vivo`                                         | O7 · Live dashboard                                                    |
| `/organizacao/incidentes`                                      | O8 · Incidents grouped by AI                                           |
| `/organizacao/incidentes/:id`                                  | O9 · Incident detail                                                   |
| `/organizacao/definicoes`                                      | O10 · Categories that ask for confirmation                             |
| `/organizacao/pedidos`                                         | O12 · Publish request                                                  |

Sections without a mockup show "Disponível em breve". Data comes from `src/data/admin.ts` and `src/data/organizer.ts` (demo data) until the Core API exists. Sign-in also works on demo data: `ana.lopes@crowded.test` opens the administration and the active members open the organizer area, with any password; a member with a temporary password (created in A2, or `rita.ferreira@margens.pt` with `T7q-mK4-v9Px`) must choose a new one, and `sofia.martins@teatromunicipal.pt` (`Hc3-pW8-z2Rt`) shows the expired error. The "Gerar rascunho com IA" button in O6 uses a local stand-in (`draftAlert`) until the AI drafting service exists. "Ver página pública" opens the participant app at `VITE_PARTICIPANT_URL` (default `http://localhost:8765`).

The organizer pages work on the event chosen in the top bar. Lotação, filas and the live dashboard only have data for Festival Margens 2026 (live).

## Design system

Colours and fonts follow the [Crowded — Mockups](https://www.figma.com/design/EdgpgHGNgfRGewcW5kvbES/Crowded-%E2%80%94-Mockups) Figma file and match the participant app's tokens. They are defined once as Tailwind theme variables in `src/index.css` (`bg-primary`, `text-ink-secondary`, `bg-warning-soft`, …), and Inter is bundled in `public/fonts`.

Shared components:

- `src/ui/components.tsx`: `Button` and `ButtonLink` (primary, secondary, approve, danger, ai; sizes sm, md, lg), `Tag`, `AiTag`, `Panel`, `Avatar`, `Chip`, `PageHeader`, `Breadcrumb`, `StatCard`, `Metric`, `IconTile`, `Notice`, `ProgressBar`, `EventThumb`, `Dialog`, `SuccessBanner`, and the base components of the mockups: `ConfidenceBadge`, `CrowdMeter` and `TicketCard` (wallet card).
- `src/ui/forms.tsx`: `Field` (label, help and error linked to the control), `TextInput`, `TextArea`, `Select`, `Toggle`, `ToggleRow`, `Checkbox`, `RadioOption`, `RadioDot`, `SegmentedControl` and `Stepper`.
- `src/layout/Shell.tsx`: side menu and top bar shared by `AdminLayout` and `OrganizerLayout` (which adds the event selector and the phone tab bar).

Icons come from `lucide-react`, which matches the icons in the mockups (`src/ui/icons.tsx` adds the poll icon). `src/ui/qr.tsx` draws QR codes with the `qrcode` package.
