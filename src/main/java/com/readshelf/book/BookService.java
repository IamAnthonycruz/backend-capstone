package com.readshelf.book;

import com.readshelf.config.CacheConfig;
import com.readshelf.event.BookCreatedEvent;
import com.readshelf.event.BookDeletedEvent;
import com.readshelf.event.BookUpdatedEvent;
import com.readshelf.outbox.OutboxEvent;
import com.readshelf.outbox.OutboxRepository;
import com.readshelf.utils.PagedResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

/**
 * Business/data layer for books. The controller stays HTTP-only and delegates here.
 *
 * Convention: service methods take/return DTOs (they own the BookMapper + repository).
 *
 * Phase 11: create/update now open a transaction, because each pairs its entity write with an
 * outbox row and the two must commit together. Both use TransactionTemplate inside the method
 * rather than an annotation — see update() for why that matters next to the cache eviction.
 */
@Service
public class BookService {
    private static final String DEFAULT_DESCRIPTION = "No description for this book";
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    // Outbox collaborators, same shape as LoanService: the template scopes the transaction to the
    // write, the repo persists the event row, the mapper serializes the payload.
    private final TransactionTemplate transactionTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public BookService(BookRepository bookRepository,
                       BookMapper bookMapper,
                       TransactionTemplate transactionTemplate,
                       OutboxRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.bookRepository = bookRepository;
        this.bookMapper = bookMapper;
        this.transactionTemplate = transactionTemplate;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = CacheConfig.BOOKS, key = "#id")
    public BookResponseDTO getById(UUID id) {
        return bookRepository.findById(id)
                .map(bookMapper::toResponseDTO)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    // v2 detail: the query builds the DTO directly (no mapper) — aggregates can't come
    // from a single entity mapping.
    public Optional<BookDetailV2DTO> findDetailById(UUID id) {
        return bookRepository.findBookDetailById(id);
    }

    public BookResponseDTO create(BookRequestDTO request) {
        Book book = bookMapper.toEntity(request);
        book.setDescription(descriptionOrDefault(request.summary()));

        // Book + outbox row in ONE transaction. Without this they'd be two separate commits
        // (save() supplies its own), so a crash between them loses the event with the book
        // already persisted — the dual-write problem the outbox exists to remove.
        Book saved = transactionTemplate.execute(status -> {
            Book persisted = bookRepository.save(book);
            publishOutbox("BOOK_CREATED", "book.created", new BookCreatedEvent(persisted.getId()));
            return persisted;
        });

        return bookMapper.toResponseDTO(saved);
    }

    public PagedResponse<BookResponseDTO> findAll(int page, int size, BookSortField sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy.getProperty()).and(Sort.by("id")).ascending());
        return PagedResponse.from(bookRepository.findAll(pageable).map(bookMapper::toResponseDTO));
    }

    /**
     * The transaction is a TransactionTemplate INSIDE the method rather than an annotation on it,
     * and that is deliberate, because {@code @CacheEvict} is on this method too.
     *
     * {@code @CacheEvict} defaults to beforeInvocation=false, so it fires after the method returns.
     * Both annotations are advice on the same proxy call and Spring doesn't guarantee their
     * relative order, so with {@code @Transactional} the evict can run BEFORE the commit. In that window a
     * concurrent getById() misses the cache, reads the still-uncommitted (old) row, and repopulates
     * the cache with stale data — which then survives until the region TTL, because the eviction
     * already happened and nothing will fire another. Committing inside the body makes the ordering
     * explicit: new data is visible before the evict runs.
     */
    @CacheEvict(value = CacheConfig.BOOKS, key = "#id")
    public BookResponseDTO update(UUID id, BookRequestDTO request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
        book.setDescription(descriptionOrDefault(request.summary()));
        book.setIsbn(request.isbn());
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setGenre(request.genre());

        transactionTemplate.executeWithoutResult(status -> {
            bookRepository.save(book);
            publishOutbox("BOOK_UPDATED", "book.updated", new BookUpdatedEvent(book.getId()));
        });

        return bookMapper.toResponseDTO(book);
    }

    /**
     * Writes an outbox row. Must be called from inside a transaction that also contains the
     * business write — on its own it gives you nothing the pattern is for.
     */
    private void publishOutbox(String eventType, String routingKey, Object payload) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType(eventType);
        outboxEvent.setRoutingKey(routingKey);
        outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
        outboxRepository.save(outboxEvent);
    }

    /**
     * The API still allows `summary` to be omitted, but books.description is NOT NULL (V13),
     * so every write path funnels the incoming value through here first.
     *
     * @param requestedSummary the client-supplied summary — may be null
     * @return a non-null description safe to write to the entity
     */
    private String descriptionOrDefault(String requestedSummary) {
        return requestedSummary == null ? DEFAULT_DESCRIPTION : requestedSummary;
    }


    /**
     * Same TransactionTemplate-not-@Transactional shape as update(), for the same @CacheEvict
     * ordering reason, plus one specific to deletion: the delete and its outbox row MUST commit
     * together. If the row were written outside the transaction and the delete then rolled back,
     * the indexer would remove a document for a book that still exists — and nothing would ever
     * put it back, because no further event fires for a book that didn't change.
     */
    @CacheEvict(value = CacheConfig.BOOKS, key = "#id")
    public void delete(UUID id) {
        if (!bookRepository.existsById(id)) {
            throw new BookNotFoundException(id);
        }
        transactionTemplate.executeWithoutResult(status -> {
            bookRepository.deleteById(id);
            publishOutbox("BOOK_DELETED", "book.deleted", new BookDeletedEvent(id));
        });
    }
}