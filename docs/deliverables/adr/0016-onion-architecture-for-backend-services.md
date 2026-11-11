# ADR 0016: Onion architecture for the backend services

- Status: accepted
- Date: 2026-11-11
- Deciders: Crowded team
- Amends: [ADR 0006](0006-technology-stack.md) (adds MapStruct, and ArchUnit for the tests)

## Context

Core, Jobs and Notifications were empty skeletons until #37 added Core's first endpoint. Several stories now add code to the three services in parallel during Sprint 2. Without a shared structure, each service would grow its own layout, business rules would leak into controllers and JPA entities, and moving a developer between services would cost time.

The first version of #37 put a domain rule next to the web layer: the change planner threw an exception that carried an HTTP status, the JPA entities were also the domain model, and the API body was the domain's input. The review found this, and it showed how easily the layers mix when nothing checks them.

## Options considered

1. **Layered by technical type** (`controller`, `service`, `repository` packages at the top). Familiar, but every feature spreads across the whole service, and nothing stops the service layer from depending on JPA or the web.
2. **Onion architecture per domain module**, with domain-driven design. Every module (`event`, `user`, `audit`, ...) has a framework-free domain at its centre, an application layer around it and the adapters outside. Dependencies point inwards only, and a test checks this.
3. **Hexagonal architecture without layers inside the core.** Similar dependency rule, but less guidance on where use cases, commands and mappers go.

## Decision

Option 2, in every backend service. The rules are in the [Agent playbook](../../../AGENTS.md) (Backend architecture) and Core's [Code Layout](../../../core/README.md#code-layout).

- **Folders.** One package per domain under `com.crowded.<service>`, and `shared` for what several domains use. Inside each domain: `domain/{model,exception,service,repository}`, `application/{service,dto}`, `api/{controller,dto,mapper}` and `infrastructure/{persistence/{entity,repository,mapper},messaging,config}`.
- **Dependency rule.** The API and infrastructure depend on the application, which depends on the domain. Adapters never use each other. The domain uses no framework: no Spring, JPA, validation, Jackson or MapStruct.
- **DDD and SOLID.** Business rules live on the model or in domain services. Repositories are ports declared by the domain and implemented by infrastructure adapters (dependency inversion). Each adapter keeps its port's contract (Liskov). Use cases and ports stay small (single responsibility, interface segregation). Refusals are domain exceptions with an error `code`, which the API turns into RFC 9457 problems.
- **DTOs and mappers.** One class per request, response, command and result. MapStruct maps between the layers; its mappers are generated at compile time.
- **Enforcement.** Each service has an `OnionArchitectureTest` (ArchUnit) that fails the build on a dependency pointing outwards or a framework in the domain.

## Consequences

- A domain rule can be tested without Spring, a database or HTTP, and the persistence or the API can change without touching it.
- There are more classes: a JPA entity, a domain object and DTOs for the same concept, with a mapper between each pair. MapStruct generates the mapping code, and a target field left unmapped fails the build.
- New dependencies: MapStruct 1.6.3 (compile time) and ArchUnit 1.5.1 (tests only). The strict compiler needs `-Xlint:-processing` and the MapStruct options on the main compile only (Agent playbook, Pitfalls).
- Jobs and Notifications adopt the layout, the architecture test and the MapStruct setup with their first domain code.
- The backoffice and the participant app are not affected; they keep their own layouts.
