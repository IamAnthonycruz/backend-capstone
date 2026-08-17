package com.readshelf.search;

import java.util.List;
import java.util.Map;

/**
 * One search hit, as the API returns it. Deliberately NOT BookDocument:
 *
 *   - `score` and `highlights` don't exist on the document at all. They're properties of THIS
 *     query against it — the same book scores differently for a different `q`.
 *   - `summary` and the full review text are omitted. A result list shows enough to pick a
 *     result; the client follows the id to GET /api/v1/books/{id} for the whole thing.
 *
 * `highlights` is keyed by field name because WHERE the match landed is the useful part: a hit
 * highlighted under "reviews" tells the user why a book they didn't recognise came back. Values
 * are snippets with the matched terms wrapped in <em> tags by Elasticsearch.
 */
public record SearchResultDTO(
        String id,
        String title,
        String author,
        String genre,
        float score,
        Map<String, List<String>> highlights
) {
}
