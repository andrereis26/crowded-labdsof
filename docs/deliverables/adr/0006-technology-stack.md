# ADR 0006: Technology stack

- Status: accepted
- Date: 2026-10-11
- Deciders: Crowded team

## Context

We need a backend stack for three services, a participant app that runs on mobile and in desktop and mobile browsers (TR-007), and a web backoffice for organizers and administrators. The team's existing experience matters more than small performance differences.

## Options considered

- **Backend:** Java + Spring Boot; Node.js + NestJS; .NET; Python + FastAPI.
- **Participant app:** Flutter (mobile and web from one codebase); React Native (Expo) plus a separate web app; participant screens added to the React backoffice.
- **Backoffice:** React + Vite; Angular; Flutter Web.

## Decision

- **Backend: Java + Spring Boot.** It covers everything we need with mature libraries: Spring AMQP (RabbitMQ), Spring Data JPA (PostgreSQL), Spring Security (JWT), Micrometer (Prometheus metrics) and scheduling for Jobs. Team has experience with it.
  - Java 25 (JDK 25, the current LTS) and the latest stable Spring Boot release (4.1.2 when the services were created).
  - Maven as the build tool, through a committed wrapper (`mvnw`) in each service, because the team knows it better than Gradle.
- **Participant app: Flutter.** One codebase builds the Android, iOS and web versions, so screens, models and the API client are written once. Push on mobile uses `firebase_messaging` ([ADR 0010](0010-external-integrations.md)).
- **Backoffice: React + Vite + TailwindCSS.** Built as a static bundle served by the `web-app` container; many dashboard and chart libraries.

## Consequences

- Two languages on the client side (Dart, TypeScript) besides Java on the backend.
- Flutter Web is less suited to text-heavy or SEO pages; acceptable for the participant screens (wallet, event page, notifications).
- iOS push needs an Apple Developer account; without it, push is demonstrated on Android.
