-- V14: transactional outbox table
--
-- The producer half of the outbox pattern. Instead of writing the loan to Postgres
-- AND publishing to a broker (two systems, no shared transaction -> dual-write problem),
-- we insert an event row HERE, inside the same transaction as the loan. Loan + event
-- commit or roll back together. A separate poller reads this table and does the real
-- publishing (Phase 11); until then it just logs.
--
-- Columns:
--   id            UUID PK, app-generated (same convention as every other table)
--   event_type    what kind of event this is (e.g. 'LOAN_REQUESTED') -> lets the
--                 consumer route/deserialize without cracking open the payload
--   payload       the serialized message body (see TODO below for the type)
--   created_at    stamped at insert; the poller ORDERs BY this to replay events in
--                 the order they happened, and it doubles as "queued at" for debugging
--   processed_at  NULL = not yet published; a timestamp = when the poller shipped it.
--                 A nullable timestamp instead of a boolean: same yes/no info, but you
--                 also learn WHEN it published (latency debugging) for free.

-- TODO(human): choose the column type for `payload`.
--   The event body is a small JSON object ({ "loanId": "...", "borrowerId": "..." }).
--   Weigh two options:
--     * JSONB   — Postgres parses + validates the JSON, and you can later query INTO it
--                 (WHERE payload->>'borrowerId' = ...). Costs a parse on write.
--     * TEXT    — Postgres stores the string as-is, opaque. Cheaper write, but the DB
--                 can't validate or query the contents; it's just a blob to Postgres.
--   Which fits an outbox, where the payload is written by your app and only ever read
--   back out whole by your poller? Fill in the type below.

CREATE TABLE outbox(
    id UUID PRIMARY KEY,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);