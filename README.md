# Crowded

Project developed for the LABDSOF (Software Development Laboratory) course, part of the Master's Degree in Informatics Engineering – Software Engineering, during the first semester of the 2026/2027 academic year.

Crowded is a platform that helps people discover, manage and attend events, providing real-time information about event changes and venue conditions, and connecting attendees with similar interests.

## Documents

- [Assignment](/docs/LABDSOF-26-27-Assignment.md)
- [Problem and Opportunity Report](/docs/deliverables/problem-and-opportunity-report.md)
- [Market and Competitor Analysis Report](/docs/deliverables/market-and-competitor-analysis-report.md)
- [User Research](/docs/deliverables/user-research.md)
- [Product Vision](/docs/deliverables/product-vision.md)
- [Responsible AI Opportunity Assessment](/docs/deliverables/responsible-ai-opportunity-assessment.md)
- [Requirements Specification](/docs/deliverables/requirements.md)
- [Security and Privacy Assessment](/docs/deliverables/security-and-privacy-assessment.md)
- [Definition of Ready and Definition of Done](/docs/deliverables/definition-of-ready-and-done.md)
- [Technical Design](/docs/deliverables/technical-design.md)
- [Architecture Decision Records](/docs/deliverables/adr/)

## Domain Model

![Crowded — Domain Model](/docs/deliverables/diagrams/domain-model.svg)

The domain model shows the main concepts of Crowded and how they relate, independently of how they are stored or deployed.

- **Event** is the centre of the model. Participants connect to it through a **Ticket** or a **Follow**, organizations own it and publish on-site information, alerts, surveys and polls about it, and participants report what they see.
- **Aggregate roots** (yellow) mark consistency boundaries: each one is created and changed as a unit, and other parts of the model refer to it rather than changing it directly.
- **Provenance** is attached to every piece of event information, so the product can always show where it came from and when it was last updated.
- Integration details (ticketing sync state, external providers) are kept out of the model; they belong to the services described in the [Technical Design](/docs/deliverables/technical-design.md).

## Team

| Student Number | Name               |
|-----------------|-------------------|
| 1191256         | André Reis        |
| 1201506         | Guilherme Cunha   |
| 1211105         | Margarida Pereira |
| 1250491         | Acácio Coutinho   |

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