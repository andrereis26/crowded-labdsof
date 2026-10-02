# Requirements Specification

This specification translates the Crowded product vision, research, opportunity analysis, assignment constraints, and responsible AI assessment into **functional**, **non-functional**, **security** and **technical** requirements. Priorities reflect the explicit MVP boundary and mandatory project deliverables.

**Priority levels are defined as follows**:
- **8-10: High - Must have for MVP**
- **5-7: Medium - Could/Should have for MVP**
- **1-4: Low - Out of scope for MVP**

## 1. Functional Requirements

| ID | Statement | Priority |
|---|---|---|
| FR-001 | The system shall present users with a feed of available events. | 10 |
| FR-002 | The system shall allow a user to search for events within a location and a user-selected radius. | 10 |
| FR-003 | The system shall allow a user to filter events by category (e.g. classic music, rock, sports, etc.). | 10 |
| FR-004 | The system shall allow a user to open an event and view its available event information including its location in a map. | 8 |
| FR-031 | The system shall let the user visualize a map with events near their configured location. | 7 |
| FR-021 | The system shall display the weather information for the day of the event so users can plan accordingly. | 6 |
| FR-005 | The system shall allow an organizer to create and publish an event with its available details, including its location and schedule. | 8 |
| FR-006 | The system shall allow an authorized organizer to update event details, including changes to the event date, time, venue, or cancellation status. | 6 |
| FR-008 | The system shall notify users interested in an event when its organizer publishes a event change. | 9 |
| FR-009 | The system shall allow a user to submit a report about conditions at an event, including crowd level. | 9 |
| FR-010 | The system shall display available crowd reports for an event with their submission times so users can assess their recency. | 8 |
| FR-012 | The system shall allow an organizer to create an interest poll associated with an event or proposed event. | 8 |
| FR-013 | The system shall allow a user to submit a response to an available event interest poll. | 8 |
| FR-014 | The system shall present poll responses to the organizer as indications of potential demand and shall not represent them as guaranteed attendance. | 8 |
| FR-015 | The system shall allow users to follow an event. | 8 |
| FR-016 | The system shall offer an AI based event recommendations using the user's selected location, radius, categories, and interests. | 5 |
| FR-018 | The system shall allow a user to provide approximate location or a manually selected location for location-based event discovery. | 8 |
| FR-019 | The system shall allow a user to configure whether they receive event notifications. | 2 |
| FR-020 | The system shall allow a user to view their event history and attendance records. | 4 |
| FR-028 | The system shall allow users to review and change the interests and location settings used for event discovery. | 7 |
| FR-029 | The system shall allow a user to keep their profile private and to control whether their event attendance is visible to other users. | 4 |
| FR-030 | The system shall support user registration and authentication through federated identity providers (Google and Facebook) as well as through email and password credentials. | 5 |
| FR-030 | The system shall provide AI-based alerts for certain weather conditions that may affect certain events. (e.g. severe thunderstorms, heavy rain, hot weather, etc) | 4 |
| FR-031 | The system shall be self sustainable by having a web scraper that regularly adds new events to the system. | 5 |
| FR-032 | The system shall allow authenticated users to permanently delete their account and all associated personal data. | 2 |
| FR-033 | The system shall provide controls for users to disable notifications and to control profile and attendance visibility. | 2 |

---

## 2. Non-Functional Requirements

| ID | Statement | Priority |
|---|---|---|
| NFR-001 | Notifications of event changes shall be delivered in a timely manner based on when the event changes occur (e.g., within 5 minutes of the change if the even is on the same day). | 6 |
| NFR-001 | Notifications of extreme weather conditions shall be delivered in a timely manner based on when the event changes occur (e.g., within 5 minutes of the change if the even is on the same day). | 4 |
| NFR-006 | The system shall provide structured application logs, relevant operational metrics, error reporting, and correlation or trace identifiers for its deployed components. | 8 |
| NFR-012 | The system shall fail safely when external data is unavailable or stale and shall communicate the resulting limitation to affected users. | 5 |
| NFR-014 | The system shall support unit, integration, and relevant end-to-end testing of the principal workflows. | 8 |
| NFR-015 | The system shall be built and delivered through an automated CI/CD pipeline. | 8 |
| NFR-020 | The system shall support evaluation of AI-assisted behavior to ensure it is functioning as intended and not causing any unintended consequences. | 5 |
| NFR-025 | The system shall provide an operational health check for each deployed backend component. | 3 |
| NFR-026 | The system shall provide a response to user requests within 5 seconds. | 9 |

---

## 3. Security Requirements

| ID | Statement | Priority |
|---|---|---|
| SR-001 | The system shall require an authenticated user identity before allowing access to user-specific or organizer-only actions. | 5 |
| SR-003 | The system shall scope user-specific profile, notification, location, and preference data to the user or authorized role permitted to access it. | 6 |
| SR-004 | The system shall request user consent before using location or preference data for personalized event discovery. | 6 |
| SR-005 | The system shall use approximate location and only the personal data needed to provide the selected event-discovery workflow. | 6 |
| SR-006 | The system shall enforce visibility settings and restrict access to profile and attendance information accordingly. | 5 |
| SR-007 | The system shall not send identifiable user data to an external AI provider as part of the AI-assisted workflow. | 5 |
| SR-014 | The system shall provide a way for a user to submit a crowd report without making their profile or identity public alongside the report. | 5 |
| SR-015 | The system shall use OAuth 2.0 / OpenID Connect protocols for authentication through Google and Facebook. | 5 |
| SR-016 | The system shall store user passwords using a strong one-way hashing algorithm with salt | 5 |
| SR-017 | The system shall have rate limiting in place in account creation and login processes to prevent abuse of the system. | 5 |
| SR-018 | The system shall have rate limiting in non authenticated requests. | 5 |


---

## 4. Technical Requirements
| ID | Statement | Priority |
|---|---|---|
| TR-001 | The system shall cache user-specific data (e.g., preferences, and profile information) for a period of 24 hours in order to reduce the load on the backend and improve the user experience. Cache should be invalidated when there's a change in the user's data. | 4 |
| TR-002 | The system shall cache AI-generated content (recommendations and alerts) for a period of 24 hours in order to reduce the response times and costs of AI services. | 1 |