# Ticket Extraction Evaluation

The evaluation behind [ADR 0020](../../docs/deliverables/adr/0020-local-nuextract-for-ticket-extraction.md): how well each method finds the event code, gate, sector and seat in the text of a ticket, after Core has removed the personal data. It is the starting point of the AI evaluation dataset (DoD-08, #72), which adds what is listed under [Still Missing](#still-missing).

All tickets are invented. Names, emails and tax numbers appear as `[REMOVIDO]`, as Core leaves them.

## Contents

| Path | What it is |
|---|---|
| `data/platform-tickets.json` | 30 tickets in the style of Portuguese and Spanish platforms (Ticketline, BOL, Blueticket, See Tickets, Fnac, Worten, Eventbrite, Ticketmaster, clubs, municipal theatres, cinema, Spain): 20 representative, 7 difficult and 3 adverse. They have PDF noise such as mixed columns, flattened tables, terms and conditions and many numbers. Written by someone who never saw the rules. The main dataset |
| `data/first-set.json` | 20 earlier tickets (5 representative, 11 difficult, 4 adverse), written by the same person as the rules, so they favour the rules. Kept for its difficult and adverse cases |
| `run.py` | Runs one method over a dataset: `rules`, the baseline without AI, or `nuextract`, with the prompt Core sends |
| `score.py` | Scores result files against their dataset |
| `make_pdfs.py` | Turns tickets into PDFs with a test QR code, to import them through Core end to end |
| `results/` | The reference results on the platform tickets: `rules.json` and `nuextract.json` (Q4_K_M, context 2048, as deployed). A new run is compared with them to catch regressions |

Each ticket has an `id`, a `kind` (`representative`, `difficult` or `adverse`), its `text` and the `expected` values of `eventCode`, `gate`, `sector` and `seat` (`null` when the ticket has none).

## Scoring

- **Event code:** right when it equals the expected one, ignoring case and punctuation. A wrong code is any other code returned; "not in text" counts wrong codes that are not in the ticket at all (invented).
- **Weighted score:** (right − 2 × wrong) / total. A wrong code could link the ticket to another event, while a missing code only makes the participant type it.
- **Gate, sector and seat:** right when equal after ignoring case, punctuation and label words ("Lugar 14" equals "14").
- **Latency:** per ticket, after a warm-up request. Memory is the model server's container, read with `docker stats` (the scripts only see their own).

## Results

All runs on 2 CPUs, like the VM (`Standard_B2ls_v2`), in November 2026. Only the rules and the deployed NuExtract are kept as files; the other rows record the models that were tried and rejected (ADR 0020).

### Platform tickets (30)

| Method | Right code | Wrong | Invented | Weighted | Gate / Sector / Seat | Time (median / max) | Model memory (idle / after use) |
|---|---|---|---|---|---|---|---|
| Rules, without AI (`results/rules.json`) | 12 | 2 | 0 | 0.27 | 18 / 17 / 12 | ~0 s | – |
| **NuExtract 2.0 2B Q4_K_M, context 2048 (`results/nuextract.json`)** | **28** | 2 | 0 | **0.80** | 22 / 18 / 11 | 7.7 / 10.5 s | 0.73 / 1.01 GB |
| NuExtract 2.0 2B Q4_K_M, context 4096 | 28 | 2 | 0 | 0.80 | 22 / 18 / 11 | 7.5 / 11.3 s | – / 1.06 GB |
| NuExtract 2.0 2B Q3_K_M, context 2048 | 21 | 8 | 0 | 0.17 | 16 / 13 / 7 | 8.0 / 16.1 s | 0.36 / 0.66 GB |
| NuExtract 1.5 tiny (0.5B) Q8_0 | 18 | 12 | 4 | −0.20 | 14 / 12 / 6 | 3.4 / 5.1 s | – / 0.23 GB |
| Qwen2.5 0.5B Instruct Q8_0 | 9 | 0 | 0 | 0.30 | 5 / 8 / 14 | 2.4 / 3.5 s | – / 0.21 GB |

NuExtract 2.0 2B's two wrong codes: on a cinema ticket it returned the age rating "M/12", and on a ticket without an event code it returned the order number. An exact match with a registered event refuses both.

### First set (20)

| Method | Right code | Wrong | Weighted | Gate / Sector / Seat | Time (median / max) | Memory |
|---|---|---|---|---|---|---|
| Rules, without AI | 18 | 0 | 0.90 | 17 / 17 / 13 | ~0 s | – |
| GLiNER multi v2.1 | 12 | 4 | 0.20 | 7 / 11 / 9 | 0.3 s (+23 s to load) | ~2.8 GB |
| NuExtract 2.0 2B Q4_K_M | 18 | 1 | 0.80 | 19 / 17 / 15 | 2.5 / 3.8 s | ~0.82 GB |

The rules do well here only because the same person wrote them and these tickets. GLiNER also picked injected codes, and was dropped.

### Through Core, end to end

Ten platform tickets were turned into PDFs with `make_pdfs.py` and imported with `POST /tickets`, with Core calling NuExtract 2.0 2B Q4_K_M: 8 of 10 event codes right. The two misses are tickets that print no event code (one returned the order number). Extraction took 5.7 to 12.5 s, and the model used about 920 MB. On the ticket with injected instructions the code stayed right, but the gate and sector came from the instructions ("VIP", "BACKSTAGE"), so Crowded shows them as read from the ticket, with a way to correct them.

One real ticket, without personal data and not kept here, had no event code, gate, sector or seat: NuExtract returned none and invented nothing. The rules invented a gate.

## Running It

Docker, from this folder. The model file is the one the deployment uses (`deployment/README.md`, Ticket Extraction Model): `NuExtract-2.0-2B-Q4_K_M.gguf` from [numind/NuExtract-2.0-2B-GGUF](https://huggingface.co/numind/NuExtract-2.0-2B-GGUF), SHA-256 `4725ac4d8437b8657005de6c5fb2de8dea186e654e9331a092f1c1146bc9582e`. Put it in a `models/` folder of your own, outside the repository.

```bash
docker network create ticket-eval
docker run -d --name llm --network ticket-eval --cpus=2 --memory=3g -v "<your models folder>:/models:ro" ghcr.io/ggml-org/llama.cpp:server -m /models/NuExtract-2.0-2B-Q4_K_M.gguf -c 2048 -t 2 --host 0.0.0.0 --port 8080
docker run --rm --network ticket-eval -v "$PWD:/e:ro" -w /e python:3.12-slim sh -c "python -I run.py nuextract data/platform-tickets.json /tmp/new.json && python -I score.py data/platform-tickets.json /tmp/new.json results/nuextract.json results/rules.json"
docker stats --no-stream llm
docker rm -f llm && docker network rm ticket-eval
```

The new run is scored next to the reference results, so a regression shows at once. Replace `results/nuextract.json` only when the model or the prompt changes on purpose. `run.py` and `score.py` use only Python's standard library, and `rules` needs no model server. `LLM_URL` points `run.py` at another server.

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
