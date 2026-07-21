-- V11: EXPAND phase of renaming books.summary -> books.description.
--
-- Expand/contract (parallel-change) rename, step 1 of 2:
--   * This file adds `description` and backfills it from `summary`.
--   * `summary` stays in place so old code still works during a rolling deploy.
--   * The contract migration (drop `summary`) comes LATER, after the app is
--     deployed reading/writing `description`.
--
-- Decisions to make:
--   * Column type/length for `description`. `summary` is VARCHAR(200) — is that
--     still the right width for a column now named "description"? (See the TEXT vs
--     bounded discussion in the summary/policy memory.)
--   * Nullability. `summary` is nullable today. If `description` is NOT NULL, the
--     backfill has to guarantee no NULLs remain — which is exactly the two-step
--     dance of migration #5. Keep them separate: make `description` nullable here.
--   * The backfill: copy summary -> description for every existing row.

    ALTER TABLE books
    ADD COLUMN description VARCHAR(200) NULL ;

    UPDATE books
    SET description = summary;

