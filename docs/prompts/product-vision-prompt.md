# Prompt used to generate the Product Vision document

We are working on a project called "Community Resilience and Everyday Services Platform", and my team came up with the idea of Crowded, an event platform. We already have a first version of the Product Vision, but after the user research, the market and competitor analysis and the Responsible AI assessment we decided to refocus the product, so I need help rewriting the Product Vision document based on the existing documentation in `docs/`. It must include: the vision statement, personas, value proposition, principal user journeys, principal user workflows (as diagrams), expected community benefit, the MVP boundary, and product risks.

The new focus is no longer event discovery or connecting people with similar interests. Crowded should keep people informed when something changes or goes wrong at the events they are attending, and give organizations the tools to communicate with and manage their audience before and during an event. Ticketing platforms and discovery apps stop being useful once the ticket is bought; Crowded should follow the event until it ends, bringing together three sources of information: official changes synchronized from the ticketing source, on-site conditions and alerts published by the organization, and incidents reported by participants. Every piece of information must always show where it comes from and when it was last updated.

I would like two personas, each with name, age/context, goal, a real frustration and how Crowded helps:

- a participant (a university student in Lisbon) who goes to paid concerts and free public events, has already arrived at a cancelled event without knowing, and has faced overcrowded venues and long queues;
- an organizer (an event manager at a local cultural association) who uses several channels to communicate updates, never knows whether everyone was informed in time, and has no direct way to redirect the audience when something goes wrong on site.

The features we want to cover are:

- **Participants:** import a ticket bought elsewhere or follow a public event; minimal "near me / today" discovery only as an entry point; notifications when an event is cancelled, postponed or moved; checking crowd levels, queues, organizer alerts and weather; reporting incidents (lost and found, accessibility barriers, on-site problems); and receiving a private recognition when the organization marks a report as helpful.
- **Organizations:** attendance-intention polls for capacity planning (an indication, not guaranteed attendance); requests on the event page (confirmations, polls or ideas) that can be marked as urgent to notify participants; a backoffice or API to publish crowd levels and queues; an incident inbox; and alerts sent to every connected participant.

AI should only be used where it was justified in the Responsible AI assessment, and always with a non-AI fallback:

- extracting event details from a ticket PDF or confirmation email, removing personal data and access codes, with manual entry as fallback and manual confirmation by the participant;
- drafting an alert from a brief organizer input and the current event state, with a predefined template as fallback, always reviewed by the organization before sending;
- grouping similar incident reports into one incident with a summary, with grouping by category, location and time window as fallback, and the organization able to split wrong groupings.

Turn these features into user journeys (sequences of steps, not a loose list) and then into four principal workflows: connecting to an event, official event change notification (periodic sync with the ticketing source simulator, change detection, message queue and notifications without duplicates), organization manages an event, and incident report and recognition. Model the workflows in BPMN 2.0 notation, with pools and lanes per actor, start and end events, user, service and send tasks, exclusive gateways and message flows, highlighting the AI tasks.

In the community benefit section, connect the product to the project's central theme: helping communities deal with everyday disruptions — cancellations, schedule or venue changes, overcrowding, long queues and on-site incidents — for both participants and organizations.

For the MVP, include: the ticket wallet and following public events, minimal discovery, synchronization with the official event source (ticketing simulator) and change notifications, the organizer backoffice (crowd levels, queues, incidents, alerts and attendance-intention polls), an organizer API for crowd levels and queues with an entrance-counter simulator, incident reporting, provenance labels on all information, and threshold-based weather alerts. Leave out for future versions: advanced search, filters, map and recommendations, buying tickets in the app, social features and gamification, zone-level crowd information, and real integrations with ticketing platforms and access-control systems.

The risks we identified are: dependence on organizer adoption (without it only official changes and weather are available), stale information if organizations do not keep data updated, false alerts (only the official source and verified organizations may publish official information), and privacy, since tickets contain personal data and access codes.

Keep the document concise and consistent with the requirements and the other deliverables.

---

**Note:** The content generated from this prompt was reviewed and adjusted by the team before being considered final.
