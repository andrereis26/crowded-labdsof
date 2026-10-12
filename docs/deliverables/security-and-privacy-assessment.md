# Security and Privacy Assessment

This assessment is based on the [Requirements Specification](./requirements.md), [Domain Model](./diagrams/domain-model.svg), [Data Model](./diagrams/data-model.svg) and [Technical Design](./technical-design.md).

## 1. Initial Threat Model

### Scope and assets

The scope includes the participant and backoffice apps, Router, Core, Jobs, Notifications, PostgreSQL, Redis, the message broker and external integrations.

The main assets to protect are:

- User accounts, credentials, roles and user-specific data
- Organization API credentials and event-management permissions
- Tickets, imported ticket content and entry codes
- Location-check results
- Reports, confirmation answers and reporter identities
- Event information, on-site updates, alerts and incidents
- Notifications, preferences and audit records

### Trust boundaries

These boundaries show where information passes between different parts of the system:

- **Clients and backend:** participant and backoffice requests pass through the Router to backend services.
- **Backend services:** Core, Jobs and Notifications exchange data through service APIs and the message broker.
- **External integrations:** Crowded connects to ticketing and municipal event sources, AI, weather and maps / transport providers, and Firebase Cloud Messaging. These external services are outside Crowded's control.
- **AI workflows:** ticket files, emails, organization input and report descriptions are untrusted content. AI output must be validated and AI must not perform actions (SR-005). Personal data and entry codes must be removed from ticket content before it is sent to an external AI provider (SR-004).

### STRIDE Threat Model

| STRIDE category | Example threat | Related [Requirements Specification](./requirements.md) and architecture decisions |
|---|---|---|
| **Spoofing** | Someone impersonates a user, organization or backend service. | Organization-scoped credentials (SR-010), authenticated service communication (SR-012), password hashing (SR-013), JWTs and revocation ([ADR 0005](./adr/0005-authentication-in-core-with-jwt.md)). |
| **Tampering** | Unauthorized changes to event information, or untrusted content influencing AI output. | Organization and publishing restrictions (SR-001, SR-002). Validated AI output and untrusted-input handling (SR-005). Organizer review of alert drafts (FR-021). |
| **Repudiation** | An actor denies making an event change, sending an alert or changing incident status. | Audit records of actions, actors and times (SR-006). |
| **Information Disclosure** | Personal data, report identities, confirmation answers or ticket entry codes are exposed. | User-data access limits (SR-003). AI data removal (SR-004). Identity protections (SR-007, SR-016). Entry-code protections (SR-018). |
| **Information Disclosure** | Location is collected or retained beyond the documented one-time check. | Consent and approximate location (SR-009). Store only the check result and do not track continuously (SR-017). |
| **Denial of Service** | Excessive requests affect account, report or confirmation workflows. AI unavailability blocks a workflow. | Rate limits (SR-011, SR-015). Non-AI fallbacks (NFR-006). |
| **Elevation of Privilege** | A user acts outside their role, an organization changes another organization's event, or a service writes another service's tables. | Role and organization restrictions (SR-001, SR-002, SR-010). One owning service per table ([ADR 0002](./adr/0002-shared-database-with-table-ownership.md)). |

## 2. Sensitive Data

The table below summarizes personal, privacy-sensitive and credential data identified in the project documents:

| Data | Examples in the data model | Why it needs protection |
|---|---|---|
| Account and user-linked data | User email, user IDs linked to tickets, reports, responses, notifications and preferences, and location consent. | These fields identify a user or connect their account to activity and preferences. |
| Reports and confirmation responses | Report description, reporter and responder links, confirmation answer and `locationVerified`. | A report description may contain personal details, such as information about a lost item. Answers and reporter links associate people with reports. `locationVerified` can reveal that a user passed a proximity check when linked to an action. |
| Credentials | User `passwordHash`, API credential `secretHash`, and ticket `entryCode`. | These values are used to authenticate users or organizations, or to access a ticket. |
| Imported ticket content | Uploaded ticket PDFs, screenshots and confirmation emails. | These may contain names, email addresses, tax numbers and access codes. The structured extraction fields exclude personal data and entry codes ([Responsible AI Opportunity Assessment](./responsible-ai-opportunity-assessment.md)). |

## 3. Access-Control Requirements

These access requirements cover user data, organization actions and communication between backend services:

| Access area | Related requirements and decisions |
|---|---|
| User accounts and personal data | Limit access to a user's tickets, followed events, incidents, preferences and location data to that user or an authorized role (SR-003). |
| Organization actions | Allow organization members to manage only events owned by their organization (SR-001). Only official event sources and verified organizations may publish official event information and alerts (SR-002). |
| Organization API | Authenticate API requests with credentials that grant access only to the relevant organization and its events (SR-010). |
| Ticket entry codes | Show entry codes only to the ticket owner (SR-018). |
| Confirmation responses | Allow at most one answer per user per report. Do not allow the reporter to confirm their own report. Do not reveal who answered (SR-016). |
| Backend services | Authenticate communication between backend components (SR-012). Each database table has one owning service. Only that service may write to it ([ADR 0002](./adr/0002-shared-database-with-table-ownership.md)). |

The account roles are participant, organization member and administrator (FR-039). Organization verification is performed by an administrator before an organization can publish official information (FR-040).

## 4. Data-Minimization Measures

The following measures limit personal data and credentials used in ticket and location workflows:

| Measure | Related requirements and decisions |
|---|---|
| Ticket processing | Extract ticket text locally. Remove personal data and access codes before sending content to an external AI provider. Do not store entry codes in plain text ([Responsible AI Opportunity Assessment](./responsible-ai-opportunity-assessment.md), SR-004, SR-018). |
| Ticket extraction data | Store structured extracted fields without personal data or entry codes (SR-004, SR-018). |
| Location | Request consent and use approximate location. For report and confirmation checks, check it only at the time of the action, store the check result rather than the location, and do not track continuously (SR-009, SR-017). |

## 5. Retention Considerations

The deletion points for uploaded ticket files, entry codes and account data are listed below:

| Data | Related requirements and decisions |
|---|---|
| Uploaded ticket file | Delete the uploaded file after extraction ([Responsible AI Opportunity Assessment](./responsible-ai-opportunity-assessment.md)). |
| Ticket entry code | Delete it when its ticket or account is deleted (SR-018). |
| Account and associated personal data | Permanently delete when the user requests account deletion (FR-042). |
