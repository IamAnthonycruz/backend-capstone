-- V15: carry the AMQP routing key on the outbox row.
--
-- Phase 11 gives the outbox a real destination: a topic exchange whose bindings match on a
-- dotted routing key (loan.#, book.#, #). Something has to turn "this is a LOAN_REQUESTED"
-- into "publish with key loan.requested".
--
-- Decision: store the key, don't derive it in the poller. Deriving means string-munging
-- event_type at publish time, which couples the DB's stored values to the wire protocol —
-- rename a constant in a refactor and every message silently reroutes to a key no binding
-- matches, and RabbitMQ DROPS unroutable messages with no error at all. Storing it puts the
-- routing decision at the producer, in the same transaction as the business write, where the
-- domain knowledge actually lives. This is the shape Debezium's outbox event router uses
-- (an aggregate/route column alongside the event type).
--
-- Two-step NOT NULL, same as V13: Postgres validates SET NOT NULL immediately with a full
-- scan and rolls the whole thing back if a single NULL survives — so the backfill MUST come
-- first. Existing rows predate routing entirely (they were "published" to a log line), so
-- they get a key derived from their event type; they're already processed and will never be
-- republished, but the column still has to hold for them.

-- Step 1: add nullable.
ALTER TABLE outbox ADD COLUMN routing_key TEXT;

-- Step 2: backfill existing rows. LOWER + replace '_' with '.' happens to be exactly the
-- transformation for the events that exist today (LOAN_REQUESTED -> loan.requested). This is
-- a ONE-TIME data fix for historical rows, which is a very different thing from making it the
-- permanent runtime rule — here the input set is known, finite, and already in the table.
UPDATE outbox SET routing_key = LOWER(REPLACE(event_type, '_', '.')) WHERE routing_key IS NULL;

-- Step 3: now that no NULLs remain, enforce it.
ALTER TABLE outbox ALTER COLUMN routing_key SET NOT NULL;
