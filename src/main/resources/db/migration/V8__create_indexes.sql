-- V8: performance indexes
--
-- Indexes speed up reads (WHERE / JOIN / ORDER BY) but cost you on every
-- INSERT/UPDATE/DELETE (the index must be maintained). So index what you QUERY by,
-- not everything.
--
-- ALREADY INDEXED FOR FREE (do NOT duplicate these):
--   * Every PRIMARY KEY            -> users.id, books.id, loans.id, ...
--   * Every UNIQUE constraint      -> users.username, users.email, books.isbn,
--                                     reviews(user_id, book_id), wishlists(user_id, book_id)
--
-- THE GOTCHA: Postgres auto-indexes the REFERENCED pk, but NOT the REFERENCING
-- fk column. So a plain fk like loans.borrower_id is currently UNINDEXED — every
-- join/lookup on it is a sequential scan.
--
-- TODO(human): decide which of these to index, and why. Then write the
-- CREATE INDEX statements below. For each, ask: do we filter/join/sort on it,
-- and is it NOT already covered by a PK/UNIQUE above?
--
--   loans.status          -- filter "show me ACTIVE / OVERDUE loans"?
--   loans.due_date        -- sort/range "loans due before X" (overdue sweep)?
--   loans.lender_id       -- fk, "loans where I'm the lender"?
--   loans.borrower_id     -- fk, "loans where I'm the borrower"?
--   loans.book_copy_id    -- fk, "loan history for this copy"?
--   reviews.book_id       -- fk, "all reviews for this work" (aggregate ratings)?
--   reviews.user_id       -- already covered by UNIQUE(user_id, book_id)? think about it
--   book_copies.book_id   -- fk, "all copies of this work" (the 🟢 easy query)?
--   book_copies.owner_id  -- fk, "all copies I own"?
--   books.author          -- filter/search "books by author X"?
--
-- Naming convention: idx_<table>_<column>(s)
-- e.g. CREATE INDEX idx_loans_status ON loans(status);

-- TODO(human): write your CREATE INDEX statements here.
CREATE INDEX idx_loans_status ON loans(status);
CREATE INDEX idx_loans_due_date ON loans(due_date);
CREATE INDEX idx_reviews_book_id ON reviews(book_id);
CREATE INDEX idx_book_copies_book_id ON book_copies(book_id);
CREATE INDEX idx_books_copies_owner_id ON book_copies(owner_id);
CREATE INDEX idx_books_author ON books(author);
CREATE INDEX idx_loans_lender_id ON loans(lender_id);
CREATE INDEX idx_loans_borrower_id ON loans(borrower_id);