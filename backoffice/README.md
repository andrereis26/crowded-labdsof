# Crowded backoffice

Web app for organizers and administrators: React, Vite and Tailwind CSS ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)).

## Setup

**Prerequisites:** Node.js 22.12 or later (CI uses 24). [`scripts/local/setup.sh`](../scripts/local/setup.sh) (`setup.ps1` on Windows) checks it and creates `.env` ([Getting Started](../README.md#getting-started)). Signing in and the organization's events need Core (below); the other organizer pages still run on demo data.

**Configuration:** [`src/config.json`](src/config.json) holds the app's settings: `apiBaseUrl` (Core) and `geocoderUrl` (the Photon geocoder, [ADR 0018](../docs/deliverables/adr/0018-openstreetmap-for-maps-and-geocoding.md)). An environment variable named `VITE_` plus the key in upper snake case replaces a value when Vite builds or serves the app (`VITE_GEOCODER_URL`, `src/config.ts`). From `.env`, the scripts read `BACKOFFICE_PORT` (5173).

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

| Route                                                       | Mockup                                                                                                                      |
| ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `/sign-in`                                                  | C1 · Sign in, with the expired temporary password error (C4)                                                                |
| `/new-password`                                             | C4 · First sign-in: choose a new password                                                                                   |
| `/organizations`                                            | A2 · Organizations and members, with the create and password dialogs                                                        |
| `/organization/events`                                      | O2 · Events list                                                                                                            |
| `/organization/events/new`, `/organization/events/:id/edit` | O3 · Create / edit event, ticketed or public (access code and QR code), with its map, queue points and report confirmations |
| `/organization/events/:id`                                  | O4 · Event page with the "Alterar evento" dialog                                                                            |
| `/organization/live/crowd`                                  | O5 · Update crowd level and queues                                                                                          |
| `/organization/alerts`                                      | O6 · Send alert, manual or AI draft                                                                                         |
| `/organization/live`                                        | O7 · Live dashboard                                                                                                         |
| `/organization/incidents`                                   | O8 · Incidents grouped by AI                                                                                                |
| `/organization/incidents/:id`                               | O9 · Incident detail                                                                                                        |
| `/organization/requests`                                    | O12 · Publish request                                                                                                       |

Sections without a mockup show "Disponível em breve". The platform administration (A2) reads and changes organizations and members in Core (`/admin/...`, #140, `src/data/adminApi.ts`) and opens only for an administrator. The organization's events (O2, O3, the change dialog of O4) come from Core (`src/data/eventsApi.ts`): listing, creating, correcting and publishing official changes. O3 looks the venue's address up in Photon, on OpenStreetMap data, when the member leaves the field and shows the pin on an embedded map ([ADR 0018](../docs/deliverables/adr/0018-openstreetmap-for-maps-and-geocoding.md), `src/data/geocoding.ts`); the report confirmations that O10 used to set are now part of each event in O3. The other organizer pages use `src/data/organizer.ts` (demo data) until #148. Sign-in goes to Core (`POST /auth/login`, #59), which keeps the session in cookies the pages cannot read ([ADR 0021](../docs/deliverables/adr/0021-browser-sessions-in-cookies-with-refresh-tokens.md)): at start-up `AuthProvider` (`src/auth/`) asks Core who is signed in (`GET /me`), so a reload keeps the session; `RequireRole` shows each area only to its role and tells a participant the account is for the app; every request refreshes the session once when its token has expired (`sessionFetch`); and "Terminar sessão" ends it in Core. Event images load with an image tag, which sends the cookie. Try it with the demo accounts of [`database/README.md`](../database/README.md#demo-accounts): `admin@crowded.test` opens the administration and `equipa@ribeira-live.test` the organizer area. The dev server sends `/api` to a Core on `http://localhost:8081` (`scripts/local/start.sh core`; `CORE_URL` changes it), and a build calls `VITE_API_BASE_URL` (default `/api`, behind the router). A member with a temporary password is refused until #141 adds C4 to the API; C4 itself still runs on demo data.

The organizer pages work on the event chosen in the top bar. O5 publishes the crowd level (`PUT /events/{eventId}/crowd`, #39) and the waiting times at the event's queue points (`PUT /events/{eventId}/queues`, #40) in Core and shows the ones Core has. O6 sends an operational alert, its type and a message written by hand, through Core (`POST /events/{eventId}/alerts`, #41) and lists the event's active alerts, which O2 counts; the AI draft comes with #42. The entry counter and the live dashboard show the demo data of Festival Margens 2026 for every event until their stories read Core.

## Design system

Colours and fonts follow the [Crowded — Mockups](https://www.figma.com/design/EdgpgHGNgfRGewcW5kvbES/Crowded-%E2%80%94-Mockups) Figma file and match the participant app's tokens. They are defined once as Tailwind theme variables in `src/index.css` (`bg-primary`, `text-ink-secondary`, `bg-warning-soft`, …), and Inter is bundled in `public/fonts`.

Shared components:

- `src/ui/components.tsx`: `Button` and `ButtonLink` (primary, secondary, approve, danger, ai; sizes sm, md, lg), `Tag`, `AiTag`, `Panel`, `Avatar`, `Chip`, `PageHeader`, `Breadcrumb`, `StatCard`, `Metric`, `IconTile`, `Notice`, `ProgressBar`, `EventThumb`, `Dialog`, `SuccessBanner`, and the base components of the mockups: `ConfidenceBadge`, `CrowdMeter` and `TicketCard` (wallet card).
- `src/ui/forms.tsx`: `Field` (label, help and error linked to the control), `TextInput`, `TextArea`, `Select`, `Toggle`, `ToggleRow`, `Checkbox`, `RadioOption`, `RadioDot`, `SegmentedControl` and `Stepper`.
- `src/layout/Shell.tsx`: side menu and top bar shared by `AdminLayout` and `OrganizerLayout` (which adds the event selector and the phone tab bar).

Icons come from `lucide-react`, which matches the icons in the mockups (`src/ui/icons.tsx` adds the poll icon). `src/ui/qr.tsx` draws QR codes with the `qrcode` package.
