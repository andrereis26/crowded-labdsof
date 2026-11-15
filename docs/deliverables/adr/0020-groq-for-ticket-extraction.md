# ADR 0020: Groq for ticket extraction

- Status: accepted
- Date: 2026-11-15
- Deciders: Crowded team
- Amends: [ADR 0010](0010-external-integrations.md) (the AI vendor, for ticket extraction only)

## Context

When a participant imports a ticket PDF, Core reads the QR code without AI, removes the personal data and the entry codes, and asks an AI model for the event code, gate, sector and seat printed on the ticket (#20). ADR 0010 put the model behind the `TicketExtractor` port, called through an external API, and left the vendor open.

Imports are few: a participant adds a ticket once per event. Ticket extraction should cost nothing per call. The ticket's text is sent without personal data, but the vendor must not train on it.

## Options considered

- **A paid provider**, such as Anthropic or OpenAI. Every call costs money, and the provider needs a payment method.
- **Google Gemini's free plan.** Free, but Google uses the requests to improve its products.
- **Mistral's free plan.** Its free API access is unclear.
- **Groq's free plan** or **Cloudflare Workers AI's free plan.** Free, and neither uses API data for training.

Groq was evaluated on synthetic tickets, all text without personal data, with the prompt Core sends. The datasets, scripts and results are in [evaluation/ticket-extraction/](../../../evaluation/ticket-extraction/README.md). The main dataset has 30 tickets in the style of Portuguese and Spanish platforms (Ticketline, BOL, Blueticket, See Tickets, Fnac, Eventbrite, Ticketmaster, clubs, theatres, cinema), written by someone who never saw the rules, with PDF noise such as mixed columns, flattened tables and terms and conditions. The weighted score is (right − 2 × wrong) / total: a wrong code could link the ticket to another event, while a missing code only makes the participant type it.

| Method | Right code | Wrong code | Weighted | Gate / Sector / Seat | Time per ticket (median / max) |
|---|---|---|---|---|---|
| Groq, `qwen/qwen3.8-27b` | 29/30 | 1 | 0.90 | 30 / 28 / 27 | 0.3 / 1.4 s |
| Groq, `openai/gpt-oss-20b` | 26/30 | 4 | 0.60 | 27 / 26 / 23 | 0.6 / 1.1 s |
| Rules, without AI | 12/30 | 2 | 0.27 | 18 / 17 / 12 | ~0 s |

Qwen's only wrong code is the event's name on a ticket that prints no code. On the ticket with injected instructions it kept the right gate and sector. In one run, the free plan was busy for a moment (HTTP 503) on one ticket.

## Decision

- **Ticket extraction uses `qwen/qwen3.8-27b` on Groq's free plan**, through its API. Core's adapter, `GroqTicketExtractor`, sends only the ticket's text without personal data and entry codes. The model is set by `crowded.ai.groq.model`.
- The key is `GROQ_API_KEY` in Doppler, given to Core as a Swarm secret. Without a key, as on development machines, every extraction fails and the participant types the event code.
- The answer goes through the schema check of ADR 0010: only the event code, gate, sector and seat, each text or missing.
- **The typed event code stays the fallback.** When there is no key, or Groq is down, busy, slower than 25 seconds or answers outside the schema, the participant types the code at once. Every import ends within 30 seconds.
- The extracted code links a ticket only on an exact match with a registered event, which refuses the wrong codes seen in the evaluation. Gate, sector and seat are shown as read from the ticket, and the participant can correct them, because injected text can change them.
- Alert drafts and report grouping are not covered. Their vendor stays open in ADR 0010.

## Consequences

- No cost per ticket, and no memory or CPU on the VM: an extraction takes about a second.
- The ticket's text, without names, emails, tax numbers or entry codes, is sent to Groq, so Groq is a processor in the privacy assessment. Groq does not train on API data, and Zero Data Retention is on in its data controls, so requests are not stored.
- The free plan is sometimes busy for a moment and has limits: for this model, 30 requests a minute and 1,000 requests and 200,000 tokens a day. A ticket used about 350 tokens in the evaluation, so about 570 tickets a day fit, above the expected volume, and Core already limits each participant to 20 imports an hour. If Groq changes or ends its free plan, imports fall back to the typed event code until the model or vendor changes.
- Groq can retire a model; every call then fails, which the fallback absorbs, and `crowded.ai.groq.model` picks another one. Latency and outcome are measured per extraction (`crowded.tickets.extraction`, with the outcome as a tag), so failures show at once.
- The evaluation in `evaluation/ticket-extraction/` is run again before changing the model or the prompt, and the evaluation dataset (#72) grows from it.
