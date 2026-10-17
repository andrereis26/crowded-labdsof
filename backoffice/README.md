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

| Route | Mockup |
|---|---|
| `/verificacoes`, `/verificacoes/:id` | A1 · Organization verification queue and detail |

The other sections in the side menu show "Disponível em breve" for now. Data comes from `src/data/verifications.ts` (demo data) until the Core API exists.

## Design system

Colours and fonts follow the [Crowded — Mockups](https://www.figma.com/design/EdgpgHGNgfRGewcW5kvbES/Crowded-%E2%80%94-Mockups) Figma file and match the participant app's tokens. They are defined once as Tailwind theme variables in `src/index.css` (`bg-primary`, `text-ink-secondary`, `bg-warning-soft`, …), and Inter is bundled in `public/fonts`.

Shared components live in `src/ui/components.tsx`: `Button` (primary, secondary, approve, danger), `Tag`, `Panel`, `Avatar` and `Chip`. Icons come from `lucide-react`, which matches the icons in the mockups.
