package com.readshelf.loan;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset cursor for loan pagination: the (due_date, id) of the last row
 * seen, packed into a URL-safe base64 token so the internal sort key stays
 * hidden behind the API boundary.
 */
public record LoanCursor(Instant dueDate, UUID id) {

    // TODO(human): pack this cursor into a URL-safe base64 token.
    //   1. Build a delimited string, e.g. dueDate + "<delim>" + id
    //      (pick a delim that can't appear in Instant.toString() or UUID.toString()).
    //   2. Turn it into bytes (getBytes), then base64-encode with the URL-safe encoder.
    //      See Base64.getUrlEncoder().
    public String encode() {
        String rawStr = dueDate + "#" + id;
        // 2. Convert to bytes and encode to a URL-safe Base64 string
        return Base64.getUrlEncoder().encodeToString(rawStr.getBytes(StandardCharsets.UTF_8));
    }

    // TODO(human): reverse of encode().
    //   1. Base64-decode the token (URL-safe decoder) back to a string.
    //   2. Split on your delimiter.
    //   3. Instant.parse(...) the first part, UUID.fromString(...) the second.
    //   4. Return new LoanCursor(dueDate, id).
    public static LoanCursor decode(String token) {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(token);
        String decodedStr = new String(decodedBytes, StandardCharsets.UTF_8);

        // 2. Split on the delimiter
        String[] tokens = decodedStr.split("#");

        // 3. Parse the fields back into their respective types
        Instant dueDate = Instant.parse(tokens[0]);
        UUID id = UUID.fromString(tokens[1]);

        // 4. Return the new cursor object
        return new LoanCursor(dueDate, id);
    }
}
