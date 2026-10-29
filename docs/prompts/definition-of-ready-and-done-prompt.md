# Prompt used to generate the Definition of Ready and Definition of Done document

We are working on a project called "Community Resilience and Everyday Services Platform", and my team is building Crowded, a platform that keeps participants informed about event changes and on-site conditions and helps organizations manage their audience. Based on the assignment and the existing documentation in `docs/` (especially the requirements, the product vision and the Responsible AI assessment), I need help writing a Definition of Ready and a Definition of Done that apply to every backlog item we track in GitHub Issues and that we will review at each sprint retrospective.

For the **Definition of Ready**, a user story should only enter a sprint when:

- it is written as a user story (*As a <persona>, I want <goal>, so that <benefit>*) and is linked to at least one requirement in `requirements.md`;
- it has clear and testable acceptance criteria, preferably in *Given / When / Then* format;
- it is small enough for one sprint (otherwise it is split), estimated in story points and has a MoSCoW priority agreed with the Product Owner;
- its dependencies (other stories, external services, simulators, the AI provider) are identified and do not block it;
- UI items have a sketch or mockup, and items involving personal data, authentication or AI identify the relevant security, privacy or AI evaluation concerns;
- the team understands it and agrees it can be delivered during sprint planning.

For the **Definition of Done**, group the criteria into code and review, testing, delivery and operation, and quality and documentation. It should cover:

- acceptance criteria verified, a pull request reviewed and approved by at least one other team member, commits following our convention (`type(#issue): message`), and code that follows the coding standards and passes static analysis;
- unit tests for new logic, integration or end-to-end tests when the item affects communication between components, external integrations or a principal user workflow, failure scenarios tested where relevant (unavailable external source, unavailable AI service, duplicate events), and an updated evaluation dataset for AI-assisted features;
- a passing CI/CD pipeline (build, tests, static analysis, dependency checks, container image), deployment to production, structured logs, metrics and trace identifiers, and no secrets in the repository;
- security and privacy requirements implemented, accessibility (WCAG 2.1 AA for the principal user journeys), updated API or architecture documentation and ADRs, acceptance by the Product Owner, and the issue closed with the board updated.

Present each list as a numbered table (DoR-01, DoR-02, … and DoD-01, DoD-02, …), with one short and verifiable criterion per row. Keep it concise and avoid criteria we cannot check in practice.

---

**Note:** The content generated from this prompt was reviewed and adjusted by the team before being considered final.
