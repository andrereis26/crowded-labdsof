# ADR 0013: Deploy on a single Azure VM with Docker Swarm and OpenTofu

- Status: accepted
- Date: 2026-10-29
- Deciders: Crowded team
- Supersedes: [ADR 0008](0008-single-ec2-docker-swarm-with-terraform.md)

## Context

The assignment requires containerized, reproducible deployment with environment-specific configuration (NFR-016) and automated delivery (NFR-014), and secrets must stay out of the repository (SR-008). [ADR 0008](0008-single-ec2-docker-swarm-with-terraform.md) chose one EC2 instance on the AWS free student plan, but that plan only covers the 1 GB t3.micro, which is likely too small for the stack ([ADR 0006](0006-technology-stack.md)). The team has **Azure for Students**: US$100 of credit a year, with no credit card, which pays for a virtual machine large enough. Nothing had been deployed on AWS yet.

## Options considered

1. **Azure Kubernetes Service (AKS)**: the control plane has a free tier, but the nodes are paid and Kubernetes is heavy for one machine.
2. **Azure Container Apps**: managed containers without a VM, but it replaces the Swarm stack, its secrets and the deploy and rollback scripts in [`deployment/`](/deployment/README.md).
3. **Docker Compose on one VM**: simplest, but no rolling updates, health-based restarts or built-in secrets.
4. **Docker Swarm on one Azure VM**: the design of ADR 0008 on another provider. Rolling updates, health checks, restarts and secrets with almost no overhead; can add nodes later.

## Decision

Option 4. OpenTofu provisions, in one resource group, the virtual network and subnet, the network security group, a static public IP, the Linux (Ubuntu) virtual machine and a managed data disk. The VM size and the region are variables; the design assumes a **B2s** (2 vCPU, 4 GB), paid from the student credit. GitHub Actions builds the images, pushes them to **Docker Hub** and runs `docker stack deploy`. Secrets live in **Doppler** and are copied into Docker Swarm secrets at deploy time. NGINX is the only container that publishes ports. Docker Hub, Doppler, the Swarm stack and the scripts in `deployment/` do not change: they need only a Linux host with Docker, reachable over SSH.

| AWS (ADR 0008) | Azure |
|---|---|
| VPC and public subnet | Virtual network and subnet |
| Security group | Network security group |
| Elastic IP | Static public IP |
| EC2 t3.micro | Virtual machine, B2s |
| EBS volume | Managed data disk |

### Environments

The design has **three deployed environments**, each a copy of the deployment view (its own VM, Swarm stack and Doppler config) running the same images:

| Environment | Doppler config | Receives a release | Demo data | Purpose |
|---|---|---|---|---|
| Development | `dev` | Every merge to `main` | Yes | First deployment of every change, integration of the team's work |
| Staging | `stg` | A release tag on `main`: the same images, not rebuilt | Yes | Each story is verified here (DoD-10) before it reaches participants |
| Production | `prd` | Manual approval of that release (GitHub environment protection) | No | Real participants and organizations |

A release moves up only by changing which images an environment runs, never by building again, so what was verified in staging is what reaches production. The settings differ only in Doppler (NFR-016). Developers' machines (**local development**, configured from a git-ignored `.env`) and CI's throwaway containers are not deployed environments.

**In practice only production is deployed**, as the course professor advised: three VMs would use up the student credit, and three environments add nothing the evaluation can see. The other two are simulated by production:

- Every merge to `main` is deployed straight to production, which plays the three roles: there are no release tags or approvals yet, and only the `prd` Doppler config is used.
- Stories are verified there (DoD-10 says staging).
- Production loads the demo data (`SEED_DEMO_DATA=true`, [ADR 0012](0012-database-migrations-with-flyway.md)), as staging would, because the demos run on it. A real production would not.

Adding development and staging later needs another VM and Doppler config each, and the promotion steps in the workflow, without a code change.

## Consequences

- One VM is a single point of failure; acceptable for the MVP.
- The B2s is paid from the US$100 credit, which lasts a few months at most; the VM is stopped (deallocated) when it is not needed. Whether 4 GB is enough is checked at the first deployment.
- The infrastructure can be recreated from code, on another subscription if the credit runs out.
- While only production is deployed, a change is first seen deployed in production. The checks on every pull request, the smoke test after each deployment and `deployment/remote-rollback.sh` make up for the missing development and staging.
- How the workflow authenticates to Azure, where the infrastructure state lives and how it reaches the VM are settled in [#101](https://github.com/andrereis26/crowded-labdsof/issues/101).
