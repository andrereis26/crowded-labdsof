# API Outline

This document outlines the principal APIs between the client apps and the backend, and between the backend components. It builds on the [Technical Design](technical-design.md) (components and data ownership), the [Domain Model](../../README.md#domain-model) and the [Data Model](diagrams/data-model.svg), and follows the [Principal User Journeys](product-vision.md#principal-user-journeys).

## Conventions

- **Format:** field names, enum values and ids (UUID) follow the [Data Model](diagrams/data-model.svg).
- **Authentication:** Core registers participants and issues JWTs valid for 15 minutes, with a refresh token that rotates on each use; the services validate them and check the revocation lists ([ADR 0005](adr/0005-authentication-in-core-with-jwt.md), [ADR 0004](adr/0004-redis-for-revocation-rate-limiting-and-cache.md), [ADR 0021](adr/0021-browser-sessions-in-cookies-with-refresh-tokens.md)). Browsers receive both tokens in `HttpOnly`, `Secure`, `SameSite=Strict` cookies and never see them; the phone app receives them in the body, keeps them in secure storage and sends `Authorization: Bearer`. Core and Notifications read the header first, then the cookie, and refuse a POST, PUT, PATCH or DELETE that carries a Crowded cookie or asks for one when its `Origin` is not Crowded's (`ORIGIN_NOT_ALLOWED`, SR-026). Organization members get their accounts from a platform administrator. The token's `sub` is the user id; Core reads the user's role and organization from the database on each request instead of trusting claims, so a changed role or organization applies at once (SR-001).
- **Access to an event:** every endpoint under `/events/{eventId}` that a participant can call requires a connection to that event, through a ticket or the access code (SR-020). Which event a ticket or an access code connects to is decided by Core from the code, never from an `eventId` sent by the client (SR-021).
- **Provenance:** every piece of event information carries `provenance` {`sourceType`, `sourceName`, `lastUpdatedAt`, `confirmed`}, so the app can show its source and last-update time (FR-032) and flag or hide information that has not been updated within the configured period (FR-033).
- **Rate limits:** unauthenticated requests, ticket imports, reports and confirmation answers (SR-011), and sign-in and participant registration (SR-015) are rate-limited. NGINX keeps a coarse per-IP limit; per-user limits run in Core ([ADR 0004](adr/0004-redis-for-revocation-rate-limiting-and-cache.md)).
- **Correlation:** requests carry a correlation id that every service logs, so one request can be followed across services (NFR-010, [ADR 0009](adr/0009-observability-stack.md)).

## Roles

The **Role** column of every table uses these names:

| Role | Who | Authentication |
|---|---|---|
| **Public** | Anyone, without an account | None; rate-limited (SR-011) |
| **Signed-in** | Any user with an account, whatever their role | JWT, in the `crowded_access` cookie or the `Authorization` header |
| **Participant** | User with role `PARTICIPANT`. *Connected* means they have a connection to the event, through a ticket or the access code; *owner* means the resource is theirs (SR-003, SR-020) | JWT |
| **Member** | Active `ORGANIZATION_MEMBER` of the organization that owns the event (SR-001, SR-002) | JWT |
| **Admin** | `ADMINISTRATOR`, a member of the Crowded team | JWT |

## Shared Endpoints

These endpoints are used by several journeys and are described once.

### Accounts (Core)

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/auth/register` | Create a participant account (FR-043): the email in lower case, the password (8 characters to 72 bytes, with an uppercase letter, a lowercase letter, a digit and a special character, SR-024) as a bcrypt hash (SR-013). Organization members cannot register: a role in the request is ignored. Rate-limited per client address (SR-015). | `email`, `password` | `userId`, `role`. Errors: `EMAIL_ALREADY_REGISTERED`, `EMAIL_INVALID`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `PASSWORD_TOO_WEAK`, `RATE_LIMITED` | Public |
| POST | `/auth/login` | Sign in and start a session: a JWT valid for 15 minutes, whose subject is the user id, and a refresh token. The session ends after 30 days without use and 90 days after sign-in for a participant, and after 12 hours and 7 days for a member or administrator (SR-026). With `delivery` `COOKIE` (browsers), the tokens go in the `crowded_access` and `crowded_refresh` cookies instead of the body. A wrong password, an unknown email and a deactivated account get the same `INVALID_CREDENTIALS`. Rate-limited per client address and per email (SR-015). A temporary password gives no token: the sign-in is refused with `PASSWORD_CHANGE_REQUIRED`, and the member sets a new password with `POST /auth/password` (FR-088, SR-022). After 7 days it is refused with `TEMPORARY_PASSWORD_EXPIRED`, only once the password matched. | `email`, `password`, `delivery` (`BODY`, the default, or `COOKIE`) | `accessToken` and `refreshToken` (only with `BODY`), `expiresAt`, `mustChangePassword` (always false), `user` {`id`, `role`, `organizationId`}. Errors: `INVALID_CREDENTIALS`, `PASSWORD_CHANGE_REQUIRED`, `TEMPORARY_PASSWORD_EXPIRED`, `RATE_LIMITED`, `ORIGIN_NOT_ALLOWED` | Public |
| POST | `/auth/password` | Replace a member's temporary password with one they choose, at the first sign-in, and sign them in (FR-088, SR-022). The new password follows SR-024 and cannot be the temporary one; the temporary password stops working. Audited (`MEMBER_PASSWORD_CHOSEN`) and rate-limited like the sign-in. A weak new password is refused before the account is looked up, so the answer does not reveal which emails have one. | `email`, `temporaryPassword`, `newPassword`, `delivery` | As `POST /auth/login`. Errors: `INVALID_CREDENTIALS`, `NO_TEMPORARY_PASSWORD`, `TEMPORARY_PASSWORD_EXPIRED`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `PASSWORD_TOO_WEAK`, `PASSWORD_UNCHANGED`, `RATE_LIMITED` | Public |
| POST | `/auth/refresh` | Continue a session: spends the refresh token and answers with a new access token and refresh token, as `POST /auth/login` with the same `delivery`. A spent refresh token used again ends the whole session, except within 10 seconds of its rotation, when another tab refreshed at the same time. Clients call it once when a request gets `401`, then repeat the request. | The `crowded_refresh` cookie, or `refreshToken` | As `POST /auth/login`. Errors: `INVALID_REFRESH_TOKEN`, `ORIGIN_NOT_ALLOWED` | Public (with a refresh token) |
| POST | `/auth/logout` | End the session at once: delete the refresh token, deny the access token in Core and Notifications until it expires, and clear the cookies, which page scripts cannot delete (SR-026). `204` even without a session, and with an expired access token. The app deletes its cached event data and entry codes (SR-023). | The `crowded_refresh` cookie, or `refreshToken` | `204` | Public (with a refresh token) |
| GET | `/me` | Current user. Each app calls it at start-up to know who is signed in and which pages to show; `401` when no one is. | – | `id`, `email`, `name` (a member's), `role`, `organizationId`; `locationConsentGranted` once location consent is stored | Signed-in |
| PUT | `/me/location-consent` | Grant or withdraw location consent (SR-009). | `granted` | `granted` | Participant |
| DELETE | `/me` | Delete the account and its personal data, and publish `UserDeleted` (FR-042). | – | – | Participant |

### Event page (Core)

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/events/{eventId}` | Event page for participants and live dashboard for the organization (FR-014, FR-023, FR-031, FR-035, FR-036, FR-059, FR-072). On-site information carries `provenance`. Members also receive the dashboard fields. | – | `id`, `name`, `type` (`TICKETED` or `PUBLIC`), `status`, `startDateTime`, `doorsOpenAt`, `endDateTime`, `venue`, `organization`, `latestChange`, `activeAlerts[]`, `crowd` {`level`, `headCount`, `capacity`}, `queues[]`, `weather` {`forecast[]`, `warnings[]`, `lastUpdatedAt`}, `estimates[]` {`categoryCode`, `confirmations` {`yes`, `no`, `lastConfirmedAt`}}, `connection`. Members: `ticketEventCode` or `accessCode`, `openIncidents`, `alertsSent`, `connectedUsers`. Implemented so far: `id`, `name`, `description`, `type`, `status`, the three times, `venue` {`name`, `address`, `district`, `latitude`, `longitude`}, `organization` {`id`, `name`}, the source of every detail, `latestChange` (as in the history, null without changes), `crowd` {`level`, `provenance`} (null before the organization publishes one) and `queues[]` {`pointName`, `waitMinutes`, `provenance`} (the queue points with a published waiting time, in the organization's order); each later story adds its own fields. Core caches the page in Redis and drops it when the event changes (TR-006). Errors: `EVENT_NOT_FOUND`, `NOT_CONNECTED` | Participant (connected), Member |
| GET | `/events/{eventId}/changes` | Change history, newest first, with what each change means for the participant (FR-014, FR-075). | – | List of {`id`, `type`, `severity`, `previousValue`, `newValue` (the changed fields by their API name), `ticketStillValid`, `refundInfo`, `message`, `provenance`}, empty when there is no change. Errors: `EVENT_NOT_FOUND`, `NOT_CONNECTED` | Participant (connected), Member |
| GET | `/events/{eventId}/live` | Server-Sent Events stream: one event each time a part of the event page changes (details, changes, crowd, queues, alerts, weather, estimates; incidents for Members), with the same shape as in `GET /events/{eventId}` ([ADR 0007](adr/0007-live-updates-with-server-sent-events.md), NFR-008, NFR-019). Closed when the connection is removed (SR-020). Implemented so far: an `event` with the page when the stream opens and each time a change is published, the event corrected, or its crowd level or queue waiting times published, sent by every Core instance through a Redis channel; the stream ends when the access token expires, and the client refreshes the session and opens it again. Browsers open it with `EventSource`, which sends the cookie. | – | Stream | Participant (connected), Member |

Participant estimates never include the report description, which may contain personal data, or the confidence level, which only the organization sees (FR-058, FR-059).

### Notification center (Notifications)

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/notifications` | Notification center, newest first, only the caller's own (FR-067, SR-003). `eventName` is null when the event no longer exists, and `causeId` when no change, alert or incident is behind it. Opening one that belongs to an event the participant is no longer connected to shows that they left it, not the event page (SR-020). | – | `notifications[]` {`id`, `type`, `title`, `body`, `eventId`, `eventName`, `causeId`, `createdAt`, `readAt`}, `unreadCount` | Signed-in |
| POST | `/notifications/{notificationId}/read` | Mark a notification as read (FR-067); `204`, also when it was read before. Another user's notification is answered as missing. | – | – (Errors: `NOTIFICATION_NOT_FOUND`) | Signed-in (recipient) |
| GET | `/notifications/stream` | SSE stream of new notifications, shown as a live banner, and of the unread count (FR-068). It opens with the comment `:open`, sends each new notification once as an event named `notification`, keeps the connection alive with a comment every 25 s and ends when the token expires. After reconnecting, the client fetches what it missed with `GET /notifications` ([ADR 0007](adr/0007-live-updates-with-server-sent-events.md)). | – | Stream of `notification` events: `notification` (as in `GET /notifications`), `unreadCount` | Signed-in |
| GET | `/notifications/settings` | Overall preferences and per-event overrides. | – | `defaults[]` {`type`, `enabled`}, `overrides[]` {`connectionId`, `type`, `enabled`} | Signed-in |
| PUT | `/notifications/settings` | Turn a notification type on or off, for every event (FR-065) or, with `connectionId`, for one event (FR-041). The client asks for confirmation before turning off official changes or operational alerts (FR-066). | `type`, `enabled`, `connectionId` | `type`, `enabled`, `connectionId` | Signed-in |
| POST | `/notifications/devices` | Register the mobile app's push token. Added with push, after in-app notifications (FR-069, [ADR 0010](adr/0010-external-integrations.md)). | `token`, `platform` | – | Participant |

### Administration (Core)

A platform administrator creates each organization and its members' accounts in a page of the backoffice. Every action is audited (SR-006). Core reads the caller's role from the database: anyone but an active administrator gets `403 ADMINISTRATOR_ONLY`.

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/admin/organizations` | Organizations by name, with their number of events and their members. | – | List of {`id`, `name`, `createdAt`, `eventCount`, `members[]` {`userId`, `name`, `email`, `active`, `mustChangePassword`, `temporaryPasswordExpiresAt`, `lastSignedInAt`}} | Admin |
| POST | `/admin/organizations` | Create an organization (FR-087). Its name is unique, whatever its case. | `name` | `organizationId`. Errors: `ORGANIZATION_NAME_TAKEN` | Admin |
| POST | `/admin/organizations/{organizationId}/members` | Create a member account with a temporary password, shown once and valid for 7 days (FR-087, SR-022). Only its bcrypt hash is stored. | `name`, `email` | `userId`, `temporaryPassword`, `expiresAt`. Errors: `ORGANIZATION_NOT_FOUND`, `EMAIL_ALREADY_REGISTERED` | Admin |
| PATCH | `/admin/members/{userId}` | Deactivate or reactivate a member, or issue a new temporary password (FR-087). Deactivating and a new password revoke the member's tokens issued before: Core and Notifications refuse them (ADR 0004). | `active` (optional), `resetPassword` (optional) | `userId`, `active`, `temporaryPassword`, `expiresAt` (the last two only with `resetPassword`). Errors: `MEMBER_NOT_FOUND` | Admin |

## Principal User Journeys

Each journey lists the endpoints it uses. Endpoints from [Shared Endpoints](#shared-endpoints) or from an earlier journey are referenced by path.

### 1. Set up the event

A platform administrator has created the organization and its members (see [Administration](#administration-core)). A member signs in, sets a new password and registers the event. The organization of a new event is always the member's own, read from the database, and only an active member may create or correct one (SR-001, SR-002).

Also uses: `POST /auth/login`, `POST /auth/password`.

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/organizations/me/events` | The organization's events, the latest start first: the backoffice home (O2) and the values the edit form (O3) starts from. | – | List of events as below | Member |
| POST | `/events` | Create and publish an event (FR-015, FR-024) with its entrances and queue points, named freely by the organization (FR-019), and which report categories ask for confirmations and how (FR-057, FR-060, FR-064). A `TICKETED` event has the event code printed on its tickets; a `PUBLIC` event has the access code that the organization gives out. The backoffice draws the QR code from the access code, so the same code always gives the same QR code. A code that another event uses is refused until that event ends or is cancelled (FR-086); codes are compared without case and kept in upper case. The start must be in the future, the district one of IPMA's, and the location comes from looking the address up on the map ([ADR 0018](adr/0018-openstreetmap-for-maps-and-geocoding.md)). `venueRadiusMeters` is in meters, from 100 to 50 000, so a public event spread over a city can cover it. Without `confirmations`, the categories enabled by default ask for them, within 500 m, 8 answers for high confidence and 3 prompts per participant and hour. | `name`, `description`, `type`, `ticketEventCode` or `accessCode`, `startDateTime`, `doorsOpenAt`, `endDateTime`, `venue` {`name`, `address`, `district`, `latitude`, `longitude`}, `capacity`, `queuePoints[]` (names), `confirmations` {`categories[]` (codes), `venueRadiusMeters`, `highConfidenceAnswers`, `maxPromptsPerHour`} | `201` with the event: `id`, `name`, `description`, `type`, `status`, `ticketEventCode` or `accessCode`, `startDateTime`, `doorsOpenAt`, `endDateTime`, `venue`, `capacity`, `connectedUsers`, `queuePoints[]`, `confirmations`, `imageUpdatedAt` (null when the event has no image), `crowd` (as in `PUT /events/{eventId}/crowd`, null before the first), `queues[]` (as in `PUT /events/{eventId}/queues`). Errors: `NOT_ORGANIZATION_MEMBER`, `EVENT_CODE_TAKEN`, and invalid values such as `START_IN_PAST`, `END_REQUIRED`, `LOCATION_REQUIRED`, `DISTRICT_UNKNOWN`, `CODE_INVALID` or `PERSONAL_CATEGORY_NOT_CONFIRMED` | Member |
| PATCH | `/events/{eventId}` | Correct details that are not an official change, which participants are not notified of: name, description, capacity, the district, the type (`TICKETED` or `PUBLIC`, which keeps the code unless a new one is sent), the code (in the field of the event's type, the new one when the type changes), the queue points (the whole list; the ones left out are removed with their waiting time) and the confirmations (FR-015, FR-060, FR-086). Changing the code keeps the participants already connected. Changing the type keeps them connected too. Date, time, doors opening time, venue and cancellation go through `POST /events/{eventId}/changes`. | Changed fields | The event, as in `POST /events`. Errors: `EVENT_NOT_FOUND`, `NOT_EVENT_ORGANIZATION`, `EVENT_CODE_TAKEN`, `CODE_TYPE_MISMATCH` (the code in the other type's field), `ONE_CODE_ONLY`, `DISTRICT_UNKNOWN` | Member |
| DELETE | `/events/{eventId}` | Delete an event nobody is connected to, such as one created by mistake, with everything that belongs to it; no one is notified, as there is no one to notify. Once a participant is connected, the organization cancels the event instead (`POST /events/{eventId}/changes`), which notifies them. The audit entry keeps the event's id and name. | – | 204. Errors: `EVENT_NOT_FOUND`, `NOT_EVENT_ORGANIZATION`, `EVENT_HAS_PARTICIPANTS` | Member |
| PUT | `/events/{eventId}/image` | Add or replace the event's image (FR-089): a JPEG, PNG or WebP of up to 5 MB, its type read from its content, whatever its name or `Content-Type` say. Core removes its metadata, the location included, before storing it in PostgreSQL ([ADR 0019](adr/0019-images-in-postgresql.md), SR-025). Audited. | The file as the body | `204`. Errors: `EVENT_NOT_FOUND`, `NOT_EVENT_ORGANIZATION`, `IMAGE_REQUIRED`, `IMAGE_TOO_LARGE`, `IMAGE_TYPE_NOT_ACCEPTED`, `IMAGE_INVALID` | Member |
| DELETE | `/events/{eventId}/image` | Remove the event's image (FR-089); nothing happens when it has none. Audited. | – | `204`. Errors: `EVENT_NOT_FOUND`, `NOT_EVENT_ORGANIZATION` | Member |
| GET | `/events/{eventId}/image` | The event's image, for whoever may see the event: a connected participant or an active member of its organization (SR-001). Cached privately and checked again with its `ETag`, which changes with `imageUpdatedAt`. Browsers load it with an image tag, which sends the cookie; the phone app sends the `Authorization` header. | – | The image, with its media type; `304` when unchanged. Errors: `EVENT_NOT_FOUND`, `NOT_CONNECTED`, `EVENT_IMAGE_NOT_FOUND` | Participant, Member |

### 2. Connect to an event

The participant imports a ticket or enters a public event's access code. There is no list or search of events.

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/tickets` | Import a ticket from a PDF or an image (FR-001, FR-082). Core decodes the QR code without AI (FR-070) and refuses a file without one. It removes personal data before the AI extraction (SR-004). When the extracted event code matches a registered `TICKETED` event, it connects the participant at once (FR-003). Otherwise it keeps the import pending in Redis, not in the database, and asks for the code (FR-004). The same QR code cannot be imported by another account for that event (FR-084). The file may be up to 10 MB, and a participant may import a limited number of tickets per hour. | `file` | `status` (`CONNECTED` or `NEEDS_EVENT_CODE`), `importId` (pending), `ticketId`, `connectionId`, `event` {`id`, `name`, `startDateTime`, `doorsOpenAt`, `venueName`} (from the registered event), `ticket` {`gate`, `sector`, `seat`}. Errors: `QR_CODE_NOT_FOUND` (not a PDF, damaged or without a QR code), `TICKET_ALREADY_IMPORTED`, `RATE_LIMITED`, and `413` for a file over 10 MB | Participant |
| POST | `/ticket-imports/{importId}/event-code` | Complete a pending import with the event code typed by the participant (FR-004). Core decides the event from the typed code, never from an identifier sent by the client (SR-021), and only for the participant's own pending import. When no registered event has that code, the import and its entry code are deleted (FR-083); they are also deleted when another account has the ticket. A pending import expires after 30 minutes. | `eventCode` | Same as `POST /tickets` with `status` `CONNECTED`. Errors: `EVENT_NOT_ON_CROWDED`, `TICKET_ALREADY_IMPORTED`, `IMPORT_NOT_FOUND` (no such pending import for the participant: it never existed, expired or is another participant's) | Participant (owner) |
| POST | `/connections` | Connect to a public event with its access code, typed or read from its QR code (FR-085). Core finds the public event that has not ended or been cancelled with that code. A participant already connected gets their connection back and nothing is added; the connection keeps the event, not the code, so it stays when the organization changes the code. | `accessCode` | `201` for a new connection, `200` for an existing one: `connectionId`, `event` {`id`, `name`, `startDateTime`, `doorsOpenAt`, `venueName`}. Errors: `ACCESS_CODE_NOT_FOUND`, the same for every code that leads to no such event, `PARTICIPANT_ONLY` | Participant |
| GET | `/me/connections` | The participant's events, also cached on the device for offline use (FR-005, FR-037). | – | `connections[]` {`connectionId`, `type` (`TICKET` or `ACCESS_CODE`), `event` {`id`, `name`, `status`, `startDateTime`, `doorsOpenAt`, `venueName`, `latestChange`, `activeAlerts[]`}, `ticket` {`gate`, `sector`, `seat`, `hasEntryCode`}}, `syncedAt` | Participant |
| GET | `/tickets/{ticketId}/entry-code` | The ticket's QR code to show full screen and keep offline (FR-071, SR-018). | – | `format` (`QR`), `value` | Participant (owner) |
| DELETE | `/me/connections/{connectionId}` | Remove a ticket, with its entry code, or leave a public event (FR-008, SR-018). Ends access to the event and closes its live stream (SR-020). | – | – | Participant (owner) |

### 3. Get notified of changes

A change reaches Crowded from the organization, published in the backoffice, or as a weather warning from IPMA, detected by Jobs. Core records it, sets its severity and asks Notifications to notify the connected participants (see [Messages](#messages)). The participant opens the notification and sees what changes for them.

Also uses: `GET /notifications`, `GET /notifications/stream`, `POST /notifications/{notificationId}/read`, `GET /events/{eventId}` and `GET /events/{eventId}/changes`.

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/events/{eventId}/changes` | Publish a change: postpone, move, cancel, or change the time or doors opening time (FR-016, FR-072, FR-075). Core sets the severity (FR-074), stores the `EventChange`, updates the event, audits it (SR-006) and publishes `NotificationRequested` in one transaction ([Asynchronous Flows](asynchronous-flows.md)). A postponement or start-time change moves the doors opening and end times with the start unless they are sent. A venue change sends the new venue's name, address and location (`latitude` and `longitude`, looked up on the map as in journey 1). A change the event already has records nothing and answers `200` with the change recorded the first time (NFR-003); a new one answers `201`. | `type` (`POSTPONEMENT`, `TIME_CHANGE`, `VENUE_CHANGE` or `CANCELLATION`), `newValue` (changed fields: `startDateTime`, `doorsOpenAt`, `endDateTime`, or `venueName`, `address`, `latitude`, `longitude`), `ticketStillValid` and `refundInfo` (postponement or cancellation only), `message` (to the participants) | `changeId`, `severity`. Errors: `EVENT_NOT_FOUND`, `NOT_EVENT_ORGANIZATION`, `EVENT_CLOSED`, `NOTHING_CHANGES`, and invalid values such as `START_IN_PAST`, `VENUE_REQUIRED` or `LOCATION_REQUIRED` | Member |

### 4. Check conditions and decide

On the event day, the participant checks the event page, reports what they see and confirms other participants' reports.

Also uses: `GET /events/{eventId}` and `GET /events/{eventId}/live`. Maps and transport are called by the app directly from the providers; the weather comes from IPMA through Jobs and Core ([Technical Design](technical-design.md#logical-view-level-2)).

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| GET | `/events/{eventId}/report-categories` | Report categories and whether they trigger confirmation prompts at this event (FR-034, FR-060). | – | List of {`code`, `name`, `personal`, `promptsEnabled`} | Participant (connected), Member |
| POST | `/events/{eventId}/reports` | Report something at the event (FR-034). Non-personal categories need a location within the venue radius, checked once and not stored (FR-064, SR-017). Core publishes `ReportSubmitted` and, when prompts are enabled, asks a sample of connected participants to confirm (FR-057). | `categoryCode`, `description`, `reportedCrowdLevel`, `reportedQueuePoint`, `reportedWaitMinutes`, `location` {`latitude`, `longitude`} | `reportId`, `locationVerified` | Participant (connected) |
| PUT | `/reports/{reportId}/confirmation` | Answer a confirmation prompt with "Yes" or "No" (FR-057, FR-058, FR-064). One answer per participant, never by the reporter, never revealed (SR-016). | `answer` (`YES` or `NO`), `location` {`latitude`, `longitude`} | – | Participant (asked, connected) |
| GET | `/me/contributions` | Private history of reports and confirmations, with their outcome and recognitions (FR-038, FR-054). | – | List of {`type` (`REPORT` or `CONFIRMATION`), `id`, `eventId`, `categoryCode`, `at`, `outcome`, `recognition` {`impact`, `reachedUsers`}} | Participant |

### 5. Manage the event on the day

During the event, the organization keeps on-site information up to date, sends and resolves alerts, handles incidents and publishes surveys. The live dashboard is `GET /events/{eventId}` with `GET /events/{eventId}/live`.

Also uses: `POST /events/{eventId}/changes` (journey 3).

**On-site information and settings**

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| PUT | `/events/{eventId}/crowd` | Publish the crowd level (FR-018), with the organization as its source and the time as its update time. Connected participants get the new page on the live stream at once (NFR-008); the same level again tells them it is still current. A cancelled or ended event takes none. Implemented so far: `level`, without `headCount`. | `level` (`LOW`, `MODERATE`, `HIGH` or `FULL`), `headCount` | `crowd` {`level`, `headCount`, `provenance`}. Errors: `EVENT_NOT_FOUND`, `NOT_EVENT_ORGANIZATION`, `EVENT_CLOSED`, `CROWD_LEVEL_REQUIRED` | Member |
| PUT | `/events/{eventId}/queues` | Publish queue waiting times (FR-019) at the event's queue points, set with the event (journey 1), with the organization as their source and the time as their update time. The points left out keep their time; a point removed from the event loses it. Connected participants get the new page on the live stream at once (NFR-008); the same time again tells them it is still current. A cancelled or ended event takes none. | `queues[]` {`pointName` (one of the event's queue points), `waitMinutes` (whole minutes, 0 to 600)} | `queues[]` {`pointName`, `waitMinutes`, `provenance`}: every point with a time, in the organization's order. Errors: `EVENT_NOT_FOUND`, `NOT_EVENT_ORGANIZATION`, `EVENT_CLOSED`, `QUEUE_TIMES_REQUIRED`, `QUEUE_POINT_UNKNOWN`, `QUEUE_POINT_REPEATED`, `WAIT_MINUTES_INVALID` | Member |

**Alerts**

| Method | Path | Purpose | Main request | Main response | Role |
|---|---|---|---|---|---|
| POST | `/events/{eventId}/alerts` | Create an alert as a draft. With `organizerInput`, Core asks the AI for a draft based on the event's current state (FR-021); when the AI is unavailable, the draft is empty and the organization writes the message (NFR-006). `incidentId` links an alert triggered by an incident. | `type`, `organizerInput`, `incidentId` | `alertId`, `status` (`DRAFT`), `draft` {`draftText`, `factsUsed`, `aiGenerated`} | Member |
| POST | `/alerts/{alertId}/send` | Send the reviewed alert to connected participants (FR-020, FR-021, SR-006). | `type`, `severity`, `message` | `status` (`SENT`), `sentAt`, `recipientsCount` | Member |
| POST | `/alerts/{alertId}/resolve` | Mark the alert as resolved and tell connected participants it has ended (FR-073). | – | `resolvedAt` | Member |

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
| POST | `/events/{eventId}/surveys` | Publish a survey on the event page; urgent surveys are notified (FR-051, FR-052). | `type`, `text`, `options[]`, `urgent`, `closesAt` | `surveyId`, `publishedAt` | Member |
| GET | `/events/{eventId}/surveys` | Surveys of the event, with the participant's own answer for participants and the totals or ideas, without names, for Members. | – | List of {`id`, `type`, `text`, `options[]`, `urgent`, `closesAt`, `myAnswer` (Participant), `results` (Member)} | Participant (connected), Member |
| PUT | `/surveys/{surveyId}/response` | Answer a survey, or change the answer (FR-051). | `answer` | `respondedAt` | Participant (connected) |

## Between Components

### Router

The Router (NGINX) is the single entry point ([Technical Design](technical-design.md#logical-view-level-2)), over HTTPS with a Let's Encrypt certificate ([ADR 0021](adr/0021-browser-sessions-in-cookies-with-refresh-tokens.md)). It sends the requests for the endpoints marked Core to Core, and those marked Notifications to Notifications, keeping the SSE streams open without response buffering ([ADR 0007](adr/0007-live-updates-with-server-sent-events.md)). The services do not call each other: everything between them goes through the Message Broker, authenticated (SR-012).

### Messages

Messages are JSON, published to the topic exchange `crowded.events` with the message name as routing key in dotted lower case (`NotificationRequested` → `notification.requested`) and `messageId` as the AMQP message id ([Asynchronous Flows](asynchronous-flows.md#2-messages)). Messages carry ids only; consumers read the current data from the database ([ADR 0003](adr/0003-rabbitmq-for-asynchronous-messaging.md)). Failed processing is retried with backoff, and repeatedly failing messages are moved to a dead-letter queue (NFR-004). The only exception to ids only is `ReportsGrouped`, which carries the grouping result because Jobs does not store it.

| Message | Publisher | Consumer | Payload | Effect |
|---|---|---|---|---|
| `WeatherUpdated` | Jobs | Core | `district`, `snapshotId` | Core stores the forecast and warnings for the events in that district (FR-035) and, for a new or raised orange or red warning during an event, notifies its connected participants (FR-036). |
| `ReportSubmitted` | Core | Jobs | `eventId`, `reportId` | Jobs groups the report with similar ones, by category and time window when the AI is unavailable (FR-053). |
| `ReportsGrouped` | Jobs | Core | `eventId`, `groupingMethod`, `groups[]` {`reportIds[]`, `summary`} | Core creates or updates incidents; AI summaries are labelled until the organization acts on them (FR-053, NFR-007). |
| `NotificationRequested` | Core | Notifications | `messageId`, `type`, `eventId`, `causeId`, `userIds[]` | Notifications notifies the participants connected to the event or, when `userIds` is not empty, only those users. |
| `UserDeleted` | Core | Notifications | `userId` | Notifications deletes the user's notifications, settings and device tokens (FR-042). |

Core publishes `NotificationRequested` with the `type` and `causeId` that the `Notification` will have:

- `OFFICIAL_CHANGE`: a recorded `EventChange` (FR-013).
- `OPERATIONAL_ALERT`: an `Alert` sent or resolved (FR-020, FR-073).
- `ORGANIZATION_SURVEY`: an urgent `EventSurvey` (FR-052).
- `WEATHER_WARNING`: an orange or red `WeatherWarning` for the event's district (FR-036).
- `CONFIRMATION_REQUEST`: a `Report`, with the sampled participants in `userIds` (FR-057).
- `INCIDENT_STATUS`: an `Incident`, with its reporters in `userIds` (FR-029).
- `RECOGNITION`: a `Recognition`, with the reporter in `userIds` (FR-054).

Notifications skips recipients who turned the type off (FR-041, FR-065), stores one `Notification` per recipient, and sends it on `GET /notifications/stream`, and later as a push. The `deduplicationKey` is the recipient plus the trigger (`messageId`) and is unique, so a redelivered message never notifies twice (NFR-003).

### External integrations

AI, IPMA, maps, transport and push (Firebase Cloud Messaging) are called through adapters with a fake for tests ([ADR 0010](adr/0010-external-integrations.md), [ADR 0015](adr/0015-organization-as-the-only-event-source.md), TR-008). The IPMA endpoints are public open data and need no key.

| Endpoint | Caller | Purpose |
|---|---|---|
| IPMA `GET /open-data/forecast/warnings/warnings_www.json` | Jobs | Weather warnings by warning area and level (yellow, orange, red), polled every 5 minutes (NFR-021). |
| IPMA `GET /open-data/forecast/meteorology/cities/daily/{globalIdLocal}.json` | Jobs | Five-day forecast for the district capital of each upcoming event's district (FR-035). |
| GTFS feed of the transport operator | Participant app | Upcoming departures to the venue and disruptions when the feed reports them (FR-055). The operator is chosen in its story. |

## Vertical Slice

Sprint 2 implements journey 3 end to end on top of the walking skeleton: **a change published by the organization reaches a participant connected with their ticket, in the notification center and as a live banner**. It crosses every backend component (Router, Core, Jobs, Notifications, Message Broker, Database and Cache) and both clients, and is the same path used in the push-failure demonstration ([Degraded Operation](technical-design.md#degraded-operation)).

| Step | Interface |
|---|---|
| 1. The administrator creates the organization and a member, who sets a new password | `POST /admin/organizations`, `POST /admin/organizations/{organizationId}/members`, `POST /auth/login`, `POST /auth/password` |
| 2. The member registers a ticketed event | `POST /events` |
| 3. The participant signs up, signs in and imports a ticket | `POST /auth/register`, `POST /auth/login`, `POST /tickets` (and `POST /ticket-imports/{importId}/event-code` when the AI is unavailable) |
| 4. The member publishes a change | `POST /events/{eventId}/changes` |
| 5. Notifications notifies the participant | `NotificationRequested` (`OFFICIAL_CHANGE`) |
| 6. The open app shows a live banner and updates the badge | `GET /notifications/stream` |
| 7. The participant opens the notification center and the change | `GET /notifications`, `POST /notifications/{notificationId}/read`, `GET /events/{eventId}/changes` |
| 8. Each service reports its health | Health check of each service (NFR-011) |
