# Requirements Specification

This specification translates the Crowded product vision, problem and opportunity report, user research, assignment constraints, and responsible AI assessment into **functional**, **non-functional**, **security** and **technical** requirements. Priorities reflect the MVP boundary: keeping participants informed when their event changes or something goes wrong on site, through an app that an event organization adopts instead of building its own. The mandatory project deliverables also shape them.

The scope was narrowed after the Sprint 1 review, where the professors found it too broad and the story hard to follow. The organization is now the only source of event information. Discovery, pre-event interest polls, the post-event summary, the ticketing source and the merging of changes from two sources were removed. Removed requirements were deleted, and their IDs are not reused. The [Product Vision](product-vision.md#mvp-boundary) lists what is out of the MVP.

**Priority levels are defined as follows**:
- **8-10: High - Must have for MVP**
- **5-7: Medium - Could/Should have for MVP**
- **1-4: Low - Out of scope for MVP**

## 1. Functional Requirements

### 1.1 Connecting to Events

A participant reaches an event's page in one of two ways only. For a ticketed event, they import their ticket. For a public event, which has no tickets, they use the access code that the organization gives out. There is no event list or search.

| ID | Statement | Priority |
|---|---|---|
| FR-001 | The system shall allow a participant to import a ticket bought on an external platform by uploading its PDF. AI extracts the event code printed on the ticket and the ticket details (gate or sector, seat). | 9 |
| FR-082 | The system shall allow a participant to import a ticket by uploading a screenshot or photo of it, with the same extraction as FR-001. | 8 |
| FR-070 | The system shall decode the QR code of an imported ticket, from its PDF or image, without AI, and store it with the ticket as its entry code. | 9 |
| FR-003 | When the extracted event code matches a ticketed event registered by an organization, the system shall add the ticket to the participant's events without a review step. It shall show a summary ("Adicionaste o evento X") with the event's name, date and time, doors opening time and venue, taken from the registered event and not from the AI extraction. The summary offers to remove the ticket if it is not the participant's event. | 10 |
| FR-004 | When no event code is extracted, or the extracted code matches no registered ticketed event, the system shall tell the participant that it could not match the ticket to an event and ask for the event code printed on the ticket. The typed code only completes the import of the uploaded ticket, whose QR code must have been decoded (FR-070). It is never accepted on its own. | 10 |
| FR-083 | When the event code matches no registered event, the system shall tell the participant that the event does not use Crowded yet, and shall not store the ticket or its file. | 8 |
| FR-084 | The system shall link a ticket's entry code to at most one account per event, and refuse a second import of the same ticket by another account. | 8 |
| FR-085 | The system shall allow a participant to connect to a public event by scanning its QR code or typing its access code, both defined by the organization. | 10 |
| FR-005 | The system shall present the participant's events (tickets and public events), listing the upcoming ones first with their essential information. With no events, it shall show only the options to import a ticket or enter an access code. | 9 |
| FR-071 | The system shall display a ticket's entry code full screen, also without a network connection, labelled with its origin and without stating that the ticket is valid. | 8 |
| FR-008 | The system shall allow a participant to remove a ticket or leave a public event, which ends their access to its page. | 7 |

### 1.2 Official Event Changes

The organization that owns an event is its only official source. A change it publishes reaches everyone connected to the event.

| ID | Statement | Priority |
|---|---|---|
| FR-013 | The system shall notify the participants connected to an event (through a ticket or the access code) when its organization publishes a change, according to their notification preferences. | 10 |
| FR-014 | The system shall display an event's current status and its change history, each change with its source and time. | 8 |
| FR-075 | When an event is cancelled, postponed or moved, the notification and the change detail shall tell the participant what changes for them: the new date, time or venue, whether their ticket is still valid and, when the organization provides it, where to ask for a refund. | 8 |
| FR-072 | The system shall display an event's doors opening time when the organization provides it, and shall treat a change to it as an event change (FR-016, FR-013). | 7 |
| FR-074 | The system shall classify every official event change, operational alert and weather warning as critical, warning or information (critical: cancellation, postponement, venue or start-time change, entrance closure, red weather warning), and shall present critical ones first and highlighted, not by color alone. | 7 |

### 1.3 Organization Backoffice

| ID | Statement | Priority |
|---|---|---|
| FR-015 | The system shall allow an organization to create and publish an event with its name, description, schedule (start, doors opening, end), venue (address, district and location), capacity, and type. A **ticketed** event records the event code printed on its tickets. A **public** event records the access code that the organization gives out, and the system generates its QR code. | 10 |
| FR-086 | The system shall refuse an event code or access code that another upcoming event already uses. | 8 |
| FR-016 | The system shall allow an organization to update, postpone, move or cancel its own events, with the refund information when it applies, triggering notifications to connected participants labelled with the organization as their source. | 10 |
| FR-089 | The system shall allow an organization to add, replace or remove an image of its event (JPEG, PNG or WebP, up to 5 MB), shown wherever the event is shown. | 5 |
| FR-018 | The system shall allow an organization to publish the current crowd level of its event (low, moderate, high, full) through the backoffice. | 10 |
| FR-019 | The system shall allow an organization to publish queue waiting times for points of its event (e.g., entrances, ticket office, bars, toilets). | 8 |
| FR-020 | The system shall allow an organization to send an operational alert to the participants connected to its event, according to their notification preferences, choosing an alert type (e.g., entrance closure, capacity reached, schedule delay) and writing its message, with or without an AI-assisted draft. | 10 |
| FR-021 | The system shall generate an AI-assisted alert draft from the organization's brief input and the event's current state, which the organization must review, edit and approve before it is sent. | 8 |
| FR-073 | The system shall allow an organization to mark an operational alert as resolved (e.g., the entrance reopened), removing it from the event's active alerts and informing the connected participants that it has ended. | 8 |
| FR-023 | The system shall present the organization with a live dashboard showing the event's crowd level, queues, open incidents and alerts sent. | 9 |
| FR-024 | The system shall allow an organization to define its event's capacity and show the current crowd level relative to it. | 7 |
| FR-051 | The system shall allow an organization to publish surveys on its event's page for the participants connected to it (confirmations such as "Is Gate B still closed?", multiple-choice questions or calls for ideas), and mark each survey as urgent or non-urgent. | 8 |
| FR-052 | The system shall notify connected participants who have organization surveys turned on only of urgent surveys. Opening the notification shall take the participant to the survey on the event page. Non-urgent surveys shall remain available on the event page without a notification. | 8 |
| FR-029 | The system shall allow an organization to view reported incidents and mark them as acknowledged or resolved, notifying the reporting participant. | 8 |
| FR-053 | The system shall use AI to group similar incident reports into a single incident with a short summary, allow the organization to split incorrectly grouped reports, and fall back to grouping by category and time window when the AI service is unavailable. | 8 |
| FR-060 | The system shall allow an organization to choose, per event, which predefined report categories trigger confirmation prompts. | 8 |

### 1.4 On-Site Information for Participants

| ID | Statement | Priority |
|---|---|---|
| FR-031 | The system shall display an event's crowd level, queue times and alerts to connected participants. | 10 |
| FR-032 | The system shall label every piece of event information with its source (organization, participant estimate, IPMA, transport operator, AI-generated) and its last-update time. | 9 |
| FR-033 | The system shall flag or hide on-site information that has not been updated within a configured period. | 8 |
| FR-034 | The system shall allow a participant to report something at an event by choosing a predefined category (e.g., crowd level, queue length, lost and found, accessibility barrier, on-site problem) and adding an optional description. | 8 |
| FR-090 | The system shall allow a participant to attach one photo (JPEG, PNG or WebP, up to 5 MB) to a report, seen only by the reporter and the active members of the event's organization. | 5 |
| FR-057 | The system shall ask a limited random sample of the participants connected to an event who have allowed location access, excluding the reporter, to confirm a report with a single "Yes" or "No" while the event is taking place. It does so only for categories that trigger confirmation prompts, stops when enough answers are collected, and limits how often each participant is asked. | 8 |
| FR-058 | The system shall compute a rule-based confidence for each report (raised by "Yes" answers, lowered by "No" answers and decaying over time) and classify it as Low, Medium or High. The level is shown to the organization; participants see the confirmations behind it instead (FR-059). | 8 |
| FR-059 | The system shall display reports with Medium or High confidence on the event page as participant estimates, showing how many participants confirmed or denied each one and when it was last confirmed, and a notice that they are estimates that may not reflect reality. Crowd level and queue estimates shall only be shown when the organization has no current value for them. Reports in personal categories (e.g., lost and found) shall never be shown publicly or trigger confirmation prompts. | 8 |
| FR-064 | The system shall only allow a participant to submit a report in a non-personal category, or to answer a confirmation prompt, when they have allowed location access and their approximate location is within a configured radius of the event venue. Otherwise, it shall explain that location is only requested to ensure better report quality. Reports in personal categories (e.g., lost and found) shall not require location. | 8 |
| FR-054 | The system shall notify a participant when the organization marks their report as helpful, showing its impact (e.g., the incident was resolved, or an alert was sent to N people). | 7 |
| FR-035 | The system shall display the IPMA weather forecast for the day and the district of the event. | 6 |
| FR-036 | The system shall notify connected participants when IPMA issues an orange or red weather warning for the event's district during the event, and show yellow warnings on the event page without a notification. | 8 |
| FR-037 | The system shall make the essential information of the participant's events (date, time, doors opening time, venue, ticket details and entry code, latest change and active alerts) available offline, showing the time of the last synchronization. | 8 |
| FR-055 | The system shall display public transport options to the event venue (e.g., metro, bus, train) with their upcoming departure times, from a real GTFS feed, and indicate service disruptions when the feed reports them. | 6 |
| FR-056 | The system shall display an integrated map showing the event venue and routes to it from the participant's location. | 6 |
| FR-038 | The system shall allow a participant to view a private history of their reports and confirmations, with the outcome of each report (confirmed, grouped into an incident, marked as helpful, resolved) and the recognitions received (see the Ethical Engagement Design). | 7 |

### 1.5 Accounts and Preferences

| ID | Statement | Priority |
|---|---|---|
| FR-039 | The system shall identify users and assign them a role (participant, organization member, platform administrator). | 10 |
| FR-043 | The system shall allow participants to register and sign in with email and password. Organization members cannot register themselves. | 9 |
| FR-087 | The system shall allow a platform administrator to create an organization and its member accounts, each with a temporary password, and to deactivate a member. | 10 |
| FR-088 | The system shall require an organization member who signs in with a temporary password to set a new password before doing anything else. | 9 |
| FR-041 | The system shall allow a participant to turn each notification type on or off for a specific event: official event changes, operational alerts, organization surveys (urgent surveys, see FR-052), confirmation prompts, weather warnings, recognitions and incident updates. These per-event choices shall override the participant's overall preferences (FR-065) for that event. | 8 |
| FR-065 | The system shall allow a participant to set overall notification preferences, turning each notification type (official event changes, operational alerts, organization surveys, confirmation prompts, weather warnings, recognitions and incident updates) on or off. These preferences shall apply by default to every event the participant is connected to, including events connected later, and every type shall be on by default. A type that is turned off shall not be pushed, shown as a banner or added to the notification center. | 8 |
| FR-066 | When a participant turns off official event changes or operational alerts, overall or for an event, the system shall warn them that they may miss cancellations or safety instructions and ask for confirmation. This information shall remain visible in the app (their events and the event page) while its notifications are off. | 8 |
| FR-067 | The system shall provide an in-app notification center on every participant client (mobile app and desktop and mobile browsers), listing the participant's notifications newest first with an unread badge, letting them mark notifications as read, and taking them to the related event, alert or report when a notification is opened. | 9 |
| FR-068 | While the app or website is open, the system shall show new notifications as a live banner and update the unread badge within seconds, without the participant reloading the page. | 8 |
| FR-069 | The system shall send push notifications to the mobile app when it is closed or in the background. Browsers shall not receive push notifications in the MVP; browser users receive notifications in the notification center and as live banners while the website is open. | 8 |
| FR-042 | The system shall allow participants to permanently delete their account and all associated personal data. | 8 |

---

## 2. Non-Functional Requirements

Each requirement states a measurable target, why the product needs it, and how it is verified. Together they cover:

- **performance:** NFR-001, 008, 009, 019, 021;
- **reliability and degraded operation:** NFR-003 to 006, 020;
- **trust in AI:** NFR-007, 015;
- **operability:** NFR-010 to 012;
- **maintainability and delivery:** NFR-013, 014, 016;
- **usability:** NFR-017;
- **accessibility:** NFR-018.

Security is specified separately in section 3. Scaling beyond one node is out of the MVP ([ADR 0013](adr/0013-single-azure-vm-docker-swarm-with-terraform.md)). NFR-009 is measured under load to show that one node is enough for the MVP's audience.

| ID | Category | Statement | Rationale | Verified by | Priority |
|---|---|---|---|---|---|
| NFR-001 | Performance | Critical notifications (cancellation, rescheduling, venue change, operational alert, red weather warning) shall be delivered within 1 minute of the change being published or the warning being detected, as a push to the mobile app, or as a live banner when the app or website is open. | The north star is notifying participants before the doors open ([Product Vision](product-vision.md#success-criteria)). One minute leaves Inês time to change her plans even when she is already on her way. | Delivery latency from the disruption to the notification, an indicator on the operational dashboard; integration test that publishes a change through the backoffice API | 9 |
| NFR-021 | Performance | A new or raised IPMA weather warning for the district of an upcoming or ongoing event shall be detected within 10 minutes of IPMA publishing it. | Weather is the one disruption that does not come from the organization. Polling every 5 minutes keeps detection under 10 minutes without loading a public API. | Detection time measured with the IPMA adapter's fake | 8 |
| NFR-003 | Reliability | Notifications shall be delivered at least once and processed idempotently, so that a participant never receives the same notification twice for the same change, alert or warning. | The message broker delivers at least once ([ADR 0003](adr/0003-rabbitmq-for-asynchronous-messaging.md)), so a message can arrive twice. A duplicate notice erodes the trust the product depends on, and notification fatigue was a [user research](user-research.md) finding. | Unique deduplication key; test that redelivers the same message | 9 |
| NFR-004 | Reliability | Asynchronous workflows shall retry failed processing with backoff and move repeatedly failing messages to a dead-letter queue for inspection. | Transient failures (a restarting service, a slow provider) must not lose a notification. Messages that never succeed must stay visible for inspection rather than retry forever. | Integration test with a failing consumer; dead-letter queue depth on the dashboard | 8 |
| NFR-005 | Reliability | When an external source (IPMA, the GTFS feed) is unavailable, delayed or returns invalid data, the system shall keep operating, reject and log invalid data, and show participants the time of the last successful update. | External sources fail or send bad data (risk R3). Participants are better served by the last valid data with its time than by an error or wrong data. | Tests with each adapter's fake set to unavailable, slow and invalid | 8 |
| NFR-006 | Reliability | When the AI service is unavailable, the system shall fall back to the typed event code for ticket import, manually written alerts and grouping by category and time window, so that the principal workflows continue. | The AI provider is an external dependency. The principal journeys must not depend on it ([Responsible AI Opportunity Assessment](responsible-ai-opportunity-assessment.md#baseline-or-fallback-strategy)). | Tests with the AI adapter's fake set to fail; demonstration of a failure condition | 9 |
| NFR-020 | Reliability | When the push notification provider is unavailable, the system shall keep delivering notifications to the in-app notification center and as live banners on open clients, retry push delivery with backoff, never notify twice, and record the failure in the logs and the operational dashboard. | Push is a convenience on mobile, not the only channel. Every client gets in-app delivery ([ADR 0007](adr/0007-live-updates-with-server-sent-events.md)), so a push outage must not hide a cancellation. | Test with the push adapter's fake set to fail; failure counter on the dashboard | 9 |
| NFR-007 | AI trust | The system shall never present AI-generated content as verified fact. Event information shown after a ticket import comes from the registered event, not from the extraction. AI-generated content shown to other people (e.g., alert drafts, incident summaries) shall be labelled as AI-generated until approved. | AI extraction and drafts can be wrong (risks R2, R3 of the AI assessment). Provenance is the product's core promise, so unconfirmed AI output must never look like official information. | UI tests for the AI-generated labels and the import summary; review against the mockups | 9 |
| NFR-008 | Performance | Information published by an organization shall be visible to connected participants within 30 seconds. | Organizations publish crowd levels and alerts during the event, when conditions change minute by minute. Thirty seconds keeps the event page current enough to decide on. | End-to-end timing from publication to the open event page, over SSE | 8 |
| NFR-009 | Performance | 95% of user requests shall receive a response within 2 seconds, excluding AI-assisted ticket extraction, which shall complete within 30 seconds. | Two seconds keeps interactions fluid on a mobile network. Extraction is allowed 30 seconds because it replaces typing a ticket's code by hand. | Load test of the principal journeys at the 95th percentile (#74); extraction latency in the AI evaluation (#72) | 8 |
| NFR-010 | Operability | The system shall provide structured application logs, relevant operational metrics, error reporting, and correlation or trace identifiers for its deployed components. | Three services and a broker cannot be debugged without logs, metrics and identifiers that follow a request across them. | Logs in Loki and metrics in Prometheus, searchable by trace identifier (#70, [ADR 0009](adr/0009-observability-stack.md)) | 8 |
| NFR-011 | Operability | The system shall provide an operational health check for each deployed backend component. | Swarm uses health checks to restart failed services and to roll back a bad release ([ADR 0013](adr/0013-single-azure-vm-docker-swarm-with-terraform.md)). | Readiness and liveness probes; the smoke test of each image and deployment | 8 |
| NFR-012 | Operability | The system shall provide an operational dashboard with service-level indicators, including notification delivery latency, weather-warning detection lag, failed asynchronous jobs, and AI latency and error rate. | It shows the success criteria (NFR-001, NFR-021, NFR-006, NFR-020) live, so the team sees a degradation before users report it. The course requires it in Sprint 3: see the [Product Backlog](product-backlog.md#6-to-resolve-in-sprint-2-planning). | The dashboard itself (#71), shown in the Sprint 3 demonstration | 4 |
| NFR-013 | Maintainability | The system shall support unit, integration, and relevant end-to-end testing of the principal workflows. | Regressions must be caught before release, and the principal workflows cross components that unit tests alone cannot cover. | CI: unit tests with 80% line coverage, Testcontainers integration tests, end-to-end tests ([ADR 0011](adr/0011-ci-pipeline-and-vulnerability-policy.md)) | 8 |
| NFR-014 | Maintainability | The system shall be built and delivered through an automated CI/CD pipeline. | Every merge must be releasable without manual steps, so the shared environment always runs the latest reviewed code. | Release workflow on `main` and the deployment with automatic rollback | 8 |
| NFR-015 | AI trust | The AI-assisted capabilities shall be evaluated against a documented dataset, including difficult and adverse cases, with defined acceptance criteria. | AI accuracy cannot be assumed. Difficult and adverse cases (bad scans, photos at an angle, other languages, injected instructions) are where it fails. | Evaluation report with the metrics and acceptance criteria of the AI assessment (#72) | 8 |
| NFR-016 | Portability | The system shall be delivered using containers with reproducible builds and environment-specific configuration. | The image tested in CI must be the one deployed, and only the configuration may differ between environments ([ADR 0013](adr/0013-single-azure-vm-docker-swarm-with-terraform.md#environments)). | Images by digest in each release; configuration and secrets from Doppler | 8 |
| NFR-017 | Usability | The participant interface shall be usable on mobile devices. | Participants use Crowded on the way to and at the venue, on their phone (persona Inês). | Mockups and screenshots at mobile width; tests at 375 px | 7 |
| NFR-018 | Accessibility | The principal user journeys shall meet WCAG 2.1 AA, and alerts and crowd levels shall not be conveyed by color alone. | Event information matters most to people with reduced mobility or vision. About 8% of men have a colour vision deficiency, so colour alone cannot carry a crowd level or an alert. | Automated checks and a manual keyboard and screen-reader pass of the principal journeys (#74) | 7 |
| NFR-019 | Performance | New confirmations of a report, and the resulting change in whether it is shown, shall be reflected on the event page within 30 seconds. | Confirmations decide whether an estimate is shown (FR-059). They must appear as fast as the organization's own information (NFR-008), or estimates would lag behind the crowd. | End-to-end timing from a confirmation to the event page | 7 |

---

## 3. Security Requirements

| ID | Statement | Priority |
|---|---|---|
| SR-020 | The system shall serve an event's page, change history, live updates, report categories, surveys and on-site information only to participants connected to the event (through a ticket or its access code) and to members of the organization that owns it. Access ends when the connection is removed, including any open live stream. | 10 |
| SR-021 | The system shall decide on the server which event a ticket or an access code connects to, from the extracted or typed code, and shall never accept an event identifier from the client for that purpose. | 10 |
| SR-001 | The system shall restrict organization actions (events and their changes, on-site information, alerts, surveys, incidents) to active members of the organization that owns the event. | 10 |
| SR-002 | The system shall only allow members of organizations created by a platform administrator to publish event information, changes and alerts. | 10 |
| SR-026 | The system shall keep session tokens out of reach of page scripts in the browser, shall refuse cookie-authenticated requests that come from another site, shall end a session after a period without use and at a maximum age, and shall end it at once when the user signs out. | 9 |
| SR-022 | Temporary passwords set by a platform administrator shall only allow setting a new password, and shall expire after 7 days. | 8 |
| SR-003 | The system shall scope participant data (tickets, connections, incidents, preferences, location) to the participant or the authorized role permitted to access it. | 8 |
| SR-004 | The system shall remove personal data (e.g., name, email, tax number) and access codes from ticket content before sending it to an external AI provider. | 8 |
| SR-005 | The system shall treat ticket files and organization input as untrusted data for the AI, validate AI output against a fixed schema, and never allow the AI to perform actions. | 8 |
| SR-018 | The system shall treat ticket entry codes as credentials: store them encrypted, show them only to the ticket owner, never send them to the AI provider or write them to logs, and delete them with the ticket or account. To enforce FR-084, it may keep a keyed hash of the code. | 8 |
| SR-023 | The participant client shall delete its cached event data and entry codes when the user signs out or deletes their account, and the cached data of an event when its connection is removed. | 8 |
| SR-013 | The system shall store user passwords using a strong one-way hashing algorithm with salt. | 8 |
| SR-024 | The system shall accept a password chosen by a user only with at least 8 characters, at most 72 bytes, and at least one uppercase letter, one lowercase letter, one digit and one special character; the temporary passwords it generates meet the same rule. | 8 |
| SR-025 | The system shall accept an uploaded image only when its content is of an accepted type and size, shall remove its metadata (including the location) before storing it, shall serve it only to whoever may see what it belongs to, and shall delete it with what it belongs to. | 7 |
| SR-016 | The system shall accept at most one confirmation answer per participant per report, shall not let reporters confirm their own reports, and shall not reveal who confirmed or denied a report. | 8 |
| SR-017 | The system shall check location for reporting and confirmation only once, at the moment of the action, store only the result of the check (not the location itself), and never track location continuously. | 8 |
| SR-006 | The system shall keep an audit log of event changes, alerts sent, incident status changes and the administrator's account actions, including who performed them and when. | 7 |
| SR-007 | The system shall not make the reporting participant's identity public in incident reports. | 7 |
| SR-008 | The system shall manage secrets outside the source repository. | 7 |
| SR-015 | The system shall apply rate limiting to sign-in and participant registration to prevent abuse. | 7 |
| SR-009 | The system shall request user consent before using location data and shall use only approximate location. | 6 |
| SR-011 | The system shall apply rate limiting to unauthenticated requests, ticket imports, reports and confirmation answers. | 6 |
| SR-012 | The system shall authenticate service-to-service communication between backend components. | 5 |

---

## 4. Technical Requirements

| ID | Statement | Priority |
|---|---|---|
| TR-002 | The system shall consist of at least two independently deployable backend components (e.g., core services, background jobs, notifications). | 9 |
| TR-008 | The system shall integrate with real external sources through adapters that have a fake for tests: the IPMA public API for weather warnings and forecasts, and a GTFS feed for public transport. | 9 |
| TR-003 | The system shall use a message broker for asynchronous communication between the component that detects or receives a disruption and the component that notifies participants. | 8 |
| TR-007 | The participant client shall be available as a mobile app and as a web app usable in desktop and mobile browsers. | 8 |
| TR-009 | Demonstration data shall come from the database seed and from a demo script outside the deployed product, which acts through the public API with demo accounts (e.g., to submit reports and confirmations live). No simulator is deployed. | 6 |
| TR-006 | The system shall cache event status and on-site information reads, invalidating the cache when the information changes. | 4 |
