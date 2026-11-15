# Ticket Extraction Evaluation

The evaluation behind [ADR 0020](../../docs/deliverables/adr/0020-groq-for-ticket-extraction.md): how well each method finds the event code, gate, sector and seat in the text of a ticket, after Core has removed the personal data. It is the starting point of the AI evaluation dataset (DoD-08, #72), which adds what is listed under [Still Missing](#still-missing).

All tickets are invented. Names, emails and tax numbers appear as `[REMOVIDO]`, as Core leaves them.

## Contents

| Path | What it is |
|---|---|
| `data/platform-tickets.json` | 30 tickets in the style of Portuguese and Spanish platforms (Ticketline, BOL, Blueticket, See Tickets, Fnac, Worten, Eventbrite, Ticketmaster, clubs, municipal theatres, cinema, Spain): 20 representative, 7 difficult and 3 adverse. They have PDF noise such as mixed columns, flattened tables, terms and conditions and many numbers. Written by someone who never saw the rules. The main dataset |
| `data/first-set.json` | 20 earlier tickets (5 representative, 11 difficult, 4 adverse), written by the same person as the rules, so they favour the rules. Kept for its difficult and adverse cases |
| `run.py` | Runs one method over a dataset: `rules`, the baseline without AI, or `groq`, with the prompt Core sends |
| `score.py` | Scores result files against their dataset |
| `make_pdfs.py` | Turns tickets into PDFs with a test QR code, to import them through Core end to end |
| `results/` | The reference results on the platform tickets: `groq.json` (`qwen/qwen3.8-27b`, as deployed) and `rules.json`. A new run is compared with them to catch regressions |

Each ticket has an `id`, a `kind` (`representative`, `difficult` or `adverse`), its `text` and the `expected` values of `eventCode`, `gate`, `sector` and `seat` (`null` when the ticket has none).

## Scoring

- **Event code:** right when it equals the expected one, ignoring case and punctuation. A wrong code is any other code returned; "not in text" counts wrong codes that are not in the ticket at all (invented).
- **Weighted score:** (right − 2 × wrong) / total. A wrong code could link the ticket to another event, while a missing code only makes the participant type it.
- **Gate, sector and seat:** right when equal after ignoring case, punctuation and label words ("Lugar 14" equals "14").
- **Latency:** per ticket, after a warm-up request.

## Results

Run on Groq's free plan, in November 2026. Only `qwen/qwen3.8-27b` and the rules are kept as files.

### Platform tickets (30)

| Method | Right code | Wrong | Invented | Weighted | Gate / Sector / Seat | Time (median / max) |
|---|---|---|---|---|---|---|
| **Groq, `qwen/qwen3.8-27b` (`results/groq.json`)** | **29** | 1 | 0 | **0.90** | 30 / 28 / 27 | 0.3 / 1.4 s |
| Groq, `openai/gpt-oss-20b` | 26 | 4 | 1 | 0.60 | 27 / 26 / 23 | 0.6 / 1.1 s |
| Rules, without AI (`results/rules.json`) | 12 | 2 | 0 | 0.27 | 18 / 17 / 12 | ~0 s |

Qwen's wrong code is the event's name on a ticket that prints no code. On the ticket with injected instructions it kept the right gate and sector. In an earlier run, the free plan was busy for a moment (HTTP 503) on one ticket. `run.py` retries such answers, counts them in `busy` and times only the request that was answered; Core does not retry, so that participant would type the event code.

### First set (20)

| Method | Right code | Wrong | Weighted | Gate / Sector / Seat | Time (median / max) |
|---|---|---|---|---|---|
| Groq, `qwen/qwen3.8-27b` | 19 | 1 | 0.85 | 20 / 20 / 20 | 0.25 / 0.6 s |
| Rules, without AI | 18 | 0 | 0.90 | 17 / 17 / 13 | ~0 s |

The rules do well here only because the same person wrote them and these tickets. Qwen's wrong code is one injected into the ticket's text, which no registered event has.

### Through Core, end to end

Two tickets turned into PDFs with `make_pdfs.py` and imported with `POST /tickets` on the local stack, with Core calling Groq: both gave the right gate, sector and seat in under a second (`outcome=EXTRACTED`). Without a key, the same import asked for the event code at once (`outcome=UNAVAILABLE`).

## Running It

Docker, from this folder. Groq needs a key of your own in `GROQ_API_KEY`; `GROQ_MODEL` picks another model:

```bash
docker run --rm -e GROQ_API_KEY -v "$PWD:/e:ro" -w /e python:3.12-slim sh -c "python -I run.py groq data/platform-tickets.json /tmp/new.json && python -I score.py data/platform-tickets.json /tmp/new.json results/groq.json results/rules.json"
```

The new run is scored next to the reference results, so a regression shows at once. Replace a reference result only when the model or the prompt changes on purpose. `run.py` and `score.py` use only Python's standard library, and `rules` needs no key.

`make_pdfs.py` needs `qrcode==8.2` and `fpdf2==2.9.0` and the DejaVu font (`fonts-dejavu-core` on Debian):

```bash
python make_pdfs.py data/platform-tickets.json <output folder> p01-ticketline-concerto p28-injecao-instrucoes
```

## Still Missing

For the evaluation dataset (#72), as planned in the [Responsible AI Opportunity Assessment](../../docs/deliverables/responsible-ai-opportunity-assessment.md#evaluation-approach):

- ticket PDFs and screenshots as files, including low-quality scans, photos at an angle, cropped screenshots and several tickets in one file;
- more tickets without an event code and in other languages, and more embedded instructions;
- the share of tickets matched without typing the code, wrong matches, and the time to add a ticket compared with typing the code;
- datasets for alert drafts and report grouping.
