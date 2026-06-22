package com.readshelf.utils;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Keyset-pagination envelope. Unlike {@link PagedResponse}, there are no page
 * numbers or totals — keyset paging can't cheaply know how many rows lie ahead.
 * Instead the client echoes {@code nextCursor} back to fetch the next page.
 * A null {@code nextCursor} means "you've reached the end" and is omitted from JSON.
 */
public record CursorPage<T>(
        List<T> records,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor
) {
}
