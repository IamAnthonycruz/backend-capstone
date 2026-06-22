-- V6: loans table
--
-- The only stateful entity. Resolves users <-> book_copies as a many-to-many,
-- but is a first-class entity because it carries its own state (status + dates).
--
-- Three FKs:
--   lender_id     -> users(id)         the user who owns/lends the copy
--   borrower_id   -> users(id)         the user borrowing it   (two roles, same table)
--   book_copy_id  -> book_copies(id)   the specific physical copy on loan
--
-- Decisions locked in:
--   * id            UUID primary key, app-generated
--   * lender_id     UUID FK -> users(id),        NOT NULL (non-unique: a user lends many)
--   * borrower_id   UUID FK -> users(id),        NOT NULL (non-unique: a user borrows many)
--   * book_copy_id  UUID FK -> book_copies(id),  NOT NULL
--   * status        REQUESTED | APPROVED | ACTIVE | RETURNED | OVERDUE  (VARCHAR + CHECK)
--
-- TODO(human) — decisions to make as you write it:
--   1. The four dates. Which are NOT NULL vs nullable, and which get a DEFAULT?
--        request_date  -> set at creation
--        approval_date -> null until a lender approves
--        due_date      -> set when?
--        return_date   -> null until returned
--   2. ON DELETE for each FK. Remember the cascade chain from V4:
--        books --CASCADE--> book_copies --?--> loans
--      If book_copy_id is ON DELETE CASCADE, deleting a book silently erases loan
--      history. Do you want loans to BLOCK that (NO ACTION/RESTRICT) instead?
--      And lender_id / borrower_id: can you delete a user who has loan history?
--   3. `version` column for optimistic locking is a Phase 7 concern (don't pre-build).
--      Add it now, or introduce it via its own migration in Phase 7? Your call.

-- TODO(human): write the CREATE TABLE loans (...) statement.


CREATE TABLE loans(

    id UUID PRIMARY KEY,
    lender_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    book_copy_id UUID NOT NULL,
    status TEXT NOT NULL CONSTRAINT check_lend_state CHECK(status IN('REQUESTED', 'APPROVED', 'ACTIVE', 'RETURNED', 'OVERDUE')),
    request_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approval_date TIMESTAMPTZ,
    due_date TIMESTAMPTZ,
    return_date TIMESTAMPTZ,

    CONSTRAINT fk_lender_id
        FOREIGN KEY (lender_id)
        REFERENCES users(id)
        ON DELETE NO ACTION,
    CONSTRAINT fk_borrower_id
        FOREIGN KEY (borrower_id)
        REFERENCES users(id)
        ON DELETE NO ACTION,
    CONSTRAINT fk_book_copy_id
        FOREIGN KEY (book_copy_id)
        REFERENCES book_copies(id)
        ON DELETE NO ACTION

);
