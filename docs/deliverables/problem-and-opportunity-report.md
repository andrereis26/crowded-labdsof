# Problem and Opportunity Report

## 1. Target Community

### 1.1 Event Attendees

People who attend cultural and public events in a city, including:

- Concerts
- Festivals
- Theatre and shows
- Popular festivities (e.g., Santos Populares)
- Municipal and community events
- Sports events

For paid events, attendees usually buy their tickets on external ticketing platforms (e.g., Ticketline, BOL). For free public events, there is often no ticket at all. In both cases, once they have decided to attend, they rely on multiple channels to learn about changes and on-site conditions.

### 1.2 Event Organizers

Promoters, venues, cultural associations and public entities (e.g., municipalities, parish councils) that need better tools to:

- Plan capacity based on expected attendance
- Communicate event changes to their audience
- Publish on-site conditions such as crowd levels and queues
- Send operational alerts during the event
- Receive and respond to on-site incidents

---

## 2. Problem Statement

Event participants frequently face disruptions — cancellations, schedule or venue changes, overcrowding, long queues and on-site incidents — and learn about them too late.

Before the event, changes are communicated across multiple channels:

- Ticketing platforms
- Organizer websites
- Social media platforms
- Email

During the event, participants have little or no visibility into:

- Crowd levels
- Queue lengths
- Closed entrances or access changes
- On-site incidents

Event organizers also face significant uncertainty when planning capacity, and lack a direct and fast channel to reach the people who are on their way to, or already at, the event.

> **As a result, both participants and organizers react to disruptions based on incomplete or outdated information, leading to wasted trips, overcrowding, avoidable queues and slow incident response.**

---

## 3. Problem Evidence

### 3.1 Fragmented Communication of Changes

When an event is changed, postponed, relocated or cancelled, communication is frequently dispersed across ticketing platforms, social media, organizer websites and email.

Users must actively monitor several channels to stay informed, and may miss important updates or receive them too late. Our user research found a documented case of a user who did not receive alerts despite having notifications enabled (see User Research).

### 3.2 Ticketing Platforms Stop at the Purchase

Ticketing and discovery platforms focus on selling tickets and promoting events, but offer limited support once the ticket has been bought.

Our user research also found a documented case of a user who had difficulty accessing their tickets while travelling to a concert (see User Research). For free public events, there is no ticketing relationship at all, so there is no channel to reach attendees.

### 3.3 Lack of Real-Time On-Site Awareness

Existing platforms offer limited visibility into real-time conditions such as:

- Venue congestion
- Queue lengths
- Closed entrances
- Current attendance levels

Users often discover these conditions only after arriving at the venue.

### 3.4 Limited Capacity Planning

Event organizers frequently have difficulty estimating how many people will actually attend.

This uncertainty may result in:

- Undersized staff or entrances
- Oversized venues
- Excess operational costs
- Overcrowding and poor attendee experience

### 3.5 No Direct Organizer-to-Audience Channel During Events

When something goes wrong on site (e.g., an entrance closes, capacity is reached, a weather warning is issued), organizers depend on social media posts or on-site staff to redirect the audience.

Participants who are on their way, or in another area of the venue, may not receive this information in time.

---

## 4. Stakeholder Map

### 4.1 Event Attendees

#### Interests

- Knowing about event changes before leaving home
- Avoiding overcrowding and long queues
- Having the information about their events in one place
- Reporting problems on site

#### Benefits

- Event change notifications
- Ticket wallet and followed public events
- Crowd and queue information with its source and last-update time
- Incident reporting routed to the organization

### 4.2 Event Organizers

#### Interests

- Planning capacity
- Communicating changes and alerts quickly
- Managing crowds and queues
- Responding to incidents

#### Benefits

- Attendance-intention polls
- Backoffice and API to publish on-site conditions
- Direct alerts to every participant connected to the event
- Incident inbox
- AI-drafted alerts for review

### 4.3 Public Entities and Cultural Institutions

#### Interests

- Public safety at public events
- Improving visitor satisfaction
- Verified institutional communication

#### Benefits

- A verified channel to communicate with attendees
- Visibility of on-site conditions and incidents
- Better coordination during large public events

### 4.4 Ticketing Platforms (External)

#### Interests

- Reducing support requests caused by event changes
- Keeping their customers informed

#### Benefits

- An additional channel that delivers their official event changes to ticket holders

---

## 5. Assumptions

| ID | Assumption | Description |
|---|---|---|
| **A1** | **Users Want Event Changes in a Single Place** | We assume users value receiving changes for all the events they attend in one place, regardless of where the ticket was bought. |
| **A2** | **Organizations Are Willing to Publish On-Site Conditions** | We assume organizations will publish crowd levels, queues and alerts if the backoffice is simple, or if the data can be sent automatically through an API. |
| **A3** | **Organizers Need Attendance-Intention Data** | We assume organizers are interested in knowing intended attendance before finalizing staff, entrances and other capacity decisions. |
| **A4** | **Users Are Willing to Import Tickets** | We assume users are willing to import tickets bought elsewhere if the process is fast and gives them access to change notifications and on-site information. |
| **A5** | **Event Disruptions Are Common Enough to Justify the Product** | We assume changes, overcrowding and on-site incidents occur frequently enough to justify a dedicated communication service. |
| **A6** | **Official Event Data Can Be Obtained** | We assume official event changes can be obtained from ticketing platforms or public agendas. In the MVP, this source is simulated. |
| **A7** | **Participants Will Report On-Site Incidents** | We assume participants will report incidents (e.g., lost and found, accessibility barriers) if reporting is fast and the organization responds. |

---

## 6. Risks

### R1 — Organizer Adoption

Crowd levels, queues and alerts depend on organizations using the backoffice or API. Without them, only official changes and weather information are available.

**Mitigation**

>
    - Keep the backoffice extremely simple
    - Offer an API so data can be sent automatically (e.g., entrance counters)
    - Official changes and weather alerts work without the organization

---

### R2 — Stale or Inaccurate Organizer Data

Organizations may not keep on-site information up to date during the event.

**Mitigation**

>
    - Always show the source and last-update time
    - Hide or flag information that has not been updated recently

---

### R3 — Availability of Official Event Data

Most ticketing platforms do not offer public APIs for event changes.

**Mitigation**

>
    - Use a realistic ticketing simulator in the MVP
    - Treat real integrations as partnerships in the product roadmap
    - Show the last successful synchronization when the source is unavailable

---

### R4 — False or Misleading Alerts

False cancellations or safety alerts could cause confusion or panic.

**Mitigation**

>
    - Only the official source and verified organizations can publish official information
    - Keep an audit log of changes and alerts
    - AI-generated drafts always require organizer approval

---

### R5 — Privacy and Data Security Concerns

Tickets contain personal data and access codes, and users may be hesitant to share them.

**Mitigation**

>
    - Store only the minimum ticket data needed
    - Never store access codes in plain text
    - Do not make participant identity public in incident reports
    - Use approximate location, only with consent

---

### R6 — Low Participant Adoption

Without participants connected to an event, organizer alerts have little reach.

**Mitigation**

>
    - Allow public events to be followed with minimal friction
    - Let organizers promote the event through QR codes at the venue and on tickets
