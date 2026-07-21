-- V13: make books.description NOT NULL, in two ordered steps.
--
-- Why two steps (and why the order is fixed):
--   Postgres validates SET NOT NULL immediately by scanning the table. If ANY row
--   still holds a NULL, the ALTER fails outright and rolls back. So every NULL must
--   be gone BEFORE the constraint is added — backfill first, constrain second.
--
-- Why this is "painful" at scale:
--   SET NOT NULL takes an ACCESS EXCLUSIVE lock and full-scans the table, blocking
--   reads and writes for the duration. On a large table the production-safe version
--   is a two-release dance: add a CHECK (description IS NOT NULL) NOT VALID (cheap,
--   no scan), then VALIDATE CONSTRAINT (scans under a weaker lock). Overkill here —
--   this table is small — but that's the real-world escape hatch.
--
-- Step 1 decision (yours): what should existing NULL descriptions become?
--   Options worth weighing:
--     * a placeholder string ('No description available') — honest, obviously a filler,
--       but now it's real data that looks like a real description to consumers
--     * an empty string ('') — satisfies NOT NULL, but arguably NULL-with-extra-steps;
--       your API can no longer distinguish "unset" from "deliberately blank"
--     * derive it from another column (e.g. title) — no fake data, but invents meaning
--   Note the tradeoff: NOT NULL removes "unknown" from the domain, so whatever you
--   pick becomes the permanent stand-in for it.

-- TODO(human): step 1 — backfill the NULL descriptions with your chosen value.

UPDATE books
    SET description = 'No description available'
    WHERE description IS NULL;



-- Step 2: now that no NULLs remain, the constraint can be validated.
ALTER TABLE books
    ALTER COLUMN description SET NOT NULL;