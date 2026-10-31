# Operating Costs Estimation

What it costs to run Crowded each month: the infrastructure of the [Deployment View](technical-design.md#deployment-view), the delivery tools of [ADR 0011](adr/0011-ci-pipeline-and-vulnerability-policy.md) and the external services of [ADR 0010](adr/0010-external-integrations.md). There are two scenarios:

- **A. The MVP as built in this course:** one production environment that also plays development and staging ([ADR 0013](adr/0013-single-azure-vm-docker-swarm-with-terraform.md#environments)), demo traffic, paid from the Azure for Students credit.
- **B. A real launch after the MVP:** the three environments of ADR 0013, real participants and a paid AI provider.

Team work is not an operating cost and is not included.

Prices were collected on 29 October 2026:
- **Azure:** from the [Azure Retail Prices API](https://prices.azure.com/api/retail/prices), region `westeurope` (the closest to Portugal), pay-as-you-go, in euros.
- **Other services:** from their own pricing pages, in US dollars, as they bill.

A month is 730 hours.

## 1. Summary

| | A. MVP (course) | B. Real launch |
|---|---|---|
| Azure infrastructure | **€40.35** always on, or **€18.82** stopped outside working hours | €152.03 |
| GitHub Actions (beyond the included minutes) | **US$11 to US$17** at the current pace, or **US$0** if the repository is public | US$0 to US$17 |
| AI provider | US$0.60 to US$12 | US$7.50 to US$150 |
| Everything else (Docker Hub, Doppler, Firebase, weather, maps, TLS, design) | US$0 | US$16 to US$58, plus a commercial weather plan |
| **Total per month** | **about €19 to €70** | **about €175 to €350**, plus the weather plan |
| Paid by | Azure for Students credit (US$100 a year); GitHub and the AI provider need a payment method | A real budget |

Scenario A fits the student credit if the VM is stopped when nobody uses it. Two decisions follow from it (section 6): turn on the deallocation schedule, and decide what to do about GitHub Actions minutes.

## 2. Scenario A: the MVP

### 2.1 Azure infrastructure

OpenTofu provisions one resource group in `westeurope` ([ADR 0013](adr/0013-single-azure-vm-docker-swarm-with-terraform.md)):

| Item | Choice | Unit price | Per month | Why this size |
|---|---|---|---|---|
| Virtual machine | `Standard_B2s`, Linux: 2 vCPU, 4 GB | €0.0422 per hour | €30.81 | The smallest burstable size that fits the stack: three Spring Boot services (limits 640, 448 and 448 MB), PostgreSQL, RabbitMQ, Redis, the router, two web apps and the observability stack ([ADR 0009](adr/0009-observability-stack.md)). The 1 GB of AWS's free t3.micro was too small ([ADR 0008](adr/0008-single-ec2-docker-swarm-with-terraform.md)). Burstable fits demo traffic: idle most of the time, short peaks during demos and CI deploys. |
| OS disk | Standard SSD `E4`, 32 GiB | €2.11 per month | €2.11 | Ubuntu and the Docker images. SSD, not HDD, because image pulls and container starts are disk-bound. |
| Data disk | Standard SSD `E6`, 64 GiB | €4.22 per month | €4.22 | Volumes of PostgreSQL, RabbitMQ, Redis, Prometheus and Loki. Logs and metrics grow fastest; 64 GiB holds their retention with room for the database. |
| Public IP | Standard, static IPv4 | €0.0044 per hour | €3.21 | A fixed address for the router and DNS. Azure gives it a free DNS name (`<label>.westeurope.cloudapp.azure.com`), so no domain has to be bought. |
| Outbound traffic | – | First 100 GB per month free | €0.00 | Demo traffic is a few GB: JSON APIs, two small web apps and image pulls, which are inbound and free. |
| **Total, always on** | | | **€40.35** | |

**When the VM is deallocated, compute stops billing**, but the disks and the IP still cost €9.54 a month. If the VM runs only on working days, 10 hours a day (about 220 hours a month), it costs €9.28 + €9.54 = **€18.82**. The shared environment is needed for demos, sprint reviews and verifying stories (DoD-10), not overnight.

**The student credit** is US$100 for 12 months (about €85 to €95, depending on the exchange rate). From the first deployment (November 2026) to the end of Sprint 3 (3 January 2027) is about two months:

| Usage | Two months | Share of the credit |
|---|---|---|
| Always on | about €81 | almost all of it |
| On working hours only | about €38 | under half, leaving room for load tests (NFR-009) and a larger VM if 4 GB is not enough |

### 2.2 Delivery and development tools

| Item | Plan | Per month | Justification |
|---|---|---|---|
| GitHub repository, issues, board | Free | US$0 | Private repository on a personal account |
| **GitHub Actions** | Includes **US$12** of compute (GitHub Free) or **US$18** (GitHub Pro, which students get for free); Linux 2-core runner at **US$0.006 per minute** ([GitHub docs](https://docs.github.com/en/billing/reference/actions-runner-pricing)) | **US$11 to US$17** | **Measured**: 1,129 runner minutes from 22 to 29 October 2026, about 4,840 a month, which is US$29. The repository is private, so minutes are billed. That week included setting up the backend, the CI and the database, so it is an upper bound. A typical sprint week will be lower. |
| Docker Hub | Personal, free | US$0 | Public images ([ADR 0011](adr/0011-ci-pipeline-and-vulnerability-policy.md)). The host pulls only the images that changed, well within the free pull limits. |
| Doppler | Developer, free for up to 3 users ([pricing](https://www.doppler.com/pricing)) | US$0 | Only the people who manage secrets need a seat. Deployments use a service token, which is not a user. |
| Security scans | Trivy, gitleaks, GitGuardian | US$0 | Open source or free for small teams |
| Figma | Education | US$0 | Mockups (#75) |

**GitHub Actions is the only cost outside the student credit.** When the included amount runs out, GitHub stops the workflows unless a budget above zero is set on the account that owns the repository. The options are in section 6.

### 2.3 External services

| Service | Choice in the MVP | Per month | Justification |
|---|---|---|---|
| Ticketing source, municipal agenda, entrance counters | Simulators in the Swarm stack (TR-001, TR-004, TR-005) | €0 | They run on the VM above |
| Push notifications | Firebase Cloud Messaging ([ADR 0010](adr/0010-external-integrations.md)) | US$0 | FCM has no charge per message |
| Weather | [Open-Meteo](https://open-meteo.com/en/pricing), free non-commercial API | US$0 | Limits: 10,000 calls a day and 300,000 a month. Jobs checks each upcoming event every 3 hours. 50 events need 400 calls a day, 4% of the limit. |
| Maps and transport | No API in the MVP: the venue address and a link that opens the phone's maps app | US$0 | The live transport API is after the MVP ([Technical Design](technical-design.md#maps-and-transport)) |
| TLS certificate | Let's Encrypt, on Azure's free DNS name | US$0 | |
| AI provider | Vendor still open ([ADR 0010](adr/0010-external-integrations.md)). Estimated with Anthropic's Claude prices, as an example of a provider that reads PDFs | **US$0.60 to US$12** | See 2.4 |

### 2.4 AI provider

The AI is used three times (Responsible AI Opportunity Assessment): ticket extraction (Core), alert drafts (Core) and grouping similar reports (Jobs). Every call is rate limited per user and per organization ([Technical Design](technical-design.md#ai-provider)), so the volume, and therefore the cost, has a ceiling.

| Call | Input tokens | Output tokens | Assumption |
|---|---|---|---|
| Ticket extraction | 5,000 | 500 | A 1–2 page ticket PDF read as document input, plus the prompt and the extraction schema |
| Alert draft | 1,500 | 300 | The organizer's note, the event facts and the prompt |
| Report grouping | 3,000 | 500 | The reports of a time window and the open incidents |

Prices per million tokens (input / output): **Claude Haiku 5.5** at US$0.10 / US$0.50, the cheapest model that reads PDFs; **Claude Sonnet 5.5** at US$2 / US$10, for when the evaluation (#72) shows Haiku is not accurate enough.

| Per call | Haiku 5.5 | Sonnet 5.5 |
|---|---|---|
| Ticket extraction | US$0.00075 | US$0.015 |
| Alert draft | US$0.0003 | US$0.006 |
| Report grouping | US$0.00055 | US$0.011 |

**MVP volume** (demos, tests, the evaluation dataset): 500 extractions, 200 drafts and 300 groupings a month gives **US$0.60 with Haiku and US$12 with Sonnet**.

Automated tests never call the provider: the adapter has a fake implementation ([ADR 0010](adr/0010-external-integrations.md)). Only the evaluation runs and the demos spend money.

## 3. Scenario B: a real launch

The design of [ADR 0013](adr/0013-single-azure-vm-docker-swarm-with-terraform.md), with all three environments, and a first audience of a few thousand participants. Assumptions: 10,000 imported tickets, 2,000 alerts and 5,000 report groupings a month.

| Item | Per month | Change from scenario A |
|---|---|---|
| Production VM | €71.23 | `Standard_B2ms` (8 GB, €0.0845 per hour) for headroom under real load, with the same disks and IP as in A |
| Development and staging | €80.70 | Two B2s VMs like scenario A, always on. Stopped outside working hours: €37.64 |
| Backups | under €0.10 | Daily `pg_dump` kept 30 days in Blob Storage (hot, LRS, €0.017 per GB) |
| AI provider | US$7.50 (Haiku) to US$150 (Sonnet) | Ticket volume dominates: 10,000 × US$0.00075 or US$0.015 |
| Weather | A commercial Open-Meteo plan (API Standard, 1 million calls a month; price on request) | The free API is non-commercial only |
| Doppler | US$16 to US$58 | 2 people beyond the 3 free seats (US$8 each), or the Team plan (US$21 per user) when roles and audit logs are needed |
| GitHub Actions | US$0 to US$17 | As in A |
| Outbound traffic | €0 | Still under 100 GB: notifications are small, and push goes through Firebase |
| **Total** | **about €175 to €350**, plus the weather plan | US dollars converted at about €0.9 |

The range comes mostly from the choice of AI model, which the evaluation decides (#72).

## 4. Accuracy

| Estimate | Basis | Confidence |
|---|---|---|
| Azure prices | Official retail prices API, `westeurope`, pay-as-you-go | High. Prices change rarely; reservations would lower them, but they do not apply to the student credit. |
| GitHub Actions minutes | Measured on the repository over one week | Medium. A setup-heavy week, so probably high. Recomputed at each sprint review (section 5). |
| AI tokens per call | Typical ticket and prompt sizes | Medium. Replaced by the token counts of the evaluation runs (#72), which also measure latency and cost per ticket. |
| Volumes in scenario B | Assumptions, not measurements | Low. A planning range, not a forecast. |
| Doppler free seats | Official pricing page, which also lists 25 users for the plan | Medium. Checked before adding a fourth seat. |

## 5. How the costs are monitored

- **Azure:** a budget alert on the subscription at 50%, 75% and 90% of the credit, sent to the Scrum Master. Azure for Students also shows the remaining credit in the portal.
- **GitHub Actions:** the account's usage page, checked at each sprint review. Also a budget on the owner's account, at the amount the team agrees.
- **AI provider:** spend per call is logged with the trace identifier (NFR-011). The operational dashboard (#71) shows calls and estimated cost per day. Rate limits per user and per organization cap the worst case.
- **Each sprint review** compares the month's real costs with this estimate, and this document is updated when they differ by more than 20%.

## 6. Decisions to take

1. **Deallocate the VM outside working hours.** For example, an Azure auto-shutdown at 20:00 and a manual start, or a scheduled start before demos. It halves the Azure cost and keeps the credit for load tests.
2. **GitHub Actions minutes.** The current pace exceeds what the plan includes. One of these:
   - make the repository public, which makes Actions free, if the course allows it;
   - set a small budget (for example US$20 a month) on the owner's account;
   - cut minutes: cancel superseded runs, which pull requests already do, and skip unchanged apps, which is already done. The release on `main` repeats the checks by design ([ADR 0011](adr/0011-ci-pipeline-and-vulnerability-policy.md)), and that is where most of the minutes go.
3. **AI model.** Start with the cheapest model that reads PDFs, and move up only if the evaluation (#72) shows its accuracy is not enough.
