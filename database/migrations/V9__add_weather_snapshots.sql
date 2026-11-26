-- What Jobs last fetched from IPMA for a district: its warnings or its
-- forecast, as JSON (Asynchronous Flows, weather warning flow). Jobs stores a
-- snapshot only when the content differs from the district's latest one, and
-- publishes WeatherUpdated for it; published_at stays null until RabbitMQ
-- confirmed the message, so a snapshot whose message was lost is sent again.
CREATE TYPE weather_snapshot_kind AS ENUM ('WARNINGS', 'FORECAST');
CREATE TABLE weather_snapshot (
  id uuid PRIMARY KEY,
  district text NOT NULL,
  kind weather_snapshot_kind NOT NULL,
  content jsonb NOT NULL,
  fetched_at timestamptz NOT NULL,
  published_at timestamptz
);
CREATE INDEX weather_snapshot_latest ON weather_snapshot (district, kind, fetched_at DESC);
CREATE INDEX weather_snapshot_unpublished ON weather_snapshot (fetched_at)
  WHERE published_at IS NULL;

-- IPMA's description of a warning, such as "Rajadas até 90 km/h.". Nullable:
-- IPMA does not always give one, and the previous release does not write it
-- (README, rule 4).
ALTER TABLE weather_warning ADD COLUMN description text;
