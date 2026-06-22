package com.readshelf.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic pagination envelope for collection endpoints. Owns its JSON contract
 * (unlike serializing Spring's Page directly), so the shape is stable across
 * Spring upgrades.
 *
 * Target shape:
 * {
 *   "_metadata": { "page": 5, "perPage": 20, "totalPages": 27, "totalCount": 521 },
 *   "records": [ ... ]
 * }
 *
 * HATEOAS "_links" are intentionally NOT here yet — that's the separate HATEOAS
 * step later in Phase 3 (links are HTTP/URL-aware and belong in the controller).
 */
public record PagedResponse<T>(
        @JsonProperty("_metadata")
        Metadata metadata,
        List<T> records

) {

    public record Metadata(
            int page,
            int perPage,
            int totalPages,
            Long totalCount
    ) {
    }

    /**
     * Build a PagedResponse from a Spring Page whose contents are already DTOs.
     * Spring's Page hands you everything you need:
     *   page.getContent(), getNumber(), getSize(), getTotalPages(), getTotalElements()
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        Metadata metadata = new Metadata( page.getNumber(), page.getSize(), page.getTotalPages(), page.getTotalElements());
        return new PagedResponse<>(metadata,  page.getContent());

    }
}
