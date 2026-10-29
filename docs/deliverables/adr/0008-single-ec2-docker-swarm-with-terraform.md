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

There is **one deployed environment, production**, as the course professor advised: no development or staging environment. Every merge to `main` is deployed to it, and it is where each story is verified (DoD-10) and where the demos run, so it loads the demo data (`SEED_DEMO_DATA=true`, [ADR 0012](0012-database-migrations-with-flyway.md)). Doppler has one config, `prd`. Before a change reaches production it runs on the developer's machine, with its configuration in a git-ignored `.env`, and in CI, in throwaway containers; neither is a deployed environment. Environment-specific configuration (NFR-016) stays: the same images take every setting from the environment, so a second environment would need only another Doppler config and host, without a code change.

## Consequences

- One instance is a single point of failure; acceptable for the MVP.
- The 1 GB t3.micro is likely too small for the stack ([ADR 0006](0006-technology-stack.md)). This will be checked at the first deployment: a larger instance type, or another provider.
- The infrastructure can be recreated from code.
- With one environment, a change is first seen deployed in production. The checks on every pull request, the smoke test after each deployment and `deployment/remote-rollback.sh` make up for the missing staging.
