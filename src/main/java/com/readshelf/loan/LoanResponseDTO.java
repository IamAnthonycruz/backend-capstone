package com.readshelf.loan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.readshelf.utils.Link;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LoanResponseDTO(
        UUID id,
        UUID lenderId,
        UUID borrowerId,
        UUID bookCopyId,
        String status,
        Instant requestDate,
        Instant approvalDate,
        Instant dueDate,
        Instant returnDate,
        // HAL-style hypermedia links, keyed by relation. Omitted from JSON when not set
        // (e.g. the mapper builds the DTO with null; the controller attaches links).
        @JsonProperty("_links")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, Link> links
) {
    /** Returns a copy of this DTO with the given links attached (records are immutable). */
    public LoanResponseDTO withLinks(Map<String, Link> links) {
        return new LoanResponseDTO(id, lenderId, borrowerId, bookCopyId, status,
                requestDate, approvalDate, dueDate, returnDate, links);
    }
}