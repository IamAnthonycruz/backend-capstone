-- V4: book_copies table
--
-- The physical copy a user owns and lends out. This is where ownership and
-- availability live (Model A: `books` is the catalog/work, `book_copies` is the copy).
--
--   User  1--* book_copies   (a user owns many copies)   owner_id -> users(id)
--   Book  1--* book_copies   (a work has many copies)     book_id  -> books(id)
--   Loan  *--1 book_copies   (loans reference a copy)     [built in a later migration]
--
-- Decisions locked in:
--   * id            UUID primary key, app-generated
--   * book_id       UUID FK -> books(id), NOT NULL
--   * owner_id      UUID FK -> users(id), NOT NULL
--   * is_available  BOOLEAN NOT NULL DEFAULT TRUE  (kept in sync by loan logic, Phase 7)
--   * created_at / updated_at: TIMESTAMPTZ + DEFAULT NOW()
--
-- TODO(human), decide for EACH foreign key:
--   * book_id  ON DELETE ?  — if a catalog work is removed, what happens to its copies?
--   * owner_id ON DELETE ?  — if a user is deleted, what happens to the copies they own?
--
-- Worth a thought (no need to add it): can one user own TWO copies of the same edition?
--   If yes -> no UNIQUE(book_id, owner_id). If no -> add it.

-- TODO(human): write the CREATE TABLE book_copies (...) statement.
CREATE TABLE book_copies(
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_book_id
            FOREIGN KEY(book_id)
            REFERENCES books(id)
            ON DELETE CASCADE,
    CONSTRAINT fk_owner_id
            FOREIGN KEY (owner_id)
            REFERENCES users(id)
            ON DELETE NO ACTION


);
