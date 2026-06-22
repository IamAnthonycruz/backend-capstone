-- V5: reviews table
--
-- A user's review of a WORK (not a copy) — so reviews aggregate across all copies
-- of the same book (Model A). Two FKs:
--   User 1--* Review   user_id -> users(id)   (the author)
--   Book 1--* Review   book_id -> books(id)   (the work being reviewed)
--
-- Decisions locked in:
--   * id        UUID primary key, app-generated
--   * user_id   UUID FK -> users(id), NOT NULL
--   * book_id   UUID FK -> books(id),  NOT NULL
--   * rating    INT NOT NULL, CHECK (rating BETWEEN 1 AND 5)
--   * content   review text (decide: nullable? rating-only reviews allowed?)
--   * created_at / updated_at: TIMESTAMPTZ + DEFAULT NOW()
--   * UNIQUE (user_id, book_id)  -- composite: one review per user per work (DB backstop)
--
-- TODO(human), decide for EACH foreign key:
--   * user_id ON DELETE ?  — user deleted -> what happens to their reviews?
--   * book_id ON DELETE ?  — work deleted -> what happens to its reviews?

-- TODO(human): write the CREATE TABLE reviews (...) statement.

CREATE TABLE reviews(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    book_id UUID NOT NULL,
    rating INT NOT NULL CONSTRAINT check_valid_rating CHECK(rating >= 1 AND rating <= 5),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_book
        FOREIGN KEY (book_id)
        REFERENCES books(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_user_book
        UNIQUE (user_id, book_id)


);
