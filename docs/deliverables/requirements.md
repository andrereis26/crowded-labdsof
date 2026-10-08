# Requirements Specification

This specification translates the Crowded product vision, problem and opportunity report, user research, assignment constraints, and responsible AI assessment into **functional**, **non-functional**, **security** and **technical** requirements. Priorities reflect the MVP boundary — keeping participants and organizations informed when an event changes or something goes wrong on site — and the mandatory project deliverables.

**Priority levels are defined as follows**:
- **8-10: High - Must have for MVP**
- **5-7: Medium - Could/Should have for MVP**
- **1-4: Low - Out of scope for MVP**

## 1. Functional Requirements

### 1.1 Connecting to Events

| ID | Statement | Priority |
|---|---|---|
| FR-001 | The system shall allow a user to import a ticket bought on an external platform by uploading its PDF, extracting the event information (event name, date and time, venue, gate or sector, seat) with AI assistance. | 9 |
| FR-002 | The system shall allow a user to import a ticket by pasting its confirmation email, extracting the event information with AI assistance. | 9 |
| FR-003 | The system shall allow a user to review, correct and confirm the extracted ticket information before saving it, and to enter ticket information manually when extraction is unavailable or fails. | 10 |
| FR-004 | The system shall match an imported ticket to an event in the synchronized event catalog and, when the match is ambiguous or has low confidence, ask the user to choose the correct event. | 9 |
| FR-005 | The system shall present the user's ticket wallet, listing their tickets and upcoming events with the essential event information. | 9 |
| FR-006 | The system shall allow a user to follow a public event (or mark it as "going") to receive its changes and alerts. | 9 |
| FR-008 | The system shall allow a user to remove a ticket or stop following an event. | 6 |
| FR-009 | The system shall present a simple list of events near the user or happening today, allowing the user to follow them. | 6 |
| FR-010 | The system shall allow a user to provide an approximate or manually selected location for the nearby events list. | 5 |

### 1.2 Official Event Changes

| ID | Statement | Priority |
|---|---|---|
| FR-011 | The system shall periodically synchronize events from the external ticketing source and detect changes to their status, date, time or venue (e.g., cancellation, postponement, relocation). | 10 |
| FR-012 | The system shall periodically synchronize public events from the external municipal agenda source and detect changes to them. | 3 |
| FR-013 | The system shall notify every user connected to an event (ticket holders and followers) when a change to that event is detected or published. | 10 |
| FR-014 | The system shall display an event's current status and its change history, each change with its source and time. | 8 |

### 1.3 Organization Backoffice

| ID | Statement | Priority |
|---|---|---|
| FR-015 | The system shall allow an organization to create and publish an event with its details, including location and schedule. | 8 |
| FR-016 | The system shall allow an organization to update or cancel its own events, triggering notifications to connected users. | 9 |
| FR-017 | The system shall allow an organization to request ownership of an existing synchronized event, subject to administrator approval. | 7 |
| FR-018 | The system shall allow an organization to publish the current crowd level of its event (low, moderate, high, full) through the backoffice. | 10 |
| FR-019 | The system shall allow an organization to publish queue waiting times for points of its event (e.g., entrances, ticket office, bars, toilets). | 8 |
| FR-020 | The system shall allow an organization to send an operational alert (e.g., entrance closure, capacity reached, schedule delay) to every user connected to its event. | 10 |
| FR-021 | The system shall generate an AI-assisted alert draft from the organization's brief input and the event's current state, which the organization must review, edit and approve before it is sent. | 8 |
| FR-022 | The system shall provide predefined alert templates that the organization can fill in and send without AI assistance. | 9 |
| FR-023 | The system shall present the organization with a live dashboard showing the event's crowd level, queues, open incidents and alerts sent. | 9 |
| FR-024 | The system shall allow an organization to define its event's capacity and show the current crowd level relative to it. | 7 |
| FR-025 | The system shall provide an API through which an organization's systems (e.g., entrance counters) can submit crowd counts and queue times automatically. | 6 |
| FR-026 | The system shall allow an organization to create an attendance-intention poll for an event or proposed event. | 7 |
| FR-027 | The system shall allow a user to respond to an available attendance-intention poll. | 7 |
| FR-028 | The system shall present poll responses to the organization as indications of potential attendance and shall not represent them as guaranteed attendance. | 7 |
| FR-051 | The system shall allow an organization to publish requests on the event page — confirmations (e.g., "Is Gate B still closed?"), polls or requests for ideas — and mark each request as urgent or non-urgent. | 8 |
| FR-052 | The system shall notify connected users only of urgent requests; opening the notification shall take the user to the request on the event page. Non-urgent requests shall remain available on the event page without a notification. | 8 |
| FR-029 | The system shall allow an organization to view reported incidents and mark them as acknowledged or resolved, notifying the reporting user. | 8 |
| FR-053 | The system shall use AI to group similar incident reports into a single incident with a short summary, allow the organization to split incorrectly grouped reports, and fall back to grouping by category and time window when the AI service is unavailable. | 8 |
| FR-060 | The system shall allow an organization to choose, per event, which predefined report categories trigger confirmation prompts; events without an owning organization shall use the default settings. | 8 |
| FR-030 | The system shall present the organization with a post-event summary (crowd peaks, incidents, alerts sent). | 3 |

### 1.4 On-Site Information for Participants

| ID | Statement | Priority |
|---|---|---|
| FR-031 | The system shall display an event's crowd level, queue times and alerts to connected users. | 10 |
| FR-032 | The system shall label every piece of event information with its source (official source, organization, participant estimate, AI-generated) and its last-update time. | 9 |
| FR-033 | The system shall flag or hide on-site information that has not been updated within a configured period. | 8 |
| FR-034 | The system shall allow a user to report something at an event by choosing a predefined category (e.g., crowd level, queue length, lost and found, accessibility barrier, on-site problem) and adding an optional description. | 8 |
| FR-057 | The system shall ask a limited random sample of the users connected to an event who have allowed location access, excluding the reporter, to confirm a report with a single "Yes" or "No" while the event is taking place, only for categories that trigger confirmation prompts, stopping when enough answers are collected and limiting how often each user is asked. | 8 |
| FR-058 | The system shall compute a rule-based confidence for each report — raised by "Yes" answers, lowered by "No" answers and decaying over time — and present it as Low, Medium or High. | 8 |
| FR-059 | The system shall display reports with Medium or High confidence on the event page as participant estimates, showing their confidence and a notice that they are estimates that may not reflect reality. Crowd level and queue estimates shall only be shown when the organization has no current value for them, and reports in personal categories (e.g., lost and found) shall never be shown publicly or trigger confirmation prompts. | 8 |
| FR-064 | The system shall only allow a user to submit a report in a non-personal category, or to answer a confirmation prompt, when the user has allowed location access and their approximate location is within a configured radius of the event venue. Otherwise, it shall explain that location is only requested to ensure better report quality. Reports in personal categories (e.g., lost and found) shall not require location. | 8 |
| FR-054 | The system shall notify a participant when the organization marks their report as helpful, showing its impact (e.g., the incident was resolved, or an alert was sent to N people). | 7 |
| FR-035 | The system shall display the weather forecast for the day and location of the event. | 6 |
| FR-036 | The system shall notify connected users when the weather forecast for an event meets a configured severe-weather threshold. | 7 |
| FR-037 | The system shall make the essential information of the user's events (date, time, venue, ticket details, latest change) available offline. | 5 |
| FR-055 | The system shall display public transport options to the event venue (e.g., metro, bus, train) with their upcoming departure times, and indicate service disruptions when the transport source reports them. | 6 |
| FR-056 | The system shall display an integrated map showing the event venue and routes to it from the user's location. | 6 |
| FR-038 | The system shall allow a user to view a private history of their reported incidents and the recognitions received for them. | 5 |

### 1.5 Accounts and Preferences

| ID | Statement | Priority |
|---|---|---|
| FR-039 | The system shall identify users and assign them a role (participant, organization member, administrator). | 10 |
| FR-040 | The system shall allow an administrator to verify organizations before they can publish official information. | 6 |
| FR-041 | The system shall allow a user to choose, per event, whether to receive notifications for all organization requests, only urgent requests, or none, and whether to receive confirmation prompts for participant reports; official event changes and operational alerts shall always be delivered. | 8 |
| FR-042 | The system shall allow authenticated users to permanently delete their account and all associated personal data. | 8 |
| FR-043 | The system shall support registration and authentication with email and password credentials. | 9 |
| FR-044 | The system shall support registration and authentication through federated identity providers (Google and Facebook). | 2 |

### 1.6 Out of Scope for the MVP

| ID | Statement | Priority |
|---|---|---|
| FR-045 | The system shall allow a user to search events within a radius and filter them by category. | 2 |
| FR-046 | The system shall display events on a map. | 2 |
| FR-047 | The system shall offer personalized event recommendations. | 1 |
| FR-048 | The system shall display crowd levels per zone of the event venue. | 3 |
| FR-049 | The system shall allow users to buy tickets within the app. | 1 |
| FR-050 | The system shall allow users to connect with other participants of the same event. | 1 |
| FR-061 | The system shall allow an organization to define custom report categories for its events and choose whether they trigger confirmation prompts; events without an owning organization shall only offer the predefined categories. | 3 |
| FR-062 | The system shall automatically restrict users whose reports repeatedly end with Low confidence: first a warning, then a temporary restriction from reporting, and finally a ban from reporting, while every other feature remains available, informing the user of the reason and duration at each step. | 3 |
| FR-063 | The system shall detect that a connected user has left the event — using approximate location when the user has consented, or otherwise by asking the user — and stop sending them on-site confirmation prompts. To be investigated in a timeboxed spike before any implementation. | 2 |

---

## 2. Non-Functional Requirements

| ID | Statement | Priority |
|---|---|---|
| NFR-001 | Critical notifications (cancellation, rescheduling, venue change, operational alert) shall be delivered within 1 minute of the change being detected or published. | 9 |
| NFR-002 | Changes in the external event sources shall be detected within 5 minutes of occurring. | 8 |
| NFR-003 | Notifications shall be delivered at least once and processed idempotently, so that a user never receives the same notification twice for the same event change. | 9 |
| NFR-004 | Asynchronous workflows shall retry failed processing with backoff and move repeatedly failing messages to a dead-letter queue for inspection. | 8 |
| NFR-005 | When an external event source is unavailable, delayed or returns invalid data, the system shall keep operating, reject and log invalid data, and show users the time of the last successful synchronization. | 9 |
| NFR-006 | When the AI service is unavailable, the system shall fall back to manual ticket entry and alert templates so that the principal workflows continue. | 9 |
| NFR-007 | The system shall never present AI-generated content as verified fact; AI output shall be labelled until confirmed by the user or organization. | 9 |
| NFR-008 | Information published by an organization shall be visible to connected users within 30 seconds. | 8 |
| NFR-009 | 95% of user requests shall receive a response within 2 seconds, excluding AI-assisted ticket extraction, which shall complete within 30 seconds. | 8 |
| NFR-010 | The system shall provide structured application logs, relevant operational metrics, error reporting, and correlation or trace identifiers for its deployed components. | 8 |
| NFR-011 | The system shall provide an operational health check for each deployed backend component. | 8 |
| NFR-012 | The system shall provide an operational dashboard with service-level indicators, including notification delivery latency, synchronization lag, failed asynchronous jobs, and AI latency and error rate. | 4 |
| NFR-013 | The system shall support unit, integration, and relevant end-to-end testing of the principal workflows. | 8 |
| NFR-014 | The system shall be built and delivered through an automated CI/CD pipeline. | 8 |
| NFR-015 | The AI-assisted capabilities shall be evaluated against a documented dataset, including difficult and adverse cases, with defined acceptance criteria. | 8 |
| NFR-016 | The system shall be delivered using containers with reproducible builds and environment-specific configuration. | 8 |
| NFR-017 | The participant interface shall be usable on mobile devices. | 7 |
| NFR-018 | The principal user journeys shall meet WCAG 2.1 AA, and alerts and crowd levels shall not be conveyed by color alone. | 7 |
| NFR-019 | Changes to a report's confidence shall be reflected on the event page within 30 seconds. | 7 |

---

## 3. Security Requirements

| ID | Statement | Priority |
|---|---|---|
| SR-001 | The system shall restrict organization actions (event changes, on-site information, alerts, polls, incidents) to members of the organization that owns the event. | 10 |
| SR-002 | The system shall only allow the official event sources and verified organizations to publish official event information and alerts. | 10 |
| SR-003 | The system shall scope user-specific data (tickets, followed events, incidents, preferences, location) to the user or authorized role permitted to access it. | 8 |
| SR-004 | The system shall remove personal data (e.g., name, email, tax number) and access codes from ticket content before sending it to an external AI provider. | 8 |
| SR-005 | The system shall treat ticket files, emails and organization input as untrusted data for the AI, validate AI output against a fixed schema, and never allow the AI to perform actions. | 8 |
| SR-006 | The system shall keep an audit log of event changes, alerts sent and incident status changes, including who performed them and when. | 7 |
| SR-007 | The system shall not make the reporting user's identity public in incident reports. | 7 |
| SR-008 | The system shall manage secrets outside the source repository. | 7 |
| SR-009 | The system shall request user consent before using location data and shall use only approximate location. | 6 |
| SR-010 | The system shall authenticate organization API requests with credentials scoped to the organization and its events. | 6 |
| SR-011 | The system shall apply rate limiting to unauthenticated requests, to reports and to confirmation answers. | 6 |
| SR-012 | The system shall authenticate service-to-service communication between backend components. | 5 |
| SR-013 | The system shall store user passwords using a strong one-way hashing algorithm with salt. | 8 |
| SR-014 | The system shall use OAuth 2.0 / OpenID Connect protocols for authentication through Google and Facebook. | 2 |
| SR-015 | The system shall apply rate limiting to account creation and login to prevent abuse. | 7 |
| SR-016 | The system shall accept at most one confirmation answer per user per report, shall not let reporters confirm their own reports, and shall not reveal who confirmed or denied a report. | 8 |
| SR-017 | The system shall check location for reporting and confirmation only once, at the moment of the action, store only the result of the check (not the location itself), and never track location continuously. | 8 |

---

## 4. Technical Requirements

| ID | Statement | Priority |
|---|---|---|
| TR-001 | The system shall include an independently deployable ticketing source simulator that publishes events and their changes. | 9 |
| TR-002 | The system shall consist of at least two independently deployable backend components (e.g., event synchronization, notifications, core services). | 9 |
| TR-003 | The system shall use a message broker for asynchronous communication between event synchronization and notifications. | 8 |
| TR-004 | The system shall include a municipal agenda simulator that publishes public events and their changes. | 3 |
| TR-005 | The system shall include an entrance-counter simulator that submits crowd counts through the organization API. | 5 |
| TR-006 | The system shall cache event status and on-site information reads, invalidating the cache when the information changes. | 4 |
