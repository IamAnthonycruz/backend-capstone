package com.readshelf.loan;

import java.util.UUID;

/**
 * Thrown when a borrow/approve targets a book copy that isn't available (already out
 * on loan). The @ControllerAdvice maps this to 409 Conflict.
 *
 * Lives in the loan package: it's a lending rule raised during a borrow attempt, not
 * something the book catalog is concerned with. Carries the copy id.
 */
public class BookAlreadyLentException extends RuntimeException {

    private final UUID bookCopyId;

    public BookAlreadyLentException(UUID bookCopyId) {
        super(String.format("Book copy %s is currently lent out and unavailable", bookCopyId));
        this.bookCopyId = bookCopyId;
    }
}
