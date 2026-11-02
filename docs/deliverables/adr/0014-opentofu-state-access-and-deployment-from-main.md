# ADR 0014: OpenTofu, its state and access, and the deployment from main

- Status: accepted
- Date: 2026-11-01
- Deciders: Crowded team
- Amends: [ADR 0011](0011-ci-pipeline-and-vulnerability-policy.md) (how `main` runs the app workflows)

## Context

[ADR 0013](0013-single-azure-vm-docker-swarm-with-terraform.md) puts production on one Azure VM with Docker Swarm, provisioned as code, and leaves to [#101](https://github.com/andrereis26/crowded-labdsof/issues/101) how the pipeline reaches Azure and the VM. The environment must be reproducible (NFR-016), every merge to `main` must reach it without manual steps (NFR-014), and credentials must stay out of the repository (SR-008). The images come from four app workflows ([ADR 0011](0011-ci-pipeline-and-vulnerability-policy.md)) and the deploy and rollback scripts from [`deployment/`](../../../deployment/README.md). The subscription is Azure for Students: one owner, US$100 of credit, and a tenant where creating app registrations may not be allowed.

## Options considered

- **Tool:** Terraform; OpenTofu.
- **State:** local file; Terraform Cloud; an Azure Storage blob, with or without OpenTofu's state encryption.
- **Azure sign-in from GitHub Actions:** service principal with a client secret; OIDC with an app registration; OIDC with a user-assigned managed identity.
- **Reaching the VM:** SSH open to the internet; SSH from fixed addresses; SSH opened to the runner's address while the job runs; Azure Run Command.
- **Pipeline on `main`:** a deploy workflow started by each app workflow (`workflow_run`); one release workflow that calls the app workflows.
- **Approval of `tofu apply`:** required reviewers on the GitHub environment; none.
- **Region and size:** ADR 0013's `westeurope` and B2s; a region and size the subscription allows.
- **TLS:** now, with a certificate for the Azure DNS name; later.

## Decision

### OpenTofu

**OpenTofu** (1.13), with the `hashicorp/azurerm` provider (5.x), in [`infra/`](../../../infra/README.md). It reads the same HCL as Terraform, is open source under the MPL (Terraform's licence is no longer open source), and encrypts its state on the client, which Terraform does not. The provider versions are locked in `infra/.terraform.lock.hcl`, with hashes for Linux, macOS and Windows.

### State

The state is the record of which Azure resources OpenTofu created. It lives in a blob of a storage account that `infra/bootstrap.sh` creates once, outside the configuration it serves:

- the blob's **lease** locks the state while `apply` runs;
- **OpenTofu's state encryption** (AES-GCM, key derived with PBKDF2 from a passphrase in the `TOFU_STATE_PASSPHRASE` secret) encrypts the state and saved plans before they leave the machine, and refuses unencrypted ones;
- the account accepts only Microsoft Entra ID sign-in (no shared keys), TLS 1.2, no public access, and keeps earlier versions for 30 days.

Rejected: a local file (one person's laptop, no lock); Terraform Cloud (another account, and the state leaves Azure).

### Azure sign-in: OIDC with managed identities

GitHub Actions signs in with **OIDC** as one of two **user-assigned managed identities**, with federated credentials and no secret:

| Identity | Federated subject | Roles | Used by |
|---|---|---|---|
| `crowded-github-deploy` | `repo:andrereis26/crowded-labdsof:environment:production` | Contributor on the subscription; Storage Blob Data Contributor on the state | The Release workflow on `main` |
| `crowded-github-plan` | `repo:andrereis26/crowded-labdsof:pull_request` | Reader on the subscription; Storage Blob Data Reader on the state | `tofu plan` in pull requests, without the lock |

Managed identities need no app registration, which a student tenant may forbid. Only jobs in the `production` environment, which only `main` can use, get write access. Rejected: a client secret (long-lived, rotated by hand).

### Reaching the VM: SSH opened just in time

The network security group opens the **router's port (80)** to the internet and **SSH (22)** only to the addresses in the `SSH_ALLOWED_CIDRS` variable (the team's, if any). GitHub-hosted runners have no fixed addresses, so the deploy job adds a rule for **its own address**, removes it at the end even when the job fails or is cancelled, and removes any rule a cancelled run left. SSH accepts keys only; the job's key is the `SSH_PRIVATE_KEY` secret, and the host key is pinned in `SSH_KNOWN_HOSTS`, never accepted on first use.

Rejected: SSH open to the internet (against SR-008's least exposure); Azure Run Command (output cut to 4 KB, and the Doppler token passed as a parameter Azure records).

### The first boot

The VM's cloud-init mounts the managed data disk at `/var/lib/docker` (images and volumes, formatted only when empty, so a replaced VM keeps the data) and runs `deployment/remote-deploy.sh --bootstrap-only`. The VM ignores later changes to its first-boot data, so editing the scripts never replaces it. The data disk cannot be destroyed by `apply` (`prevent_destroy`).

### Pipeline on `main`: one Release workflow

`release.yml` runs on every push to `main`. It calls the four app workflows (`workflow_call`; they no longer run on `push` themselves), then applies the infrastructure, then deploys:

1. **Apply**, in the `production` environment, with **no required reviewers**: the pull request already showed the plan and was approved, and releases need no manual step.
2. **Deploy**, unless the push pushed no image and did not change `deployment/` (a manual run always deploys). It opens SSH, copies `deployment/` to `/opt/crowded/deployment`, and runs `remote-deploy.sh` with every app's `<namespace>/crowded-<app>:main`. The host pins each to its digest, so only the services whose image changed restart, and an image built by a run that was cancelled before deploying is picked up by the next one.
3. **Check from outside**: `/healthz`, `/` and `/backoffice/` through the public IP. If the check fails, the job runs `remote-rollback.sh`. A failure inside `remote-deploy.sh` is already rolled back there.

The workflow runs one at a time and is never cancelled. In pull requests, `infrastructure.yml` runs `tofu fmt -check`, `tofu validate`, Trivy's misconfiguration scan and `tofu plan`, and posts the plan in the pull request. `Infrastructure result` joins the required checks of the `main` ruleset.

Rejected: `workflow_run` (a merge that changes several apps would deploy once per app workflow).

### Region, VM size and TLS

The subscription's policy allows only `switzerlandnorth`, `austriaeast`, `norwayeast`, `germanywestcentral` and `spaincentral`, and the B2s that ADR 0013 assumed is not offered in them. The environment is in **`spaincentral`**, the closest to Portugal, on a **`Standard_B2ls_v2`**: the current generation of the B2s, with the same 2 vCPU and 4 GB, burstable, at €0.0409 an hour (the B2s was €0.0422 in `westeurope`). Both are variables in `infra/`.

The router serves **plain HTTP on port 80** at the Azure DNS name `crowded-labdsof.spaincentral.cloudapp.azure.com`; TLS on 443, with a certificate for that name, is a later story.

## Consequences

- The environment can be recreated from `infra/` and the bootstrap script, in another subscription if the credit runs out; the README in `infra/` has the steps.
- No long-lived Azure credential exists. The secrets left are the state passphrase, the deploy key and Doppler's service token, all in GitHub's secret store.
- Anyone who can open a pull request in this repository can read the infrastructure and decrypt the state (the plan job gets the passphrase), but cannot change Azure: the plan identity is read-only.
- A replaced VM has a new host key: the first deployment after it fails until `SSH_KNOWN_HOSTS` is updated ([`infra/README.md`](../../../infra/README.md#a-new-vm)).
- Every merge to `main` that changes an app is in production minutes later, with no staging in between (ADR 0013).
- Until TLS is added, traffic, including sign-in, is not encrypted between the browser and the router.
