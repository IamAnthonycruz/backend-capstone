package com.readshelf.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.readshelf.utils.PagedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Read side of the search index. The write side is SearchIndexConsumer; nothing here ever
 * modifies a document.
 *
 * Uses ElasticsearchOperations rather than BookSearchRepository because derived query methods
 * can't express what this endpoint needs — per-field boosts, fuzziness, and highlighting are
 * query-shape concerns, and there is no method-name syntax for them. The repository stays for
 * the by-id save/delete the consumer does.
 */
@Service
public class BookSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private static final Logger log = LoggerFactory.getLogger(BookSearchService.class);
    public BookSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * TODO(human): make this DEGRADE instead of failing when Elasticsearch is unreachable.
     *
     * Wrap the search call: on failure, log a warning and return an empty result rather than
     * letting the exception reach the advice as a 500. The rest of the app runs off Postgres and
     * is unaffected — a dead search box shouldn't take the page down with it. Timeouts are
     * already bounded in application.yml, so the failure arrives in ~2s rather than 30.
     *
     * Note the contrast with SearchIndexConsumer.reindex, which must NOT do this: swallowing
     * there ACKs the message and destroys the event, leaving the index permanently stale. Here
     * nothing is lost — the books are still in Postgres and the user can search again.
     *
     * The decision to make is WHICH exception you catch. Spring Data translates Elasticsearch
     * failures into org.springframework.dao.DataAccessException subclasses, so that's the broad
     * net. But "broad" cuts both ways: a malformed query is a BUG in buildQuery, and a catch
     * wide enough to hide it would return empty results forever with nobody the wiser. Pick your
     * level, and make sure whatever you log says enough to tell the two apart at 3am.
     */
    public PagedResponse<SearchResultDTO> search(String q, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        try {
            SearchHits<BookDocument> hits = elasticsearchOperations.search(
                    buildQuery(q, pageable), BookDocument.class);

            List<SearchResultDTO> results = hits.getSearchHits().stream()
                    .map(BookSearchService::toResultDTO)
                    .toList();

            int totalPages = (int) Math.ceil((double) hits.getTotalHits() / size);
            return new PagedResponse<>(
                    new PagedResponse.Metadata(page, size, totalPages, hits.getTotalHits()),
                    results);

        } catch (DataAccessResourceFailureException e) {
            // Triggered when Elasticsearch is unreachable, timed out, or connection refused
            log.warn("Elasticsearch unreachable during search for query='{}'. Returning empty fallback response. Exception: {}",
                    q, e.getMostSpecificCause().getMessage());

            return new PagedResponse<>(
                    new PagedResponse.Metadata(page, size, 0, 0L),
                    Collections.emptyList());
        }
    }

    /** Fields the text query runs against, each with its relevance boost. */
    private static final List<String> SEARCHABLE_FIELDS =
            List.of("title^3", "author^2", "summary", "reviews^0.5");

    /**
     * Builds the query: one fuzzy multi_match across the boosted text fields, paged, with
     * highlighting on the same fields.
     *
     * BOOSTS encode the ranking decided in Phase 12: a title match is the strongest signal that
     * this is the book you meant, an author match nearly as strong, the blurb weaker, and a
     * review weakest of all — a review saying "reminds me of Dostoevsky" should surface that book
     * only when nothing better matched, never above Crime and Punishment.
     *
     * genre is absent on purpose. It's mapped as Keyword, so it is never analyzed and a free-text
     * `q` could only ever match it by being byte-identical to the whole genre string.
     *
     * FUZZINESS "AUTO" scales the allowed edit distance with term length (0 edits under 3 chars,
     * 1 under 6, 2 beyond) — a fixed 2 would let "cat" match "bat", "hat" and "car" equally.
     */
    private NativeQuery buildQuery(String q, Pageable pageable) {
        Query multiMatchQuery = Query.of(qBuilder -> qBuilder.multiMatch(
                m -> m.query(q)
                        .fields(SEARCHABLE_FIELDS)
                        .fuzziness("AUTO")
        ));

        // Highlight the same fields that were searched, minus the boost suffixes — "title^3" is
        // query syntax, not a field name, so the highlighter wouldn't recognise it.
        List<HighlightField> highlightFields = SEARCHABLE_FIELDS.stream()
                .map(field -> new HighlightField(field.split("\\^")[0]))
                .toList();

        return NativeQuery.builder()
                .withQuery(multiMatchQuery)
                .withPageable(pageable)
                .withHighlightQuery(new HighlightQuery(new Highlight(highlightFields), BookDocument.class))
                .build();
    }

    /**
     * SearchHit wraps the document with the per-query metadata: the relevance score Elasticsearch
     * computed and the highlight snippets it generated. getContent() is the BookDocument itself.
     */
    private static SearchResultDTO toResultDTO(SearchHit<BookDocument> hit) {
        BookDocument document = hit.getContent();
        return new SearchResultDTO(
                document.getId(),
                document.getTitle(),
                document.getAuthor(),
                document.getGenre(),
                hit.getScore(),
                hit.getHighlightFields());
    }
}
