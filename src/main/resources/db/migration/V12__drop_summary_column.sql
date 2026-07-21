-- V12: CONTRACT phase of renaming books.summary -> books.description.
--
-- Expand/contract (parallel-change) rename, step 2 of 2. Safe to run ONLY because
-- the application no longer references the `summary` column: the Book entity now
-- maps the `description` column, and the JPQL/mapper/service were updated in the
-- same code change. The DTO's JSON field is still called `summary`, but that is an
-- API-contract name that never touches this column — so dropping it is invisible
-- to API consumers.
--
-- In a real rolling deploy this file would ship in a LATER release than V11, after
-- the description-reading code is confirmed live on every instance. Here it runs at
-- the same startup, which is fine for a single-instance dev app.

ALTER TABLE books
    DROP COLUMN summary;
