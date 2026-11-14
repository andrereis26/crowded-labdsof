-- The W3C traceparent of the request that wrote the message. The relay sends
-- it with the message, so Notifications logs it under the same trace id as
-- Core. Nullable, so the previous release still writes rows (README, rule 4).
ALTER TABLE outbox ADD COLUMN trace_parent text;
