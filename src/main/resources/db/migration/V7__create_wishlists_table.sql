-- V7: wishlists table
--
-- A TRUE join table for User <-> Book (the WORK you want), Many-to-Many.
-- Unlike `loans`, this link has no state/lifecycle — just existence + when it
-- was added. So it stays a plain join table (not a promoted entity).
--
-- Decisions locked in:
--   * id         UUID primary key, app-generated (surrogate, for JPA/consistency)
--   * user_id    UUID FK -> users(id), NOT NULL
--   * book_id    UUID FK -> books(id),  NOT NULL   (you wishlist a work, not a copy)
--   * created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()  (no updated_at — the row never changes)
--   * UNIQUE (user_id, book_id)  -- composite: can't wishlist the same work twice
--
-- TODO(human), decide for EACH foreign key:
--   * user_id ON DELETE ?  — user deleted -> their wishlist entries?
--   * book_id ON DELETE ?  — work deleted -> wishlist entries pointing at it?

-- TODO(human): write the CREATE TABLE wishlists (...) statement.

CREATE TABLE wishlists(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    book_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_id
        FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_book_id
        FOREIGN KEY (book_id)
        REFERENCES books(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_wishlist_user_book
        UNIQUE (user_id, book_id)
);