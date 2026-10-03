# Responsible AI Opportunity Assessment

## Proposed AI-Assisted Workflow

1. **Ticket information extraction:** When a user uploads a ticket (PDF) or pastes a confirmation email, extract the structured event information (event name, date and time, venue, gate or sector, seat) and match it to an event in Crowded's synchronized event catalog. Personal data and access codes are removed before the content is sent to a language model. The user reviews and confirms the extracted fields before the ticket is saved; low-confidence matches are never linked automatically.
2. **Alert drafting for organizers:** During an event, when an organizer needs to communicate a situation (e.g., an entrance closure, capacity reached, a weather warning), generate a short, clear alert draft from the event's current state (crowd levels, queues, open incidents) and the organizer's brief input. The organizer edits and approves the draft before it is sent; the AI never sends alerts on its own.
3. **Incident report grouping:** When participants submit incident reports during an event, group reports that describe the same problem (similar category, location and time, even when worded differently) into a single incident with a short summary (e.g., "12 reports · Toilets near Stage 1 have no water"). The organization sees the grouped incident with all original reports and can split reports that were grouped incorrectly. Grouping only organizes reports; it never discards them or changes their status.

Weather alerts remain rule-based: they are triggered by configured thresholds applied to a trusted weather source and are not part of the AI workflow.

## Expected Value

Ticket extraction reduces the effort of connecting a ticket bought elsewhere to Crowded, since ticket formats vary between platforms and manual entry is slow and error-prone. Without this connection, users do not receive change notifications. Alert drafting helps organizers communicate faster and more clearly under pressure, when writing a precise message is hardest. Report grouping keeps the organization's incident inbox manageable during large events, when many participants report the same problem at once, so that real and distinct problems are not lost among duplicates.

## Risks

### R1 - Privacy and Data Security
Tickets and confirmation emails contain personal data (name, email, tax number) and access codes.

**Mitigation**
> Extract text locally and remove personal data and access codes before sending content to an external AI provider. Never store access codes in plain text, and delete the uploaded file after extraction.

### R2 - Inaccurate Extraction or Wrong Event Match
An incorrect date or a wrong event match could cause the user to miss change notifications.

**Mitigation**
> Require the user to confirm the extracted fields, highlight low-confidence fields, and ask the user to choose when more than one event matches.

### R3 - Inaccurate or Misleading Alert Content
A draft that invents or changes facts (e.g., the wrong gate) could misdirect people during the event.

**Mitigation**
> Generate drafts only from structured event data and the organizer's input, highlight the facts used in the draft, and require organizer approval before sending.

### R4 - Prompt Injection
Ticket files, emails and incident descriptions are untrusted inputs that may contain instructions aimed at the model.

**Mitigation**
> Treat all inputs as data, validate model outputs against a fixed schema, and give the model no ability to perform actions.

### R5 - Over-Reliance on AI Drafts
Organizers under pressure may approve drafts without reading them carefully.

**Mitigation**
> Keep drafts short, show the source data next to the draft, and label AI-generated content until it is approved.

### R6 - Incorrect Grouping of Reports
Grouping distinct problems together could hide an incident, while failing to group duplicates leaves the inbox cluttered.

**Mitigation**
> Show all original reports inside each group, let the organization split groups, prefer leaving reports ungrouped when similarity is uncertain, and never let grouping resolve or dismiss a report.

## Evaluation Approach

**Ticket extraction:** Build a synthetic dataset of tickets and confirmation emails based on the formats of common ticketing platforms, without real personal data. Include difficult cases such as scanned or low-quality files, multiple tickets in one file, missing fields, other languages and embedded instructions. Measure field-level accuracy, correct event-match rate, the percentage of tickets requiring manual correction, latency and cost per ticket, and compare the time to add a ticket against manual entry.

**Alert drafting:** Build a set of scenarios (entrance closures, capacity reached, weather warnings, schedule changes) with the expected key facts. Measure whether every key fact is present and no fact is invented, how often organizers edit the draft, and the time to send an alert compared with using templates. Include adverse cases such as contradictory input and injected instructions in incident descriptions. A draft with an invented fact is a failure.

**Incident report grouping:** Build a synthetic dataset of incident reports for simulated events, labelled with the incident each report belongs to. Include difficult cases such as different wording for the same problem, the same wording for problems in different locations, reports in other languages, very short or vague reports and embedded instructions. Measure grouping precision (reports wrongly merged) and recall (duplicates left separate), how often organizations split groups, and latency. Compare against the non-AI baseline of grouping by category, location and time window. Wrongly merging two distinct incidents is treated as more serious than leaving duplicates separate.

## Baseline or Fallback Strategy

For ticket extraction, the fallback is a manual entry form with non-AI event matching by name, date and venue. If the AI service is unavailable, the user is shown the form directly.

For alert drafting, the baseline is a set of predefined alert templates with fields the organizer fills in. If the AI service is unavailable, the organizer uses the templates or writes the alert manually.

For incident report grouping, the baseline is rule-based grouping by category, location and time window. If the AI service is unavailable, reports are grouped with these rules, or shown individually in chronological order.
