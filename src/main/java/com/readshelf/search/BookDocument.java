package com.readshelf.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * The Elasticsearch view of a book. NOT the {@link com.readshelf.book.Book} entity — a separate
 * class on purpose, because the two are shaped by different questions.
 *
 * The entity is shaped by "what is true about this row, normalized." This document is shaped by
 * "what do I want back from a search, in one hit." Elasticsearch has no joins across indexes, so
 * the reviews of a book are DENORMALIZED into the book document: a query for a phrase that only
 * appears in a review still returns the BOOK, and the whole thing is scored in one pass instead
 * of two queries whose relevance scores can't be meaningfully combined.
 *
 * The cost of that copy is that it goes stale. Nothing here is the source of truth — Postgres is.
 * SearchIndexConsumer rebuilds a document from Postgres whenever a book or one of its reviews
 * changes, which is why every field below is a plain snapshot with no lifecycle of its own.
 *
 * `id` is a String rather than a UUID because it maps to Elasticsearch's `_id`, which is always
 * a string. Keeping the entity's UUID as its text form means a document id round-trips to the
 * book it came from with no separate lookup table.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "books")
public class BookDocument {

    @Id
    private String id;

    // FIELD TYPES decide which queries are possible at all, so they're chosen per field here
    // rather than left to Elasticsearch's dynamic mapping.
    //
    //   Text    — ANALYZED at index time: "Crime and Punishment" becomes [crime, and, punishment],
    //             lowercased. Matches single words out of order and supports fuzziness. The
    //             original string is gone, so it can't be sorted, filtered exactly, or aggregated.
    //   Keyword — stored VERBATIM as one token. Exact match only, but it's what sorting,
    //             filtering and faceting need.
    //
    // title is BOTH: analyzed for searching, plus a `title.raw` keyword sub-field so results can
    // be sorted alphabetically. genre is Keyword alone because it's a filter picked from a fixed
    // list, not something typed into the search box.
    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = {
                    @InnerField(suffix = "raw", type = FieldType.Keyword)
            }
    )
    private String title;

    @Field(type = FieldType.Text)
    private String author;

    /**
     * The book's blurb. Named `summary` here rather than `description` to match the public API
     * contract (BookRequestDTO.summary), not the column name Phase 9's rename landed on.
     */
    @Field(type = FieldType.Text)
    private String summary;

    @Field(type = FieldType.Keyword)
    private String genre;

    /**
     * Denormalized review bodies — just the text, no ids, no authors. A List of strings on a Text
     * field indexes each element separately, so a match inside any one review matches the book.
     *
     * Deliberately not a list of nested review objects: nothing here needs to know WHICH review
     * matched, only that this book did. If per-review structure is ever needed (e.g. "show me
     * which review hit"), that's FieldType.Nested, which is a real cost — Elasticsearch stores
     * each nested element as its own hidden document.
     */
    @Field(type = FieldType.Text)
    private List<String> reviews;

    /** Single place that knows a document id is just the book's UUID in text form. */
    public static String documentId(UUID bookId) {
        return bookId.toString();
    }
}
