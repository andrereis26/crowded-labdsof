# Product Vision

## Vision Statement
Crowded keeps people informed when something changes or goes wrong at the events
they are attending, and gives organizations the tools to communicate with and
manage their audience before and during an event — bringing official changes,
on-site conditions, and organizer alerts into one place, so that participants and
organizations can make decisions based on current and reliable information.

## Personas

### Persona 1 — Inês, the Participant
- **Age/context:** 24 years old, university student in Lisbon
- **Goal:** keep track of the events she is going to — concerts bought on Ticketline,
  free public events like Santos Populares — without having to monitor ticketing
  sites, Instagram pages, and emails for updates
- **Frustration:** she has already arrived at a cancelled event without knowing,
  and has faced overcrowded venues and long queues she could have avoided
- **How Crowded helps:** she imports her tickets or follows public events, receives
  notifications when an event changes, and checks crowd levels, queues, and
  organizer alerts before leaving home

### Persona 2 — Miguel, the Organizer
- **Age/context:** 35 years old, event manager at a local cultural association
- **Goal:** plan capacity based on how many people actually intend to attend, and
  communicate quickly with everyone who is on their way or already at the venue
- **Frustration:** uses multiple channels (email, social media) to communicate
  updates, never knows whether everyone has been informed in time, and has no
  direct way to redirect the audience when something goes wrong on site
- **How Crowded helps:** attendance-intention polls for capacity planning, a
  backoffice (or API) to publish crowd levels, queues, and incidents, and direct
  alerts to every participant connected to the event

## Value Proposition
Unlike ticketing platforms and event-discovery apps, which stop being useful once
the ticket is bought, Crowded follows the event until it ends. It combines three
sources of information in one product — official changes synchronized from the
ticketing source, on-site conditions and alerts published by the organization, and
incidents reported by participants — and always shows where each piece of
information comes from and when it was last updated, reducing the uncertainty that
surrounds event disruptions today.

## Principal User Journeys

1. **Connect to an event**
   The user imports a ticket bought elsewhere (AI extracts the event details from
   the PDF or confirmation email, with manual confirmation as a fallback), follows
   a public event, or scans a QR code at the venue. The user can also browse
   events nearby, but discovery is an entry point, not the core of the product.

2. **Get notified of changes**
   When the event is cancelled, postponed, or moved in the official source, the
   user receives a notification labelled with its origin — ideally before leaving
   home.

3. **Check conditions and decide**
   On the day of the event, the user checks crowd levels, queues, organizer
   alerts, and weather, each with its source and last-update time, and reports
   incidents (lost and found, accessibility barriers, on-site problems) that are
   routed to the organization.

4. **Plan and manage an event** (organizer)
   Before the event, the organizer launches an attendance-intention poll and
   adjusts staff and entrances based on the responses (an indication, not
   guaranteed attendance). During the event, the organizer updates crowd levels
   and queues through the backoffice or API, resolves incidents, and sends alerts
   (e.g., "Gate B closed, use Gate C") from an AI-generated draft that they review
   before sending.

## Expected Community Benefit
Crowded reduces the impact of common event disruptions — cancellations, schedule
or venue changes, overcrowding, long queues, and on-site incidents — by
centralizing reliable communication and giving visibility into on-the-ground
conditions. This benefits participants (fewer wasted trips, safer and better-informed
decisions) and organizations (better capacity planning, faster incident response,
and a direct channel to their audience).

## MVP Boundary

**In the MVP:**
- Ticket wallet (ticket import), following public events, and venue QR codes
- Minimal discovery ("near me / today")
- Synchronization with the official event source (ticketing simulator) and change
  notifications
- Organizer backoffice: crowd levels, queues, incidents, alerts, and
  attendance-intention polls
- Organizer API for crowd levels and queues (with an entrance-counter simulator)
- Incident reporting by participants
- Provenance labels on all information
- Threshold-based weather alerts

**Out of scope for the MVP (for future versions):**
- Advanced search, filters, map, and recommendations
- Buying tickets within the app
- Social features and gamification
- Zone-level crowd information
- Real integrations with ticketing platforms and access-control systems

## Product Risks

- **Organizer adoption** — crowd levels, queues, and alerts depend on organizations
  using the backoffice or API; without them, only official changes and weather
  are available.
- **Stale information** — if organizations do not keep data updated, it loses
  value; last-update times must always be visible and outdated data hidden.
- **False alerts** — only the official source and verified organizations may
  publish official information.
- **Privacy** — tickets contain personal data and access codes; only the minimum
  data is stored, and participant incident reports do not expose user identity.
