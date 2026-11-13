-- Each event's own setup, chosen when its organization creates or edits it:
-- its entrances and queue points, named freely, and the rules of the
-- confirmation prompts (FR-057, FR-060, FR-064).
--
-- No release before this one writes or maps queue_time, so its kind and point
-- name can go now (README, rule 4). The new event columns have defaults, so the
-- previous release can still insert an event.

-- An entrance or another place where people queue, as its organization names
-- it: "Porta A", "Bilheteira", "Bar principal". position keeps the order in
-- which the organization lists them.
CREATE TABLE queue_point (
  id uuid PRIMARY KEY,
  event_id uuid NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  name text NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
  position integer NOT NULL CHECK (position >= 0),
  UNIQUE (event_id, name)
);

-- A waiting time belongs to one of the event's queue points instead of a
-- fixed kind of point. The existing times keep their point's name.
INSERT INTO queue_point (id, event_id, name, position)
  SELECT gen_random_uuid(), event_id, point_name,
         row_number() OVER (PARTITION BY event_id ORDER BY point_name) - 1
  FROM (SELECT DISTINCT event_id, point_name FROM queue_time) AS points;
ALTER TABLE queue_time ADD COLUMN queue_point_id uuid REFERENCES queue_point (id) ON DELETE CASCADE;
UPDATE queue_time SET queue_point_id = queue_point.id
  FROM queue_point
  WHERE queue_point.event_id = queue_time.event_id AND queue_point.name = queue_time.point_name;
ALTER TABLE queue_time
  ALTER COLUMN queue_point_id SET NOT NULL,
  DROP COLUMN kind,
  DROP COLUMN point_name,
  ADD CONSTRAINT queue_time_queue_point_id_key UNIQUE (queue_point_id);
DROP TYPE queue_point_kind;

-- How far from the venue, in meters, a participant may report or confirm: up to
-- a whole city for a public event spread over it (FR-064), how many
-- answers make a report's confidence high (FR-058), and how often one
-- participant is asked to confirm (FR-057).
ALTER TABLE event
  ADD COLUMN venue_radius_meters integer NOT NULL DEFAULT 500
    CHECK (venue_radius_meters BETWEEN 100 AND 50000),
  ADD COLUMN high_confidence_answers integer NOT NULL DEFAULT 8
    CHECK (high_confidence_answers BETWEEN 2 AND 50),
  ADD COLUMN max_prompts_per_hour integer NOT NULL DEFAULT 3
    CHECK (max_prompts_per_hour BETWEEN 1 AND 10);

-- A capacity is how many people the venue holds: at least one.
UPDATE event SET capacity = NULL WHERE capacity = 0;
ALTER TABLE event DROP CONSTRAINT event_capacity_check;
ALTER TABLE event ADD CONSTRAINT event_capacity_check CHECK (capacity > 0);
