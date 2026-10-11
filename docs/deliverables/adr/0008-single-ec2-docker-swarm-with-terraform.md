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

## Consequences

- One instance is a single point of failure; acceptable for the MVP.
- The 1 GB t3.micro is likely too small for the stack ([ADR 0006](0006-technology-stack.md)). This will be checked at the first deployment: a larger instance type, or another provider.
- The infrastructure can be recreated from code.
