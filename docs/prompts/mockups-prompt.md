# Prompt used to generate the app mockups

We are working on a project called "Community Resilience and Everyday Services Platform", and my team is building Crowded, a platform that keeps participants informed about event changes and on-site conditions and helps organizations manage their audience. Based on the existing documentation in `docs/` (especially the requirements, the product vision with its personas, user journeys and BPMN workflows, the product backlog and the Responsible AI assessment), I need help creating the mockups of the participant app and of the organization backoffice (issue #75). Create them directly in Figma, through the Figma MCP connector, in a new file called "Crowded — Mockups" in our LABDSOF Figma project.

## Scope and structure

- First list the screens needed to cover the principal user journeys and the MVP, then draw every screen in a **mobile** and a **desktop** version.
- Organize the file in pages: **Capa** (cover with the design decisions), **0 · Componentes base**, **1 · Participante**, **2 · Organização** and **3 · Admin e conta**. Use one section per screen and two columns: **V1 — Sprint 2** (draw first) and **V2 — Sprint 3**.
- Screens:
  - Participant: P1 My events (ticket wallet), P2 Import ticket (PDF or email), P3 Review extracted data / manual entry, P4 Link to a catalog event, P5 Event page, P6 Change history, P7 Official change detail, P8 Notification center, P9 Events near me / today, P10 Location request, P11 Report (category and details), P12 Report sent / refused, P13 Yes / No confirmation request, P14 Participant estimates (with how many people confirmed them), P15 Recognition (your report helped), P16 Attendance poll, P17 Organization requests, P18 Directions to the venue, P19 Entry code.
  - Organization: O1 Registration / pending verification, O2 Event list, O3 Create / edit event, O4 Cancel / postpone event, O5 Update crowd level and queues, O6 Send alert (manual and AI draft), O7 Live dashboard, O8 Incidents grouped by AI, O9 Incident detail, O10 Categories that ask for confirmation, O11 Create poll and see results, O12 Publish request, O13 Request management of a synchronized event.
  - Admin and account: A1 Organization verification queue and detail, C1 Sign in / register, C2 Notification preferences, C3 Account (delete account and data).
- Before the screens, build the base components: provenance label, AI label, confidence indicator, crowd level indicator, alert banner, estimate notice, screen states (loading, empty, error, outdated data) and the wallet card component.
- All UI copy in European Portuguese.

## Visual direction

- Clean, iOS-inspired interface with the Inter font. Colors: text #121214, secondary text #6B6B73, primary blue #0066D9, background #F2F2F7. All text must meet WCAG 2.1 AA contrast (do not use #8E8E93 for text).
- The wallet (P1) should look like the card wallet on the iPhone: stacked cards with the event photo, the next event in front, date, time, gate, seat, status (e.g., "Adiado · 8 Nov") and the source of the information.
- Tapping a card opens the event page (P5) as an "opened ticket": the card expands and the event information appears below it (prototype link with Smart Animate). Draw two variants: the event day (organization alerts, crowd level, queues and weather) and any other day.
- Every piece of information shows its source and last-update time.

## Ticket import and entry code

- Tickets are imported from the ticket PDF or the confirmation email file in its original format (no pasting text and no forwarding to a Crowded email address).
- Capture the entry code (QR code) from the ticket and show it in the app (P19) so it can be used at the entrance, also offline. Open it from a QR icon next to the "…" in the top bar of the event page and from a button on the page. Add a variant for tickets without a code. Tickets with dynamic codes (codes that change every few seconds) cannot be imported.
- Entry codes are stored encrypted on the server. On the website, showing the code requires the password and the code is hidden after 2 minutes.

## Copy rules

- Do not add statements that explain privacy or system internals (e.g., "the file is deleted after extraction", "your location is not stored"). Keep only the information the user needs to act, and show required notices once, at the moment they matter (e.g., the location consent sheet). Add a "Como tratamos os teus dados" link to the import screen instead.
- Do not label individual fields as AI-generated. On the form filled by AI, use a single discreet line ("Preenchemos a partir do teu PDF"). Organization screens keep one block-level label on AI content ("Rascunho gerado por IA", "Resumo gerado por IA") until it is approved.

## Product decisions to reflect

- A ticket can be saved without being linked to a catalog event (P4).
- Crowd level and queues come from the entrance counter plus estimates; participant estimates are only shown with their confidence level.
- Location is checked once, on the server, when the user reports or confirms, and it is not stored.
- The participant app is a Flutter app (ADR 0006): no "add to home screen" banners. Browsers get no push notifications, so the desktop notification preferences (C2) explain that notifications only arrive while the website is open and suggest installing the app.
- Attendance polls and organization requests ("Pedidos") are separate features.
- The ticket PDFs used in the demo are generated by the ticketing simulator.

## Review

After drawing the screens, check every frame for clipped text, overlapping elements and contrast, and fix them. Then ask me questions ("grill me") about open product decisions, update the screens with my answers, record the decisions on the cover page, and update `requirements.md` and the related issues when a decision changes a requirement.

## Update for the narrowed MVP scope (#136)

After the Sprint 1 review narrowed the MVP (the organization is the only source of event information, [ADR 0015](../deliverables/adr/0015-organization-as-the-only-event-source.md)), we asked for the Figma file to be updated with this prompt:

Bring "Crowded — Mockups" to the MVP scope of issue #136 and the updated [requirements](../deliverables/requirements.md). Reuse the existing design system without restyling. Keep the copy rules above: European Portuguese, no privacy or AI disclaimers and no per-field AI badges. Draw mobile and desktop frames for every changed or new participant screen and desktop frames for the backoffice, and list the removed, changed and added frames at the end.

- **Remove:** P3, P4, P9 and the Eventos tab, P16, O1, O11, O13, O14 and A1; the P19 variant for tickets without a code; the "Seguir" and "Vou" buttons, "Proposto" labels and follower counts; the ticketing source, corroborated changes and source conflicts; the API key screens; and the email (.eml) import.
- **Change:**
  - P1 lists ticketed and public events.
  - P2 imports a PDF or an image of the ticket.
  - P5 to P7 show the organization as the source, the refund information, and the IPMA warnings and forecast.
  - P8 adds weather warnings and the state for an event the participant left.
  - P18 shows the GTFS operator as the source.
  - O3 chooses a ticketed or public event, with the event code or the access code, the district and a duplicate-code error.
  - O4 covers the change types, ticket validity and refund.
  - O12 drops the poll wording.
- **Add:**
  - P2a, the summary after an automatic import (FR-003).
  - P2b, the event code fallback (FR-004), pre-filled with the code that was read. It has three errors: no QR code (FR-070), event not on Crowded (FR-083) and ticket already in another account (FR-084).
  - P21, scan a QR code or type an access code, with "código não encontrado" (FR-085).
  - A2, the platform admin page: organizations and members, temporary passwords shown once that expire in 7 days, deactivate and reactivate (FR-087).
  - C4, set a new password at first sign-in, and the expired temporary password error (FR-088).

The current screens are:

- Participant: P1 My events (tickets and public events), P2 Import ticket (PDF or image), P2a Ticket added, P2b Ticket not matched (event code and errors), P5 Event page (with IPMA warnings), P6 Change history, P7 Official change detail (with refund), P8 Notification center, P10 Location request, P11 Report, P12 Report sent / refused, P13 Yes / No confirmation request, P14 Participant estimates, P15 Recognition, P17 Organization requests, P18 Directions to the venue, P19 Entry code, P20 Your contributions, P21 Enter access code.
- Organization: O2 Event list, O3 Create / edit event (ticketed or public), O4 Change event (postpone, time and doors, venue, cancel), O5 Update crowd level and queues, O6 Send alert, O7 Live dashboard, O8 Incidents grouped by AI, O9 Incident detail, O10 Categories that ask for confirmation, O12 Publish request.
- Admin and account: A2 Organizations and members, C1 Sign in, C2 Notification preferences, C3 Account, C4 New password at first sign-in.

The product decisions above about P4, polls, the email import and the catalog no longer apply.
