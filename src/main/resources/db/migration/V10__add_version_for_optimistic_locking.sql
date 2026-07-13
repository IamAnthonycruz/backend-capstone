-- V10: optimistic-locking version columns (Phase 7)
--
-- Optimistic locking = no DB lock held; instead every row carries a version number.
-- On UPDATE, JPA appends `WHERE id = ? AND version = ?` and bumps the version. If a
-- concurrent writer already bumped it, the WHERE matches 0 rows -> Hibernate raises
-- OptimisticLockingFailureException (which we'll surface as 409).
--
-- Added to the two entities that get raced:
--   * book_copies — two borrowers trying to pick up the SAME copy at once.
--   * loans       — two transitions on the same loan at once.
--
-- BIGINT NOT NULL DEFAULT 0 so existing rows start at version 0 and the @Version
-- long field maps cleanly.

ALTER TABLE book_copies ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE loans       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;