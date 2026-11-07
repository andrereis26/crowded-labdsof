# Responsible AI Opportunity Assessment

## Proposed AI-Assisted Workflow

1. **Ticket information extraction:** When a participant uploads a ticket (a PDF, or a screenshot or photo), extract the event code printed on it and the ticket details (gate or sector, seat). The QR code is decoded without AI. Personal data and access codes are removed before the content is sent to a language model. The extraction only finds the key: the ticket is added when the extracted event code matches an event registered by an organization, and the summary then shows the registered event's data, never the extracted name, date or venue. So the AI's output is never presented as event information, and no review step is needed. When the code is missing or matches nothing, nothing is linked automatically: the participant types the event code printed on the ticket.
2. **Alert drafting for organizers:** During an event, when an organizer needs to communicate a situation (e.g., an entrance closure, capacity reached, a weather warning), generate a short, clear alert draft from the event's current state (crowd levels, queues, open incidents) and the organizer's brief input. The organizer edits and approves the draft before it is sent; the AI never sends alerts on its own.
3. **Incident report grouping:** When participants submit incident reports during an event, group reports that describe the same problem (similar category, location and time, even when worded differently) into a single incident with a short summary (e.g., "12 reports · Toilets near Stage 1 have no water"). The organization sees the grouped incident with all original reports and can split reports that were grouped incorrectly. Grouping only organizes reports; it never discards them or changes their status.

Weather warnings remain rule-based: they come from IPMA, a trusted public source, and are not part of the AI workflow.

## Expected Value

Ticket extraction reduces the effort of connecting a ticket bought elsewhere to Crowded, since ticket formats vary between platforms and finding and typing a code is slow and error-prone. Without this connection, users do not receive change notifications. Alert drafting helps organizers communicate faster and more clearly under pressure, when writing a precise message is hardest. Report grouping keeps the organization's incident inbox manageable during large events, when many participants report the same problem at once, so that real and distinct problems are not lost among duplicates.

## Risks

### R1 - Privacy and Data Security
Tickets contain personal data (name, email, tax number) and access codes.

**Mitigation**
> Extract text locally and remove personal data and access codes before sending content to an external AI provider. Never store access codes in plain text, and delete the uploaded file after extraction.

### R2 - Inaccurate Extraction or Wrong Event Match
A wrongly read event code could connect the participant to another event, and they would miss its change notifications.

**Mitigation**
> Link a ticket only on an exact match of the event code with a registered event, never on a similar name or date. Show the matched event's name, date and venue in the summary, with an option to remove it, so a wrong match is visible at once. When there is no exact match, ask the participant to type the code instead of guessing.

### R3 - Inaccurate or Misleading Alert Content
A draft that invents or changes facts (e.g., the wrong gate) could misdirect people during the event.

**Mitigation**
> Generate drafts only from structured event data and the organizer's input, highlight the facts used in the draft, and require organizer approval before sending.

### R4 - Prompt Injection
Ticket files, screenshots and incident descriptions are untrusted inputs that may contain instructions aimed at the model.

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

**Ticket extraction:** Build a synthetic dataset of ticket PDFs and screenshots based on the formats of common ticketing platforms, without real personal data. Include difficult cases such as low-quality scans, photos at an angle, cropped screenshots, multiple tickets in one file, a missing event code, other languages and embedded instructions. Measure the event-code accuracy, the share of tickets matched without typing the code (target in the [Product Vision](product-vision.md#success-criteria)), wrong matches (which must be zero), field-level accuracy for gate, sector and seat, latency and cost per ticket, and compare the time to add a ticket against typing the code.

**Alert drafting:** Build a set of scenarios (entrance closures, capacity reached, weather warnings, schedule changes) with the expected key facts. Measure whether every key fact is present and no fact is invented, how often organizers edit the draft, and the time to send an alert compared with writing it manually. Include adverse cases such as contradictory input and injected instructions in incident descriptions. A draft with an invented fact is a failure.

**Incident report grouping:** Build a synthetic dataset of incident reports for simulated events, labelled with the incident each report belongs to. Include difficult cases such as different wording for the same problem, the same wording for problems in different locations, reports in other languages, very short or vague reports and embedded instructions. Measure grouping precision (reports wrongly merged) and recall (duplicates left separate), how often organizations split groups, and latency. Compare against the non-AI baseline of grouping by category, location and time window. Wrongly merging two distinct incidents is treated as more serious than leaving duplicates separate.

## Baseline or Fallback Strategy

For ticket extraction, the fallback is typing the event code printed on the ticket, matched without AI. The QR code is always decoded without AI. If the AI service is unavailable, the participant is asked for the code directly.

For alert drafting, the baseline is writing the alert manually: the organizer chooses the alert type and writes the message without AI assistance. If the AI service is unavailable, the organizer is taken directly to this manual editor. No predefined templates are provided.

For incident report grouping, the baseline is rule-based grouping by category, location and time window. If the AI service is unavailable, reports are grouped with these rules, or shown individually in chronological order.
