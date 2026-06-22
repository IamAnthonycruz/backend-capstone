-- V3: books table
--
-- User -> Book is ONE-TO-MANY (a user owns many books). Mechanically this is the
-- same FK as V2, just WITHOUT the UNIQUE constraint on user_id.
--
-- Decisions locked in:
--   * id            UUID primary key, app-generated
--   * user_id       UUID FK -> users(id), NOT NULL, NO unique (that's what makes it 1-to-many)
--                   -> pick an ON DELETE action: if an owner is deleted, what happens to their books?
--   * isbn          unique (README). Think about type/length: ISBN-13 is 13 chars.
--   * is_available  boolean, stored availability flag (kept in sync by loan logic in Phase 7)
--   * book fields    title, author, summary, genre, ... your call on which are NOT NULL
--   * created_at / updated_at: TIMESTAMPTZ + DEFAULT NOW()
--
-- NOTE: indexes (isbn unique, author) are deferred to V7 per the build plan,
--       EXCEPT a UNIQUE constraint creates its own index implicitly — decide whether
--       isbn uniqueness belongs here as a column constraint or in V7. Either is fine;
--       just be consistent.

-- TODO(human): write the CREATE TABLE books (...) statement.

CREATE TABLE books (
    id UUID PRIMARY KEY,
    isbn VARCHAR(17) UNIQUE NOT NULL,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    genre TEXT,
    summary VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


