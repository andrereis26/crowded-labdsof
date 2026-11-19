# Security and Privacy Assessment

This assessment is based on the [Requirements Specification](./requirements.md), [Domain Model](./diagrams/domain-model.svg), [Data Model](./diagrams/data-model.svg) and [Technical Design](./technical-design.md).

## 1. Initial Threat Model

### Scope and assets

The scope includes the participant and backoffice apps, Router, Core, Jobs, Notifications, PostgreSQL, Redis, the message broker and external integrations.

The main assets to protect are:

- User accounts, credentials, roles and user-specific data
- Organization member accounts, temporary passwords and event-management permissions
- Access to each event's page, which only connected participants and the owning organization may open
- Tickets, imported ticket content and entry codes
- Location-check results
- Reports, confirmation answers and reporter identities
- Survey answers and the identity of who answered
- Event information, on-site updates, alerts and incidents
- Notifications, preferences and audit records

### Trust boundaries

These boundaries show where information passes between different parts of the system:

- **Clients and backend:** participant and backoffice requests pass through the Router to backend services.
- **Backend services:** Core, Jobs and Notifications exchange data through service APIs and the message broker.
- **External integrations:** Crowded connects to the AI provider, IPMA (weather), a GTFS transport feed, a maps provider and Firebase Cloud Messaging. These external services are outside Crowded's control. There is no ticketing integration, so Crowded cannot verify that an imported ticket is genuine.
- **AI workflows:** ticket files and screenshots, organization input and report descriptions are untrusted content. AI output must be validated and AI must not perform actions (SR-005). Personal data and entry codes must be removed from ticket content before it is sent to an external AI provider (SR-004).

### STRIDE Threat Model

| STRIDE category | Example threat | Related [Requirements Specification](./requirements.md) and architecture decisions |
|---|---|---|
| **Spoofing** | Someone impersonates a user, organization or backend service, or takes over an organization account with its temporary password. | Organization accounts created only by a platform administrator (FR-087, SR-002), single-use temporary passwords that expire (FR-088, SR-022), authenticated service communication (SR-012), password hashing (SR-013), JWTs and revocation ([ADR 0005](./adr/0005-authentication-in-core-with-jwt.md)). |
| **Spoofing** | Someone connects to a ticketed event without a ticket: by sending another event's identifier, by typing the event code seen on someone else's ticket, by importing a forwarded PDF, or with a forged screenshot. | The server decides the event from the code, never from an identifier sent by the client (SR-021). A typed code only completes an uploaded ticket whose QR code was decoded (FR-004). Each QR code links to one account per event (FR-084). A forged screenshot with an unused QR code still passes: a known limitation without a ticketing integration, accepted because event pages hold nothing sensitive and reporting requires being near the venue (FR-064). |
| **Spoofing** | A script injected into a page steals a signed-in user's token, another site sends requests with the user's session, or someone keeps using a session after its owner signed out. | Tokens in `HttpOnly`, `SameSite=Strict` cookies in browsers and in secure storage on phones, an Origin check on cookie-authenticated changes, sessions that end after a period without use and at a maximum age, refresh tokens that rotate and end the session when reused, and revocation on sign-out (SR-026, [ADR 0021](./adr/0021-browser-sessions-in-cookies-with-refresh-tokens.md)). |
| **Tampering** | Unauthorized changes to event information, or untrusted content influencing AI output. | Organization and publishing restrictions (SR-001, SR-002). Validated AI output and untrusted-input handling (SR-005). Organizer review of alert drafts (FR-021). |
| **Repudiation** | An actor denies making an event change, sending an alert or changing incident status. | Audit records of actions, actors and times (SR-006). |
| **Information Disclosure** | Personal data, report identities, confirmation answers or ticket entry codes are exposed. | User-data access limits (SR-003). AI data removal (SR-004). Identity protections (SR-007, SR-016). Entry-code protections (SR-018). |
| **Information Disclosure** | A signed-in user opens the page, change history or live stream of an event they are not connected to, for example from a shared link, an old notification or after removing their ticket. | Event pages, history, live streams, report categories and surveys only for connected participants and the owning organization, ending when the connection is removed (SR-020). Cached event data deleted on sign-out, account deletion and removal of the connection (SR-023). Event identifiers are UUIDs, but access never relies on them being secret. |
| **Information Disclosure** | Location is collected or retained beyond the documented one-time check. | Consent and approximate location (SR-009). Store only the check result and do not track continuously (SR-017). |
| **Denial of Service** | Excessive requests affect sign-in, ticket import, report or confirmation workflows. AI unavailability blocks a workflow. | Rate limits (SR-011, SR-015). Non-AI fallbacks (NFR-006). |
| **Elevation of Privilege** | A user acts outside their role, an organization changes another organization's event, or a service writes another service's tables. | Role and organization restrictions (SR-001, SR-002). Administrator actions audited (SR-006). One owning service per table ([ADR 0002](./adr/0002-shared-database-with-table-ownership.md)). |

## 2. Sensitive Data

The table below summarizes personal, privacy-sensitive and credential data identified in the project documents:

| Data | Examples in the data model | Why it needs protection |
|---|---|---|
| Account and user-linked data | User email, user IDs linked to tickets, reports, responses, notifications and preferences, and location consent. | These fields identify a user or connect their account to activity and preferences. |
| Reports and confirmation responses | Report description, reporter and responder links, confirmation answer and `locationVerified`. | A report description may contain personal details, such as information about a lost item. Answers and reporter links associate people with reports. `locationVerified` can reveal that a user passed a proximity check when linked to an action. |
| Credentials | User `passwordHash`, a member's temporary password, and ticket `entryCode` with its keyed hash. | These values are used to authenticate users, or to access a ticket. |
| Imported ticket content | Uploaded ticket PDFs and screenshots. | These may contain names, email addresses, tax numbers and access codes. The structured extraction fields exclude personal data and entry codes ([Responsible AI Opportunity Assessment](./responsible-ai-opportunity-assessment.md)). |

## 3. Access-Control Requirements

These access requirements cover user data, organization actions and communication between backend services:

| Access area | Related requirements and decisions |
|---|---|
| User accounts and personal data | Limit access to a participant's tickets, connections, incidents, preferences and location data to that participant or an authorized role (SR-003). |
| Event pages | Only participants connected through a ticket or the access code, and members of the owning organization, open an event's page, history, live stream, report categories and surveys; access ends with the connection (SR-020). The server decides which event a code connects to (SR-021). |
| Organization actions | Allow active organization members to manage only events owned by their organization (SR-001). Only members of organizations created by a platform administrator may publish event information, changes and alerts (SR-002). |
| Organization accounts | Only a platform administrator creates organizations and member accounts, with temporary passwords that only allow setting a new one and expire after 7 days (FR-087, FR-088, SR-022). Organization members cannot register themselves (FR-043). |
| Ticket entry codes | Show entry codes only to the ticket owner (SR-018). |
| Confirmation responses | Allow at most one answer per user per report. Do not allow the reporter to confirm their own report. Do not reveal who answered (SR-016). |
| Backend services | Authenticate communication between backend components (SR-012). Each database table has one owning service. Only that service may write to it ([ADR 0002](./adr/0002-shared-database-with-table-ownership.md)). |

The account roles are participant, organization member and platform administrator (FR-039). An organization is trusted because a platform administrator created it, so there is no separate verification step.

## 4. Data-Minimization Measures

The following measures limit personal data and credentials used in ticket and location workflows:

| Measure | Related requirements and decisions |
|---|---|
| Ticket processing | Extract ticket text locally. Remove personal data and access codes before sending content to an external AI provider. Do not store entry codes in plain text ([Responsible AI Opportunity Assessment](./responsible-ai-opportunity-assessment.md), SR-004, SR-018). |
| Ticket file handling | Treat the file as untrusted: Core decides it is a PDF by reading it, never by its name or declared type, and refuses a larger file than 10 MB (at the router and in Core). It reads at most 10 pages, at a resolution bounded by the page size, so a small file cannot declare a page that does not fit in memory. The file stays in memory and is never written to disk or served back. Imports are limited per participant (SR-005, SR-011, ADR 0004). |
| Ticket extraction data | Store structured extracted fields without personal data or entry codes (SR-004, SR-018). A ticket whose event does not use Crowded is not stored at all (FR-083). |
| Location | Request consent and use approximate location. For report and confirmation checks, check it only at the time of the action, store the check result rather than the location, and do not track continuously (SR-009, SR-017). |

## 5. Retention Considerations

The deletion points for uploaded ticket files, entry codes and account data are listed below:

| Data | Related requirements and decisions |
|---|---|
| Uploaded ticket file | Delete the uploaded file after extraction ([Responsible AI Opportunity Assessment](./responsible-ai-opportunity-assessment.md)), or as soon as the participant leaves the typed-code step. |
| Cached data on the participant's device | Delete on sign-out and account deletion, and an event's data when its connection is removed (SR-023). |
| Ticket entry code | Delete it when its ticket or account is deleted (SR-018). |
| Account and associated personal data | Permanently delete when the user requests account deletion (FR-042). |
| Survey answers | Delete with the account (FR-042); the survey totals keep the answer count without the link to the participant. |
