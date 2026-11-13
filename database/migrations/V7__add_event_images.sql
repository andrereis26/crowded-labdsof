-- An event's image, at most one, uploaded by its organization (ADR 0019). Core
-- stores it without its metadata and serves it to whoever may see the event.
-- A new table, so the previous release is not affected (README, rule 4).
CREATE TABLE event_image (
  event_id uuid PRIMARY KEY REFERENCES event (id) ON DELETE CASCADE,
  media_type text NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
  content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880),
  updated_at timestamptz NOT NULL
);
