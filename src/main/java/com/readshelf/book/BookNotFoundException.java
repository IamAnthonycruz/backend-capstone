package com.readshelf.book;

import java.util.UUID;

/**
 * Thrown when a book is looked up by id and doesn't exist.
 *
 * Design contract for every Phase 8 domain exception:
 *   - extends RuntimeException (unchecked — services shouldn't declare `throws`)
 *   - HTTP-AGNOSTIC: no HttpStatus, no ResponseStatusException. The @ControllerAdvice
 *     decides this maps to 404. The exception only knows the *domain* fact.
 *   - carries the context that caused it (here: the missing id) so the advice can
 *     build a useful `detail` message without the service pre-formatting a string.
 */
public class BookNotFoundException extends RuntimeException {

    private final UUID id;
    public BookNotFoundException(UUID id) {
        super(String.format("Book with id %s not found", id));
        this.id = id;
    }
}