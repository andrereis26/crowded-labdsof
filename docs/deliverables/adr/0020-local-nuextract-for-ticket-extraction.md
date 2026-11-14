# ADR 0020: Local NuExtract model for ticket extraction

- Status: accepted
- Date: 2026-11-14
- Deciders: Crowded team
- Amends: [ADR 0010](0010-external-integrations.md) (the AI vendor, for ticket extraction only)

## Context

When a participant imports a ticket PDF, Core reads the QR code without AI, removes the personal data and the entry codes, and asks an AI model for the event code, gate, sector and seat printed on the ticket (#20). ADR 0010 put the model behind the `TicketExtractor` port and left the vendor open. The [Operating Costs](../operating-costs.md) estimated a paid external provider that reads PDFs.

The team wanted a free option in which the ticket's text never leaves Crowded's infrastructure: no per-call cost, no payment method, no provider key, and no processor to add to the privacy assessment. Tickets come from many platforms, each with its own layout, so fixed rules or classic OCR are not expected to find the event code reliably.

The VM is a `Standard_B2ls_v2`: 2 vCPU and 4 GB of memory, shared by the whole stack ([ADR 0013](0013-single-azure-vm-docker-swarm-with-terraform.md)).

## Options considered

- **A paid external provider**. Reads the PDF directly and needs no memory on the VM, but the text leaves the infrastructure, every call costs money, and the provider needs a key and a payment method.
- **Rules only, without AI.** Labels such as "Código" or "Event code" followed by a value.
- **A small local model** behind llama.cpp's server: NuExtract 2.0 2B, NuExtract 1.5 tiny, Qwen2.5 0.5B Instruct, or GLiNER for named entities.

The local options were compared on synthetic tickets, all text without personal data, on 2 CPUs like the VM. The datasets, scripts and every result are in [`evaluation/ticket-extraction/`](../../../evaluation/ticket-extraction/README.md). The main dataset has 30 tickets in the style of Portuguese and Spanish platforms (Ticketline, BOL, Blueticket, See Tickets, Fnac, Eventbrite, Ticketmaster, clubs, theatres, cinema), written by someone who never saw the rules, with PDF noise such as mixed columns, flattened tables and terms and conditions. The weighted score is (right − 2 × wrong) / total: a wrong code could link the ticket to another event, while a missing code only makes the participant type it.

| Method | Licence | Right code | Wrong code | Weighted | Time per ticket (median / max) | Memory (idle / after use) |
|---|---|---|---|---|---|---|
| Rules, without AI | – | 12/30 | 2 | 0.27 | ~0 s | ~20 MB |
| NuExtract 2.0 2B, Q4_K_M, context 2048 | MIT | 28/30 | 2 | 0.80 | 7.7 / 10.5 s | 0.73 / 1.01 GB |
| NuExtract 2.0 2B, Q3_K_M, context 2048 | MIT | 21/30 | 8 | 0.17 | 8.0 / 16.1 s | 0.36 / 0.66 GB |
| NuExtract 1.5 tiny (0.5B), Q8_0 | MIT | 18/30 | 12 | −0.20 | 3.4 / 5.1 s | – / 0.23 GB |
| Qwen2.5 0.5B Instruct, Q8_0 | Apache 2.0 | 9/30 | 0 | 0.30 | 2.4 / 3.5 s | – / 0.21 GB |

GLiNER was tried first, on an earlier set of 20 tickets: 12/20 right with 4 wrong and 2.8 GB of memory, and it picked injected codes. It was dropped. 

The external provider was not measured, to avoid paying for the test.

NuExtract 2.0 2B was then wired into Core and tested end to end through `POST /tickets` with 10 ticket PDFs: 8/10 codes right, and the 2 wrong answers were tickets that print no event code (it returned an order number). Extraction took 5.7 to 12.5 s. On one real ticket without an event code it invented none. On a ticket with injected instructions the code stayed right, but the gate and sector came from the instructions ("VIP", "BACKSTAGE").

## Decision

- **Ticket extraction uses NuExtract 2.0 2B** (`NuExtract-2.0-2B-Q4_K_M.gguf`, MIT), run by llama.cpp's server as the nuextract service of the Swarm stack, with `-c 2048 -t 2`. It is on the internal network only, and only Core calls it.
- Core's adapter, `NuExtractTicketExtractor`, sends the model the ticket's text without personal data. The answer goes through the same schema check as before ([ADR 0010](0010-external-integrations.md)). It is enabled by `CROWDED_AI_NUEXTRACT_URL`.
- **The typed event code stays the fallback.** When the URL is not set, or the model is down, slower than 25 seconds or answers outside the schema, the participant types the code at once. Every import ends within 30 seconds.
- The extracted code links a ticket only on an exact match with a registered event, which refuses the wrong codes seen in the evaluation. Gate, sector and seat are shown as read from the ticket, and the participant can correct them, because injected text can change them.
- Alert drafts and report grouping are not covered: NuExtract only extracts. Their vendor stays open in ADR 0010.

## Consequences

- No cost per ticket and no provider key. The ticket's text never leaves the VM, so the privacy assessment gains no processor.
- **About 1 GB of the VM's 4 GB goes to the model.** The team accepts this trade-off. The service has a 1,280 MB limit and stops before it is updated, so two copies never run at once.
- **Memory is watched in production:** the model's and the VM's memory with `docker stats` after each deployment and during demos, and on the operational dashboard once the observability stack is deployed ([ADR 0009](0009-observability-stack.md)). If the VM runs short, the options are a larger VM or running the model only when needed.
- An extraction uses both CPUs for about 8 seconds, so the other services slow down during it. Latency and outcome are measured per extraction (`crowded.tickets.extraction`, with the outcome as a tag) to see whether the 25-second timeout is reached on the VM, whose CPU may be slower than the test machine's.
- The model is weaker on gate, sector and seat than on the event code, and injected text can change them. They are never used to decide anything.
- The deployment downloads the model file (986 MB) from the Hugging Face commit that was evaluated and checks its SHA-256, once per host. The server image is pinned by digest to the build that was evaluated, so a newer build replaces it only through a reviewed change.
- The evaluation dataset (#72) grows from `evaluation/ticket-extraction/` and becomes the reference measure.
