# Crowded

Project developed for the LABDSOF (Software Development Laboratory) course, part of the Master's Degree in Informatics Engineering – Software Engineering, during the first semester of the 2026/2027 academic year.

Crowded keeps people informed when something changes or goes wrong at the events they attend, and gives organizations a direct channel to their audience: from testing interest in a proposed event, to announcing changes, to redirecting people on site. Crowded does not sell tickets; it works with tickets bought on any platform and with free public events.

## Team

| Student Number | Name               | Role                              |
|-----------------|-------------------|-----------------------------------|
| 1191256         | André Reis        | Scrum Master / Product Owner      |
| 1201506         | Guilherme Cunha   | Project Manager                   |
| 1211105         | Margarida Pereira | QA                                |
| 1250491         | Acácio Coutinho   | UI/UX                             |

## Documents

- [Assignment](/docs/LABDSOF-26-27-Assignment.md)
- [Problem and Opportunity Report](/docs/deliverables/problem-and-opportunity-report.md)
- [Market and Competitor Analysis Report](/docs/deliverables/market-and-competitor-analysis-report.md)
- [User Research](/docs/deliverables/user-research.md)
- [Product Vision](/docs/deliverables/product-vision.md)
- [Responsible AI Opportunity Assessment](/docs/deliverables/responsible-ai-opportunity-assessment.md)
- [Ethical Engagement Design](/docs/deliverables/ethical-engagement-design.md)
- [Requirements Specification](/docs/deliverables/requirements.md)
- [Security and Privacy Assessment](/docs/deliverables/security-and-privacy-assessment.md)
- [Definition of Ready and Definition of Done](/docs/deliverables/definition-of-ready-and-done.md)
- [Technical Design](/docs/deliverables/technical-design.md)
- [Architecture Decision Records](/docs/deliverables/adr/)
- [API Outline](/docs/deliverables/api-outline.md)

## Domain Model

![Crowded — Domain Model](/docs/deliverables/diagrams/domain-model.svg)

The domain model shows the main concepts of Crowded and how they relate, independently of how they are stored or deployed.

- **Event** is the centre of the model. It can start as a **proposed** event (no date or venue yet) whose **InterestPoll** tests demand before it is confirmed. Participants connect to it through a **Ticket** or a **Follow**, organizations own it and publish changes, on-site information, alerts and surveys about it, and participants report what they see.
- **Aggregate roots** (yellow) mark consistency boundaries: each one is created and changed as a unit, and other parts of the model refer to it rather than changing it directly.
- **Provenance** is attached to every piece of event information, so the product can always show where it came from and when it was last updated.
- **Disruptions** take three forms: official changes (**EventChange**, published by the organization or detected in the ticketing source, and recorded once when both report it), organizer alerts (**Alert**) and incidents reported by participants (**Incident**). Changes and alerts share a **Severity** (critical, warning, information), alerts stay active until the organization resolves them, and every **Notification** points to the disruption that caused it.
- Integration details (ticketing sync state, external providers) are kept out of the model; they belong to the services described in the [Technical Design](/docs/deliverables/technical-design.md).


Note: everyone's role is flexible and can change according to the needs of the project and everyone's assumed as developer as well.

## Contributing

### Commit Messages

All commits must follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) convention and reference the corresponding GitHub issue:

```
[fix|docs|chore|feat](#IssueNumber): message
```

Example:

```
fix(#10): raccoon now steals food with success
```

### Naming Conventions

- File names should be descriptive and use `kebab-case` and/or `camelCase`.