-- Publishing an official event change (#37): the refund information and the
-- organization's message for the participants, and the outbox that Core's
-- messages wait in until RabbitMQ has them (Asynchronous Flows, ADR 0003).

-- The refund information is free text, such as "pedir até 5 DEZ em ...", not
-- only a link. Expand, then contract (README): refund_info_url stays until a
-- later release drops it.
ALTER TABLE event_change ADD COLUMN refund_info text;
UPDATE event_change SET refund_info = refund_info_url WHERE refund_info_url IS NOT NULL;

ALTER TABLE event_change ADD COLUMN message text;

-- Core writes each outgoing message here in the same transaction as the change,
-- and a relay publishes it with id as its message id, then deletes it. Rows that
-- are still here have not reached RabbitMQ.
CREATE TABLE outbox (
  id uuid PRIMARY KEY,
  routing_key text NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL
);
CREATE INDEX outbox_created_at_idx ON outbox (created_at);
