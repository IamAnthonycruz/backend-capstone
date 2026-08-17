package com.readshelf.search;

import com.readshelf.book.BookRepository;
import com.readshelf.config.RabbitConfig;
import com.readshelf.event.BookCreatedEvent;
import com.readshelf.event.BookDeletedEvent;
import com.readshelf.event.BookUpdatedEvent;
import com.readshelf.event.ReviewCreatedEvent;
import com.readshelf.event.ReviewDeletedEvent;
import com.readshelf.event.ReviewUpdatedEvent;
import com.readshelf.review.Review;
import com.readshelf.review.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Keeps the "books" index in step with Postgres. Listens on readshelf.search.queue, which is
 * bound to BOTH "book.#" and "review.#" — a book document contains review text, so a review
 * changing makes a book document stale just as surely as the book changing does.
 *
 * Six event types, two operations. Five of them mean "this book's document no longer matches
 * Postgres, rebuild it" and one means "this book is gone, drop the document." The collapse
 * happens HERE rather than in the events, because "created and updated are the same thing to me"
 * is a fact about the indexer, not about what happened in the catalog.
 *
 * IDEMPOTENCY comes free: reindexing reads current state from Postgres and REPLACES the document
 * by id, so processing the same event twice — which at-least-once delivery guarantees will
 * happen eventually — lands on exactly the same result as processing it once.
 *
 * Failure policy is inherited from the shared listener container config: transient failures get
 * four attempts with backoff, then dead-letter. See EmailNotificationConsumer for the details.
 */
@Component
@RabbitListener(queues = RabbitConfig.SEARCH_QUEUE)
public class SearchIndexConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexConsumer.class);

    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
    private final BookSearchRepository bookSearchRepository;

    public SearchIndexConsumer(BookRepository bookRepository,
                               ReviewRepository reviewRepository,
                               BookSearchRepository bookSearchRepository) {
        this.bookRepository = bookRepository;
        this.reviewRepository = reviewRepository;
        this.bookSearchRepository = bookSearchRepository;
    }

    @RabbitHandler
    public void onBookCreated(BookCreatedEvent event) {
        reindex(event.bookId());
    }

    @RabbitHandler
    public void onBookUpdated(BookUpdatedEvent event) {
        reindex(event.bookId());
    }

    /**
     * The one event that is NOT a reindex. There is nothing left in Postgres to read, which is
     * exactly why BookDeletedEvent had to exist rather than letting the document age out.
     */
    @RabbitHandler
    public void onBookDeleted(BookDeletedEvent event) {
        bookSearchRepository.deleteById(BookDocument.documentId(event.bookId()));
        log.debug("Removed book {} from the search index", event.bookId());
    }

    @RabbitHandler
    public void onReviewCreated(ReviewCreatedEvent event) {
        reindex(event.bookId());
    }

    @RabbitHandler
    public void onReviewUpdated(ReviewUpdatedEvent event) {
        reindex(event.bookId());
    }

    @RabbitHandler
    public void onReviewDeleted(ReviewDeletedEvent event) {
        reindex(event.bookId());
    }

    /** Same reasoning as the email consumer: an event this queue doesn't care about isn't a failure. */
    @RabbitHandler(isDefault = true)
    public void onUnhandledEvent(Object payload) {
        log.debug("Ignoring event this consumer doesn't handle: {}", payload.getClass().getSimpleName());
    }

    /**
     * Rebuilds one book's document from current Postgres state and writes it to Elasticsearch.
     * Reads only — the event carries an id, never content, so what lands in the index is whatever
     * Postgres says right now rather than whatever was true when the event was published.
     */
    private void reindex(UUID bookId) {
        // A missing book is a RACE, not a failure: the row was deleted between this event being
        // published and this message being delivered, and BOOK_DELETED is already in the queue
        // behind it. Throwing would burn four retries on a row that is never coming back and then
        // dead-letter — a false alarm for an index that ends up correct either way.
        var book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            log.debug("Skipping reindex of book {}: already deleted", bookId);
            return;
        }

        var reviews = reviewRepository.findByBook_Id(bookId);

        BookDocument bookDocument = new BookDocument();
        bookDocument.setId(BookDocument.documentId(bookId));
        bookDocument.setTitle(book.getTitle());
        bookDocument.setAuthor(book.getAuthor());
        bookDocument.setGenre(book.getGenre());
        bookDocument.setSummary(book.getDescription());
        bookDocument.setReviews(reviews.stream().map(Review::getContent).toList());

        bookSearchRepository.save(bookDocument);
        log.debug("Reindexed book {} with {} review(s)", bookId, reviews.size());
    }
}
