# ADR 0019: Images in PostgreSQL, served by Core

- Status: accepted
- Date: 2026-11-13
- Deciders: Crowded team (Guilherme)

## Context

An organization adds an image to its event (FR-089), and a participant will attach a photo to a report (FR-090). Both are uploaded files of up to 5 MB that must be checked and cleaned before they are kept (SR-025), and each must reach only whoever may see what it belongs to: an event's image follows who may see the event (SR-001, [ADR 0005](0005-authentication-in-core-with-jwt.md)), a report's photo only the event's organization (SR-007).

Crowded has no file storage yet. Its only database is the shared PostgreSQL ([ADR 0002](0002-shared-database-with-table-ownership.md)), on the single Azure VM ([ADR 0013](0013-single-azure-vm-docker-swarm-with-terraform.md)).

## Options considered

- **PostgreSQL, in a table owned by Core:** the image's bytes in a `bytea` column, served by a Core endpoint. Nothing new to run, pay for or keep secret; deleted in the same transaction as what it belongs to; backed up with the rest of the data; the integration tests already run PostgreSQL. The database grows by the images' size, and every image goes through Core.
- **Azure Blob Storage, behind Core:** a private container on the Azure account we already use, Azurite locally and in the tests, an `infra/` change. Scales to many large files and could serve them through short-lived signed links, at the cost of a second store to provision, secure, back up and keep consistent with the database.
- **Firebase Storage:** Google's storage for apps, with its own client SDKs. A second cloud next to Azure; new buckets need the paid Blaze plan; and its security rules cannot apply Crowded's access rules, which live in Core's database, so Core would still sit in front of it.

## Decision

- **PostgreSQL, behind Core.** Each kind of image has its own table, owned by Core (`event_image`; `report_photo` with the reports), with the media type, the bytes and when it was stored.
- **Core checks and cleans every upload** before storing it: the type comes from the content, not from the name or the `Content-Type` header; only JPEG, PNG and WebP up to 5 MB are accepted; and the metadata (EXIF, XMP, text chunks, and so the location) is removed from the file's bytes, without decoding and encoding the image again. A JPEG keeps only its orientation, so a phone's photo is not shown sideways.
- **Core serves each image** after the same access check as what it belongs to (`EventAccess` for an event), privately cached and revalidated with an ETag. Clients fetch it with their token, since an image tag cannot send one.

## Consequences

- No new service, account, cost or secret, and nothing to provision in `infra/`.
- The database and its backups grow with the images: 5 MB at most per event, and a few hundred events in the MVP. Moving the bytes to Blob Storage later changes one adapter per table and a migration that copies them.
- Every image request goes through Core and the router. That is fine for a backoffice and an app that show a few images at a time; a public, high-traffic gallery would need a store that serves files directly.
- The metadata is removed by reading the file's structure, not by decoding the image: a file whose structure is broken is refused, but a well-formed file with a damaged picture inside is not detected.
