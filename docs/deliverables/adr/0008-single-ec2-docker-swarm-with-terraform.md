# ADR 0008: Deploy on a single EC2 instance with Docker Swarm and Terraform

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

The assignment requires containerized, reproducible deployment with environment-specific configuration (NFR-016) and automated delivery (NFR-014). The budget is the AWS free student plan, and secrets must stay out of the repository (SR-008).

## Options considered

1. **Kubernetes (EKS or self-managed)**: industry standard, but EKS is not free and Kubernetes is heavy for one machine.
2. **Amazon ECS**: managed containers, but ties the setup to AWS and costs more than one instance.
3. **Docker Compose on one instance**: simplest, but no rolling updates, health-based restarts or built-in secrets.
4. **Docker Swarm on one EC2 instance**: rolling updates, health checks, restarts and secrets with almost no overhead; can add nodes later.

## Decision

Option 4. Terraform provisions the VPC, security group, Elastic IP, EC2 instance and EBS volume. GitHub Actions builds the images, pushes them to **Docker Hub** (we may not get push rights to the school organization's GitHub registry) and runs `docker stack deploy`. Secrets live in **Doppler** and are copied into Docker Swarm secrets at deploy time. NGINX is the only container that publishes ports.

### Environments

The design has **three deployed environments**, each a copy of the deployment view (its own instance, Swarm stack and Doppler config) running the same images:

| Environment | Doppler config | Receives a release | Demo data | Purpose |
|---|---|---|---|---|
| Development | `dev` | Every merge to `main` | Yes | First deployment of every change, integration of the team's work |
| Staging | `stg` | A release tag on `main`: the same images, not rebuilt | Yes | Each story is verified here (DoD-10) before it reaches participants |
| Production | `prd` | Manual approval of that release (GitHub environment protection) | No | Real participants and organizations |

A release moves up only by changing which images an environment runs, never by building again, so what was verified in staging is what reaches production. The settings differ only in Doppler (NFR-016). Developers' machines (**local development**, configured from a git-ignored `.env`) and CI's throwaway containers are not deployed environments.

**In practice only production is deployed**, as the course professor advised: three instances do not fit the AWS free student plan, and three environments add nothing the evaluation can see. The other two are simulated by production:

- Every merge to `main` is deployed straight to production, which plays the three roles: there are no release tags or approvals yet, and only the `prd` Doppler config is used.
- Stories are verified there (DoD-10 says staging).
- Production loads the demo data (`SEED_DEMO_DATA=true`, [ADR 0012](0012-database-migrations-with-flyway.md)), as staging would, because the demos run on it. A real production would not.

Adding development and staging later needs another instance and Doppler config each, and the promotion steps in the workflow, without a code change.

## Consequences

- One instance is a single point of failure; acceptable for the MVP.
- The 1 GB t3.micro is likely too small for the stack ([ADR 0006](0006-technology-stack.md)). This will be checked at the first deployment: a larger instance type, or another provider.
- The infrastructure can be recreated from code.
- While only production is deployed, a change is first seen deployed in production. The checks on every pull request, the smoke test after each deployment and `deployment/remote-rollback.sh` make up for the missing development and staging.
