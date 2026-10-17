# API Outline

This document outlines the principal APIs between the client apps and the backend, and between the backend components. It builds on the [Technical Design](technical-design.md) (components and data ownership), the [Domain Model](../../README.md#domain-model) and the [Data Model](diagrams/data-model.svg), and follows the [Principal User Journeys](product-vision.md#principal-user-journeys).

## Conventions

- **Format:** field names, enum values and ids (UUID) follow the [Data Model](diagrams/data-model.svg).
- **Authentication:** Core registers users and issues short-lived JWTs; the services validate them and check the revocation list ([ADR 0005](adr/0005-authentication-in-core-with-jwt.md), [ADR 0004](adr/0004-redis-for-revocation-rate-limiting-and-cache.md)). Organization systems call the Organization API with API keys scoped to their organization and its events (SR-010).
- **Provenance:** every piece of event information carries `provenance` {`sourceType`, `sourceName`, `lastUpdatedAt`, `confirmed`}, so the app can show its source and last-update time (FR-032) and flag or hide information that has not been updated within the configured period (FR-033).
- **Rate limits:** unauthenticated requests (including public poll pages), reports, confirmation answers and poll answers (SR-011), and account creation and login (SR-015) are rate-limited. NGINX keeps a coarse per-IP limit; per-user limits run in Core ([ADR 0004](adr/0004-redis-for-revocation-rate-limiting-and-cache.md)).
- **Correlation:** requests carry a correlation id that every service logs, so one request can be followed across services (NFR-010, [ADR 0009](adr/0009-observability-stack.md)).

## Roles

The **Role** column of every table uses these names:

| Role | Who | Authentication |
|---|---|---|
| **Public** | Anyone, without an account | None; rate-limited (SR-011) |
| **Signed-in** | Any user with an account, whatever their role | JWT |
| **Participant** | User with role `PARTICIPANT`. *Connected* means they have a ticket for or follow the event; *owner* means the resource is theirs (SR-003) | JWT |
| **Member** | `ORGANIZATION_MEMBER` of the organization that owns the event (SR-001). *Verified* means the organization must be `VERIFIED`, because the action publishes official information (SR-002) | JWT |
| **Admin** | `ADMINISTRATOR` | JWT |
| **Org system** | An organization's own system, such as an entrance counter | API key (SR-010) |

## Shared Endpoints

These endpoints are used by several journeys and are described once.

### Accounts (Core)

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/auth/register` | Create an account (FR-043). With `organizationName`, also create the organization, pending verification (FR-040). | `email`, `password`, `organizationName` | `userId`, `role` | Public |
| POST | `/auth/login` | Sign in and get a short-lived JWT. | `email`, `password` | `accessToken`, `expiresAt`, `user` {`id`, `role`, `organizationId`} | Public |
| POST | `/auth/logout` | Revoke the current token. | – | – | Signed-in |
| GET | `/me` | Current user. | – | `id`, `email`, `role`, `organizationId`, `locationConsentGranted` | Signed-in |
| PUT | `/me/location-consent` | Grant or withdraw location consent (SR-009). | `granted` | `granted` | Participant |
| DELETE | `/me` | Delete the account and its personal data, and publish `UserDeleted` (FR-042). | – | – | Signed-in |

### Event page (Core)

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/events/{eventId}` | Event page for participants and live dashboard for the organization (FR-014, FR-023, FR-031, FR-035, FR-059, FR-072). On-site information carries `provenance`. Members also receive the dashboard fields. | – | `id`, `name`, `status`, `tentativePeriod`, `startDateTime`, `doorsOpenAt`, `endDateTime`, `venue`, `organization`, `lastSyncedAt`, `latestChange`, `activeAlerts[]`, `crowd` {`level`, `headCount`, `capacity`}, `queues[]`, `weather[]`, `estimates[]` {`categoryCode`, `confirmations` {`yes`, `no`, `lastConfirmedAt`}}, `connection`. Members: `openIncidents`, `alertsSent`, `connectedUsers`, `sourceConflicts[]` (FR-078) | Signed-in |
| GET | `/events/{eventId}/changes` | Change history, newest first, with what each change means for the participant (FR-014, FR-075, FR-078). | – | List of {`id`, `type`, `severity`, `previousValue`, `newValue`, `ticketStillValid`, `refundInfoUrl`, `provenance`, `corroboratingSourceType`, `corroboratedAt`} | Signed-in |
| GET | `/events/{eventId}/live` | Server-Sent Events stream: one event each time a part of the event page changes (details, changes, crowd, queues, alerts, estimates; incidents for Members), with the same shape as in `GET /events/{eventId}` ([ADR 0007](adr/0007-live-updates-with-server-sent-events.md), NFR-008, NFR-019). | – | Stream | Signed-in |

Participant estimates never include the report description, which may contain personal data, or the confidence level, which only the organization sees (FR-058, FR-059).

### Notification center (Notifications)

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/notifications` | Notification center, newest first (FR-067). | – | `notifications[]` {`id`, `type`, `title`, `body`, `eventId`, `causeId`, `createdAt`, `readAt`}, `unreadCount` | Signed-in |
| POST | `/notifications/{notificationId}/read` | Mark a notification as read (FR-067). | – | – | Signed-in (recipient) |
| GET | `/notifications/stream` | SSE stream of new notifications, shown as a live banner, and of the unread count (FR-068). After reconnecting, the client fetches what it missed with `GET /notifications` ([ADR 0007](adr/0007-live-updates-with-server-sent-events.md)). | – | Stream | Signed-in |
| GET | `/notifications/settings` | Overall preferences and per-event overrides. | – | `defaults[]` {`type`, `enabled`}, `overrides[]` {`connectionId`, `type`, `enabled`} | Signed-in |
| PUT | `/notifications/settings` | Turn a notification type on or off, for every event (FR-065) or, with `connectionId`, for one event (FR-041). The client asks for confirmation before turning off official changes or operational alerts (FR-066). | `type`, `enabled`, `connectionId` | `type`, `enabled`, `connectionId` | Signed-in |
| POST | `/notifications/devices` | Register the mobile app's push token. Added with push, after in-app notifications (FR-069, [ADR 0010](adr/0010-external-integrations.md)). | `token`, `platform` | – | Participant |

## Principal User Journeys

Each journey lists the endpoints it uses. Endpoints from [Shared Endpoints](#shared-endpoints) or from an earlier journey are referenced by path.

### 1. Test interest in an event

The organization publishes a proposed event with an interest poll; participants open the public link, answer and follow the event; the organization then confirms or withdraws it.

Also uses: `POST /events/{eventId}/follow` (journey 2), `POST /events/{eventId}/changes` with type `CONFIRMATION` or `CANCELLATION` (journey 3), and the notification center.

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/events` | Publish an event, proposed or scheduled (FR-015, FR-024, FR-076). A proposed event has a `tentativePeriod` and no date, time or venue. | `name`, `description`, `status` (`PROPOSED` or `SCHEDULED`), `tentativePeriod`, `startDateTime`, `doorsOpenAt`, `endDateTime`, `venue`, `capacity`, `visibility` | `eventId`, `status` | Member (verified) |
| POST | `/events/{eventId}/polls` | Create an interest poll with the organization's own options (FR-026). | `question`, `options[]` (two or more labels), `opensAt`, `closesAt` | `pollId`, `publicSlug` | Member |
| POST | `/polls/{pollId}/close` | Close a poll early (FR-026). | – | `closesAt` | Member |
| GET | `/public/polls/{publicSlug}` | Public poll page, without an account (FR-080). | – | `pollId`, `question`, `options[]` {`id`, `label`}, `closesAt`, `event` {`id`, `name`, `status`, `tentativePeriod`} | Public |
| PUT | `/polls/{pollId}/response` | Answer, or change the answer until the poll closes. Does not follow the event (FR-027, SR-019). | `optionId` | `totals[]` {`optionId`, `count`} | Participant |
| GET | `/polls/{pollId}/results` | Totals per option, never who answered (FR-028, SR-019). Participants see them only after answering. | – | `totals[]` {`optionId`, `count`}, `myOptionId` (Participant), `followers` (Member) | Participant (answered), Member |

### 2. Connect to an event

The participant finds an event and follows it, or imports a ticket bought elsewhere and confirms the extracted details.

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/events` | "Near me / today" list, including proposed events with an open poll (FR-009, FR-010). The location is approximate or chosen manually, and is not stored. | `latitude` and `longitude`, or `place`; `date` | List of {`id`, `name`, `status`, `tentativePeriod`, `startDateTime`, `venueName`, `distanceKm`, `openPollSlug`} | Participant |
| POST | `/events/{eventId}/follow` | Follow a public or proposed event (FR-006). | `going` | `connectionId` | Participant |
| POST | `/tickets` | Import a ticket from a PDF, `.eml` or screenshot, or from manually entered fields (FR-001, FR-002, FR-003). Core removes personal data before the AI extraction (SR-004), decodes the entry code without AI (FR-070) and returns candidate events, never proposed ones (FR-004). | `file`, or `eventName`, `startDateTime`, `venueName`, `gate`, `sector`, `seat` | `ticketId`, `status` (`PENDING_CONFIRMATION`), `extraction` {`extractedFields`, `fieldConfidence`, `aiGenerated`}, `candidates[]` {`eventId`, `confidence`}, `hasEntryCode` | Participant |
| POST | `/tickets/{ticketId}/confirm` | Confirm the corrected fields and the chosen event (FR-003, FR-004, NFR-007). | `eventId`, `gate`, `sector`, `seat` | `ticketId`, `status` (`CONFIRMED`), `connectionId` | Participant (owner) |
| GET | `/me/connections` | Ticket wallet and followed events, also cached on the device for offline use (FR-005, FR-037). | – | `connections[]` {`connectionId`, `type` (`TICKET` or `FOLLOW`), `event` {`id`, `name`, `status`, `startDateTime`, `doorsOpenAt`, `venueName`, `latestChange`, `activeAlerts[]`}, `ticket` {`gate`, `sector`, `seat`, `status`, `hasEntryCode`}}, `syncedAt` | Participant |
| GET | `/tickets/{ticketId}/entry-code` | Entry code to show full screen and keep offline (FR-071, SR-018). | – | `format` (`QR`, `AZTEC` or `PDF417`), `value`, `importMethod` | Participant (owner) |
| DELETE | `/me/connections/{connectionId}` | Remove a ticket, with its entry code, or stop following an event (FR-008, SR-018). | – | – | Participant (owner) |

### 3. Get notified of changes

A change reaches Crowded from the ticketing source (Jobs detects it) or from the organization (published in the backoffice). Core records it once, sets its severity and asks Notifications to notify the connected users (see [Messages](#messages)). The participant opens the notification and sees what changes for them.

Also uses: `GET /notifications`, `GET /notifications/stream`, `POST /notifications/{notificationId}/read`, `GET /events/{eventId}` and `GET /events/{eventId}/changes`.

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/events/{eventId}/changes` | Publish a change: confirm or withdraw a proposed event, postpone, move, cancel, or change the time or doors opening time (FR-016, FR-072, FR-075, FR-076). Core sets the severity (FR-074) and, when the ticketing source already reported the same change, adds the organization as corroborating source without notifying again (FR-078). | `type`, `newValue` (changed fields, such as `startDateTime`, `doorsOpenAt` or `venue`), `ticketStillValid`, `refundInfoUrl` | `changeId`, `severity`, `corroborated` | Member (verified) |
| GET | `/source-events` | Search the events synchronized from the ticketing source, to link one (FR-079). | `query` | List of {`ticketingSourceEventId`, `name`, `startDateTime`, `venueName`} | Member |
| PUT | `/events/{eventId}/ticketing-link` | Link the event to its ticketing-source event, so changes and tickets apply to it (FR-079). | `ticketingSourceEventId` | `ticketingSourceEventId`, `lastSyncedAt` | Member |

### 4. Check conditions and decide

On the event day, the participant checks the event page, reports what they see and confirms other participants' reports.

Also uses: `GET /events/{eventId}` and `GET /events/{eventId}/live`. Maps, transport and the displayed weather are called by the app directly from the providers ([Technical Design](technical-design.md#logical-view-level-2)).

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/events/{eventId}/report-categories` | Report categories and whether they trigger confirmation prompts at this event (FR-034, FR-060). | – | List of {`code`, `name`, `personal`, `promptsEnabled`} | Signed-in |
| POST | `/events/{eventId}/reports` | Report something at the event (FR-034). Non-personal categories need a location within the venue radius, checked once and not stored (FR-064, SR-017). Core publishes `ReportSubmitted` and, when prompts are enabled, asks a sample of connected users to confirm (FR-057). | `categoryCode`, `description`, `reportedCrowdLevel`, `reportedQueuePoint`, `reportedWaitMinutes`, `location` {`latitude`, `longitude`} | `reportId`, `locationVerified` | Participant (connected) |
| PUT | `/reports/{reportId}/confirmation` | Answer a confirmation prompt with "Yes" or "No" (FR-057, FR-058, FR-064). One answer per user, never by the reporter, never revealed (SR-016). | `answer` (`YES` or `NO`), `location` {`latitude`, `longitude`} | – | Participant (asked) |
| GET | `/me/contributions` | Private history of reports and confirmations, with their outcome and recognitions (FR-038, FR-054). | – | List of {`type` (`REPORT` or `CONFIRMATION`), `id`, `eventId`, `categoryCode`, `at`, `outcome`, `recognition` {`impact`, `reachedUsers`}} | Participant |

### 5. Manage the event

Before and during a confirmed event, the organization keeps on-site information up to date, sends and resolves alerts, handles incidents and publishes surveys; after it, it reviews the summary. The live dashboard is `GET /events/{eventId}` with `GET /events/{eventId}/live`.

Also uses: `POST /events/{eventId}/changes` (journey 3).

**On-site information and settings**

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/organizations/me/events` | The organization's events (backoffice home). | – | List of {`id`, `name`, `status`, `startDateTime`, `connectedUsers`} | Member |
| PUT | `/events/{eventId}/crowd` | Publish the crowd level (FR-018). | `level`, `headCount` | `crowd` {`level`, `headCount`, `provenance`} | Member |
| PUT | `/events/{eventId}/queues` | Publish queue waiting times (FR-019). | `queues[]` {`kind`, `pointName`, `waitMinutes`} | `queues[]` with `provenance` | Member |
| PUT | `/events/{eventId}/report-categories/{code}` | Choose whether a category triggers confirmation prompts (FR-060). | `promptsEnabled` | `code`, `promptsEnabled` | Member |
| POST | `/events/{eventId}/ownership-requests` | Request ownership of a synchronized event (FR-017). | – | `requestId`, `status` (`PENDING`) | Member (verified) |

**Alerts**

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/events/{eventId}/alerts` | Create an alert as a draft. With `organizerInput`, Core asks the AI for a draft based on the event's current state (FR-021); when the AI is unavailable, the draft is empty and the organization writes the message (NFR-006). `incidentId` links an alert triggered by an incident. | `type`, `organizerInput`, `incidentId` | `alertId`, `status` (`DRAFT`), `draft` {`draftText`, `factsUsed`, `aiGenerated`} | Member (verified) |
| POST | `/alerts/{alertId}/send` | Send the reviewed alert to connected users (FR-020, FR-021, SR-006). | `type`, `severity`, `message` | `status` (`SENT`), `sentAt`, `recipientsCount` | Member (verified) |
| POST | `/alerts/{alertId}/resolve` | Mark the alert as resolved and tell connected users it has ended (FR-073). | – | `resolvedAt` | Member |

**Incidents and reports**

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/events/{eventId}/incidents` | Incidents with their grouped reports, without the reporters' identity (FR-029, FR-053, SR-007). | `status` | List of {`id`, `summary`, `status`, `groupingMethod`, `reports[]` {`id`, `categoryCode`, `description`, `reportedAt`, `confidenceLevel`}} | Member |
| PATCH | `/incidents/{incidentId}` | Acknowledge or resolve an incident and notify its reporters (FR-029, SR-006). | `status` (`ACKNOWLEDGED` or `RESOLVED`) | `status` | Member |
| POST | `/incidents/{incidentId}/split` | Move wrongly grouped reports to a new incident (FR-053). | `reportIds[]` | `incidentId` | Member |
| POST | `/reports/{reportId}/helpful` | Mark a report as helpful and send its recognition to the reporter (FR-054). | – | `recognitionId`, `impact`, `reachedUsers` | Member |

**Surveys**

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/events/{eventId}/surveys` | Publish a survey on a confirmed event; urgent surveys are notified (FR-051, FR-052). | `type`, `text`, `options[]`, `urgent`, `closesAt` | `surveyId`, `publishedAt` | Member |
| GET | `/events/{eventId}/surveys` | Surveys of the event, with the user's own answer for participants and the totals or ideas, without names, for Members. | – | List of {`id`, `type`, `text`, `options[]`, `urgent`, `closesAt`, `myAnswer` (Participant), `results` (Member)} | Participant (connected), Member |
| PUT | `/surveys/{surveyId}/response` | Answer a survey, or change the answer (FR-051). | `answer` | `respondedAt` | Participant (connected) |

**After the event**

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/events/{eventId}/summary` | Post-event summary (FR-030). | – | `crowdPeaks[]`, `incidents`, `alertsSent`, `connectedUsers`, `notifiedBeforeDoorsOpen` | Member |

**Organization API credentials**

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/organizations/me/api-credentials` | Create an API key for the organization's systems (SR-010). The secret is shown once. | – | `keyId`, `secret`, `scope` | Member |
| DELETE | `/organizations/me/api-credentials/{keyId}` | Revoke an API key. | – | – | Member |

**Organization API (Core)**

Organization systems call the Organization API with an API key instead of a JWT ([ADR 0005](adr/0005-authentication-in-core-with-jwt.md), SR-010). The entrance-counter simulator submits its counts through it (TR-005).

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| PUT | `/events/{eventId}/crowd` | Submit a crowd count automatically (FR-025). Without `level`, Core derives it from `headCount` and the capacity (FR-024). | `headCount`, `level` | `level`, `headCount`, `provenance` | Org system |
| PUT | `/events/{eventId}/queues` | Submit queue waiting times automatically (FR-025). | `queues[]` {`kind`, `pointName`, `waitMinutes`} | `queues[]` with `provenance` | Org system |

### Administration

An administrator verifies organizations before they publish official information and decides ownership requests.

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/organizations` | Organizations, filtered by verification status (FR-040). | `verificationStatus` | List of {`id`, `name`, `verificationStatus`} | Admin |
| PATCH | `/organizations/{organizationId}` | Verify or reject an organization (FR-040). | `verificationStatus` (`VERIFIED` or `REJECTED`) | `verificationStatus`, `verifiedAt` | Admin |
| GET | `/ownership-requests` | Ownership requests, filtered by status (FR-017). | `status` | List of {`id`, `eventId`, `organizationId`, `requestedAt`} | Admin |
| PATCH | `/ownership-requests/{requestId}` | Approve or reject a request; on approval, the organization owns the event (FR-017). | `status` (`APPROVED` or `REJECTED`) | `status`, `decidedAt` | Admin |

## Between Components

### Router

The Router (NGINX) is the single entry point ([Technical Design](technical-design.md#logical-view-level-2)). It sends the requests for the endpoints marked Core, including the Organization API, to Core, and those marked Notifications to Notifications, keeping the SSE streams open without response buffering ([ADR 0007](adr/0007-live-updates-with-server-sent-events.md)). The services do not call each other: everything between them goes through the Message Broker, authenticated (SR-012).

### Messages

Messages carry ids only; consumers read the current data from the database ([ADR 0003](adr/0003-rabbitmq-for-asynchronous-messaging.md)). Failed processing is retried with backoff, and repeatedly failing messages are moved to a dead-letter queue (NFR-004). The only exception to ids only is `ReportsGrouped`, which carries the grouping result because Jobs does not store it.

| Message | Publisher | Consumer | Payload | Effect |
|---|---|---|---|---|
| `EventChangeDetected` | Jobs | Core | `snapshotId`, `ticketingSourceEventId` | A source event is new or has changed. Core adds it to the catalog or updates it, and records an `EventChange` or adds the ticketing source as corroborating source of an existing one (FR-011, FR-078). |
| `WeatherForecastFetched` | Jobs | Core | `eventId`, `snapshotId` | Core stores the forecast (FR-035) and, when it meets the severe-weather threshold, notifies connected users (FR-036). |
| `ReportSubmitted` | Core | Jobs | `eventId`, `reportId` | Jobs groups the report with similar ones, by category and time window when the AI is unavailable (FR-053). |
| `ReportsGrouped` | Jobs | Core | `eventId`, `groupingMethod`, `groups[]` {`reportIds[]`, `summary`} | Core creates or updates incidents; AI summaries are labelled until the organization acts on them (FR-053, NFR-007). |
| `NotificationRequested` | Core | Notifications | `messageId`, `type`, `eventId`, `causeId`, `userIds[]` | Notifications notifies the users connected to the event or, when given, only `userIds`. |
| `UserDeleted` | Core | Notifications | `userId` | Notifications deletes the user's notifications, settings and device tokens (FR-042). |

Core publishes `NotificationRequested` with the `type` and `causeId` that the `Notification` will have:

- `OFFICIAL_CHANGE`: a recorded `EventChange` (FR-013); not when the change only gains a corroborating source (FR-078).
- `OPERATIONAL_ALERT`: an `Alert` sent or resolved (FR-020, FR-073).
- `ORGANIZATION_SURVEY`: an urgent `EventSurvey` (FR-052).
- `SEVERE_WEATHER`: a `WeatherForecast` that meets the threshold (FR-036).
- `CONFIRMATION_REQUEST`: a `Report`, with the sampled users in `userIds` (FR-057).
- `INCIDENT_STATUS`: an `Incident`, with its reporters in `userIds` (FR-029).
- `RECOGNITION`: a `Recognition`, with the reporter in `userIds` (FR-054).

Notifications skips recipients who turned the type off (FR-041, FR-065), stores one `Notification` per recipient, and sends it on `GET /notifications/stream`, and later as a push. The `deduplicationKey` is the recipient plus the trigger (`messageId`) and is unique, so a redelivered message never notifies twice (NFR-003).

### External integrations

The ticketing source is our simulator (TR-001), so its API is part of this outline. AI, weather, maps and transport, and push (Firebase Cloud Messaging) are called through adapters ([ADR 0010](adr/0010-external-integrations.md)).

| Endpoint | Caller | Purpose |
|---|---|---|
| `GET /events?updatedSince={time}` | Jobs | Periodic synchronization, returning a list of {`id`, `name`, `startDateTime`, `doorsOpenAt`, `endDateTime`, `status`, `venue`, `ticketStillValid`, `refundInfoUrl`, `updatedAt`}. Changes are detected within 5 minutes (NFR-002); invalid data is rejected and logged (NFR-005). |
| `PUT /events/{id}` | Demo and tests | Change an event in the simulator, to trigger a detected change. |

## Walking Skeleton

The walking skeleton implements the core of journey 3 end to end: **a change made in the ticketing simulator reaches a participant who follows the event, in the notification center and as a live banner**. It crosses every backend component (Router, Core, Jobs, Notifications, Message Broker, Database and Cache) and the web-app container, and is the same path used in the push-failure demonstration ([Degraded Operation](technical-design.md#degraded-operation)). Ticket import, AI and push are added later.

| Step | Interface |
|---|---|
| 1. The participant signs up and signs in | `POST /auth/register`, `POST /auth/login` |
| 2. The participant finds the event and follows it | `GET /events`, `GET /events/{eventId}`, `POST /events/{eventId}/follow` |
| 3. The event changes in the simulator, and Jobs detects it | Simulator `PUT /events/{id}`, `GET /events?updatedSince={time}` |
| 4. Core records the change | `EventChangeDetected` |
| 5. Notifications notifies the follower | `NotificationRequested` (`OFFICIAL_CHANGE`) |
| 6. The open app shows a live banner and updates the badge | `GET /notifications/stream` |
| 7. The participant opens the notification center and the change | `GET /notifications`, `POST /notifications/{notificationId}/read`, `GET /events/{eventId}/changes` |
| 8. Each service reports its health | Health check of each service (NFR-011) |
