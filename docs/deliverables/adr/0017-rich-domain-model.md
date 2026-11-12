# ADR 0017: Rich domain model in the backend services

- Status: accepted
- Date: 2026-11-12
- Deciders: Crowded team
- Amends: [ADR 0016](0016-onion-architecture-for-backend-services.md) (DDD and SOLID)

## Context

ADR 0016 put the domain at the centre of every backend service and said that business rules live on the model or in domain services. It did not say what a domain type looks like, and the first stories (#33, #59, #140) filled the domain with records:

- Entities with an identity (`UserAccount`, `Organization`, `EventChange`, `Notification`) are records. Their canonical constructor is public and checks nothing, so any combination of fields can be built, such as a participant with an organization. Their equality compares every field instead of the identity, and every field has a public accessor, the password hash included.
- `Event` is a class, but `Event.apply` copies a new state without checking it. Its rules (a cancelled event takes no change, the doors open before the start, a postponement moves the start later) sit in the static `ChangePlanner`, which computes the next `EventState` from outside.
- Rules sit in application services: the password length in `AuthService`, the email normalization in `AuthService` and `MemberAdministrationService`, and "nothing changes" in `EventChangeService`.
- Invariants such as a non-blank organization name, a valid email or a latitude between -90 and 90 exist only as Bean Validation on the API DTOs, so a job, a message consumer or a test builds an invalid domain object without noticing.

Two things led there. "Map between layers only with MapStruct" makes a record the easiest target, because MapStruct fills its canonical constructor. And the agents copy the code next to them, so every new story repeated the first one's shape. Converting a few types now is cheaper than converting every type later.

## Options considered

1. **Keep records and move the rules to domain services.** Least change, but the model stays a bag of data, nothing protects its invariants, and every caller must remember to call the right service.
2. **Rich domain model.** Entities are classes that protect their invariants and change only through methods named after the domain; value objects validate themselves; application services orchestrate.
3. **Rich model with a library** (jMolecules annotations and its ArchUnit rules). Adds a dependency to the domain, which ADR 0016 keeps framework-free.

## Decision

Option 2, in every backend service. The rules are in the [Agent playbook](../../../AGENTS.md) (Backend architecture, Rich domain model):

- **Entities and aggregate roots** are final classes with private fields, no setters and equality on their id. A private constructor; static factories named after the domain check every invariant, and `restore(...)` rebuilds a stored one. State changes are methods named after domain verbs that check their rule and throw a domain exception.
- **Accessors** exist only where a caller or a mapper needs one, named for what they mean: `turnOn()` and `turnOff()` rather than `setOn(boolean)`, `hasTemporaryPassword()` rather than `isMustChangePassword()`. No Lombok: it generates an accessor for every field.
- **Value objects** are records whose compact constructor validates and normalizes, so an invalid one cannot exist, with the behaviour about their value. A primitive becomes a value object as soon as something checks it.
- **Domain events and outgoing messages** stay immutable records.
- **Rules** live on the aggregate or a value object. A domain service holds only a rule that spans aggregates or needs a port. An application service authorizes, loads, calls domain methods, saves and runs the side effects. API Bean Validation stays as an early 400, but the domain checks the same rule.
- **Mapping.** MapStruct maps the domain → JPA direction from the getters. The JPA → domain direction of an entity is a `default` mapper method that calls `restore(...)`.
- **Existing types** are converted now, with this decision, so no story copies the old shape: `UserAccount`, `User`, `Organization`, `Event` (which absorbs `ChangePlanner`), `EventChange`, `AuditLogEntry` and `OutboxMessage` in Core, `Notification` and `OfficialChange` in Notifications. New value objects: `Email`, `Password`, `Schedule`, `Venue`, `Coordinates` and `Provenance`.
- **Enforcement.** Each service's `OnionArchitectureTest` fails the build when a `domain.model` type with an `id` is a record or has a public constructor or setter, and Core's when a class outside the domain builds a `RuleViolationException`.

## Consequences

- An invalid domain object cannot be built, and each rule has one place and one unit test without Spring.
- More classes (value objects) and some hand-written mapping in `default` methods.
- The architecture test catches an anemic entity or a rule thrown from an application service; whether a primitive should be a value object, and whether a rule sits on the right aggregate, is still the review's job.
- Some refusals move earlier or change code: an invalid email at registration is now `EMAIL_INVALID` from the domain, and a coordinate out of range is `COORDINATES_OUT_OF_RANGE`. The API's Bean Validation still answers first for requests through it.
