# ReadShelf — Community Book Lending & Review API


A Spring Boot backend project that implements every concept from the
"Backend from First Principles" playlist by Sriniously.

The domain is deliberately simple — books, users, loans, reviews — so all
engineering effort goes into infrastructure, integration, and system design.

---

## Build Order

Each phase is a branch/PR. The project runs after every phase — no phase
leaves it broken. Topics in parentheses are the playlist numbers they cover.

### Phase 1: Walking Skeleton (Topics 2, 3, 9, 19)
**Goal:** A single endpoint that starts, routes, and returns JSON.

- [ ] Spring Boot app with `spring-boot-starter-web`
- [ ] `GET /api/v1/health` returns `200 OK` with a JSON body
- [ ] `application.yml` with Spring profiles (`dev`, `prod`)
- [ ] Environment variable overrides for all config values
- [ ] Understand: Tomcat embedded server, DispatcherServlet, content negotiation
- [ ] Explicit `@RestController` → `@Service` → `@Repository` layering (empty for now)

### Phase 2: Data Model & CRUD (Topics 10, 12)
**Goal:** Full CRUD on all entities with PostgreSQL.

> **Architecture note — Model A (catalog/copy split).** Unlike the naive
> "one row per listing" design, `books` is a **catalog of works/editions**
> (keyed by `isbn`) and `book_copies` is the **physical copy a user owns and
> lends**. Ownership and availability live on `book_copies`, not `books`.
> This is why the migration list has a `book_copies` table the original plan
> lacked, and why everything downstream shifts by one number.

- [x] PostgreSQL container in `docker-compose.yml`
- [ ] Flyway migrations (not JPA auto-DDL):
  - `V1__create_users_table.sql` — surrogate UUID PK, unique `username`/`email`, `role` CHECK
  - `V2__create_user_profiles_table.sql` — 1-to-1, `UNIQUE` FK, `ON DELETE CASCADE`
  - `V3__create_books_table.sql` — catalog (work/edition); `isbn` UNIQUE, `title` **not** unique
  - `V4__create_book_copies_table.sql` — owned copy; `book_id` + `owner_id` FKs, `is_available`
  - `V5__create_reviews_table.sql` — review of a **work**
  - `V6__create_loans_table.sql` — references a **copy**, not a work
  - `V7__create_wishlists_table.sql`
  - `V8__create_indexes.sql`
- [ ] JPA entities with full relationship mapping:
  - `User` ↔ `UserProfile` (One-to-One)
  - `User` → `BookCopy` (One-to-Many: copies a user owns)
  - `Book` → `BookCopy` (One-to-Many: a work has many copies)
  - `Book` → `Review` (One-to-Many: reviews attach to the **work**, so they aggregate)
  - `User` → `Review` (One-to-Many)
  - `User` ↔ `Book` via `Wishlist` (Many-to-Many with join entity; you wishlist a **work**)
  - `Loan` as a standalone entity referencing `lender`, `borrower`, `book_copy`
- [ ] Indexes on: `book.isbn` (unique, inline), `loan.status`, `loan.due_date`,
      `review.book_id`, `book.author`, `book_copies(book_id)`, `book_copies(owner_id)`,
      `wishlist(user_id, book_id)` (composite unique)
- [ ] Spring Data JPA repositories — **derived queries by default** (framework practice)
- [ ] CRUD controllers for all entities

#### Query-writing practice track
Most repository methods stay **derived** (e.g. `findByOwnerId`, `existsByUserIdAndBookId`)
to build ORM fluency. But three queries can't be expressed cleanly by method-name
derivation — write these by hand as `@Query` (JPQL or native) to practice real SQL.
Difficulty rises with joins, aggregation, and pagination:

- 🟢 **Easy** (Phase 2): *"books that have at least one available copy."* A single
  `JOIN` from `Book` to `BookCopy`, `WHERE is_available = true`, with `DISTINCT`.
- 🟡 **Medium** (Phase 2/3): *"each book with its average rating and review count."*
  `LEFT JOIN` `Book` → `Review`, `GROUP BY` the book, aggregate `AVG` + `COUNT`.
- 🔴 **Hard** (Phase 3): *keyset/cursor pagination over loans* — ordered by
  `(due_date, id)` with a composite cursor predicate `(due_date, id) > (?, ?)`.
  Derived queries can't express tuple comparison; this one is hand-written by necessity.

### Phase 3: RESTful Design & Serialization (Topics 3, 4, 11)
**Goal:** The API follows REST conventions and serializes cleanly.

- [ ] Versioned routes: `/api/v1/books`, `/api/v1/users`, `/api/v1/loans`
- [ ] Nested routes: `/api/v1/books/{id}/reviews`
- [ ] Proper HTTP status codes: 201+Location, 204 on delete, 409 on conflict
- [ ] Pagination (offset) and sorting on collection endpoints
- [ ] Cursor-based (keyset) pagination on `/api/v1/loans` and `/api/v1/reviews`
- [ ] Jackson config: custom date format, DTO projections via `@JsonView`
- [ ] Request DTOs decoupled from entities (e.g., `CreateBookRequest` ≠ `Book`)
- [ ] Response DTOs with HATEOAS-style `_links` on Loan responses
- [ ] ETag support on `GET /api/v1/books/{id}` with 304 Not Modified
- [ ] API versioning: build a `v2` of book detail response (different shape),
      serve both `/api/v1/books/{id}` and `/api/v2/books/{id}` simultaneously

### Phase 4: Validation & Transformation (Topic 6)
**Goal:** Every inbound payload is validated, every outbound payload is shaped.

- [ ] Bean Validation on all request DTOs (`@NotBlank`, `@Min`, `@Max`, `@Email`)
- [ ] Custom `@ISBN` constraint annotation with validator
- [ ] Custom `@ValidDateRange` for loan date validation
- [ ] MapStruct (or manual mapper layer) for Entity ↔ DTO conversion
- [ ] Validation error responses as structured JSON (field → message)

### Phase 5: Authentication & Authorization (Topic 5)
**Goal:** Stateless JWT auth with role-based access control.

- [ ] Spring Security config with JWT filter
- [ ] `/api/v1/auth/register` and `/api/v1/auth/login` endpoints
- [ ] JWT access token + refresh token rotation
- [ ] Three roles: `BORROWER`, `LENDER`, `ADMIN`
- [ ] Method-level authorization via `@PreAuthorize`:
  - Only the lender can approve/reject a loan
  - Only the borrower can cancel their own request
  - Only admins can delete any book
  - Users can only update their own profile
- [ ] `SecurityContextHolder` as request context for current user

### Phase 6: Middlewares & Request Context (Topics 7, 8)
**Goal:** Cross-cutting concerns handled in the filter chain.

- [ ] `CorrelationIdFilter` — generates UUID, sets on MDC and response header
- [ ] `RequestLoggingFilter` — logs method, path, status, duration
- [ ] `RateLimitFilter` — per-user rate limiting backed by Redis (sliding window)
- [ ] `RequestContext` utility class — static access to current user ID, roles,
      correlation ID from anywhere in the call stack
- [ ] Filter ordering configured explicitly

### Phase 7: Business Logic Layer (Topic 13)
**Goal:** Domain rules live in services, not controllers or repositories.

- [ ] `LoanService` enforces:
  - Can't borrow a copy you own
  - Max 3 active loans per borrower
  - The `BookCopy` must be available (`is_available`, not currently lent)
  - Loan state machine: REQUESTED → APPROVED → ACTIVE → RETURNED (or OVERDUE)
  - Only lender can transition REQUESTED → APPROVED
  - Only borrower can transition ACTIVE → RETURNED
- [ ] `ReviewService` enforces:
  - One review per user per **work** (`UNIQUE(user_id, book_id)` is the DB backstop)
  - Can only review a work you've borrowed a copy of
- [ ] Optimistic locking (`@Version`) on `Loan` and `BookCopy` entities
  - Concurrent borrow attempts on the same copy → one succeeds, one gets 409

### Phase 8: Error Handling (Topic 18)
**Goal:** Consistent, informative error responses across the entire API.

- [ ] Global `@ControllerAdvice` exception handler
- [ ] RFC 7807 Problem Detail response format
- [ ] Domain exceptions: `BookAlreadyLentException`, `LoanLimitExceededException`,
      `BookNotFoundException`, `UnauthorizedLoanActionException`
- [ ] Downstream failure handling: Redis unreachable → fallback, ES down → degrade
- [ ] Validation errors → 422 with field-level detail
- [ ] Optimistic lock failures → 409 Conflict

### Phase 9: Database Transactions & Painful Migrations (Topic 12+)
**Goal:** Understand transaction boundaries and real migration complexity.

- [ ] `@Transactional` on `LoanService.approveLoan()` — update loan status +
      update book availability in one transaction
- [ ] Manual transaction management via `TransactionTemplate` on at least one flow
- [ ] Transactional outbox pattern: write domain event to `outbox` table inside
      the same transaction as the business write, poll outbox for publishing
- [ ] Painful migration: `V8__rename_book_description_to_summary.sql` with backfill
- [ ] Painful migration: `V9__add_not_null_to_existing_column.sql` in two steps
      (add column nullable → backfill → alter to NOT NULL)

### Phase 10: Caching (Topic 14)
**Goal:** Redis caching with intentional eviction strategies.

- [ ] Redis container in `docker-compose.yml`
- [ ] `@Cacheable` on `GET /api/v1/books/{id}` and `GET /api/v1/users/{id}/profile`
- [ ] `@CacheEvict` on book update, loan approval (copy availability changed),
      profile update
- [ ] Cache-aside pattern understood and documented
- [ ] TTL configuration per cache region
- [ ] Fallback behavior when Redis is down (serve from DB, log warning)

### Phase 11: Async & Messaging (Topics 15, 16)
**Goal:** Background job processing and scheduled tasks.

- [ ] RabbitMQ container in `docker-compose.yml`
- [ ] Event publishing: `LoanRequestedEvent`, `LoanApprovedEvent`, `LoanOverdueEvent`,
      `BookCreatedEvent`, `BookUpdatedEvent`
- [ ] Outbox poller: reads `outbox` table, publishes to RabbitMQ, marks as sent
- [ ] Consumers:
  - `EmailNotificationConsumer` — sends transactional emails (JavaMailSender + MailHog)
  - `SearchIndexConsumer` — syncs book data to Elasticsearch
  - `WebhookDispatchConsumer` — fires outbound webhooks
- [ ] `@Scheduled` overdue loan checker: runs nightly, publishes `LoanOverdueEvent`
- [ ] `@Scheduled` stale outbox cleanup: deletes successfully-sent outbox entries older than 7 days
- [ ] `@Async` thread pool configuration and sizing

### Phase 12: Search (Topic 17)
**Goal:** Full-text search across the catalog.

- [ ] Elasticsearch container in `docker-compose.yml`
- [ ] Book search index with fields: title, author, summary, genre
- [ ] Review content indexed and searchable
- [ ] `GET /api/v1/search/books?q=dostoevsky` with fuzzy matching
- [ ] Relevance scoring and result highlighting
- [ ] Index kept in sync via RabbitMQ consumer (from Phase 11)
- [ ] Graceful degradation: ES down → return empty results + warning, don't crash

### Phase 13: Object Storage (Topic 25)
**Goal:** File uploads for book cover images.

- [ ] MinIO container in `docker-compose.yml`
- [ ] `POST /api/v1/books/{id}/cover` — multipart upload, store in MinIO
- [ ] Presigned URL generation for direct client download
- [ ] Metadata (filename, content type, size) stored in PostgreSQL
- [ ] File size and type validation (max 5MB, images only)

### Phase 14: Real-Time (Topic 26)
**Goal:** Push notifications over WebSocket.

- [ ] Spring WebSocket + STOMP configuration
- [ ] User subscribes to `/user/queue/notifications`
- [ ] Events pushed to connected users:
  - "Someone requested to borrow your book"
  - "Your borrow request was approved"
  - "Your book was returned"
- [ ] Event flow: service → RabbitMQ → consumer → WebSocket push
- [ ] Handles disconnected users gracefully (message is lost, not queued — 
      or optionally stored for polling)

### Phase 15: GraphQL Layer (Bonus)
**Goal:** Second API surface that reuses the same service layer.

- [ ] Spring for GraphQL dependency
- [ ] GraphQL schema: `Query { book(id: ID!): Book, searchBooks(q: String!): [Book] }`
- [ ] `@QueryMapping` and `@MutationMapping` resolvers calling existing services
- [ ] DataLoader for N+1 prevention (batch-load reviews for a list of books)
- [ ] Same auth (JWT) applied to GraphQL endpoint
- [ ] Demonstrates Topic 9 payoff: service layer is entry-point agnostic

### Phase 16: Webhooks (Topic 30)
**Goal:** Outbound webhook system with delivery guarantees.

- [ ] `webhook_subscriptions` table: user_id, target_url, event_types, secret
- [ ] `webhook_deliveries` table: subscription_id, payload, status, attempts, last_attempt_at
- [ ] HMAC-SHA256 signature on payloads using the subscription secret
- [ ] Retry with exponential backoff (1m, 5m, 30m, 2h — max 5 attempts)
- [ ] `WebhookDispatchConsumer` reads from RabbitMQ, delivers, records result
- [ ] `GET /api/v1/webhooks/{id}/deliveries` — users can inspect delivery history

### Phase 17: Security Hardening (Topic 22)
**Goal:** Defense in depth beyond auth.

- [ ] CORS configuration (allowed origins, methods, headers)
- [ ] CSRF — understand why it's disabled for stateless APIs, document the reasoning
- [ ] Input sanitization on review text (strip HTML/XSS)
- [ ] Rate limiting already in place from Phase 6
- [ ] JWT expiry tuning + refresh token rotation
- [ ] Secrets via environment variables (already from Phase 1)
- [ ] Dependency vulnerability scan (OWASP dependency-check or `mvn verify`)

### Phase 18: Observability (Topic 20)
**Goal:** You can trace any request through the entire system.

- [ ] Structured JSON logging via Logback (`logback-spring.xml`)
- [ ] Correlation ID in every log line (set in MDC by filter from Phase 6)
- [ ] Micrometer metrics:
  - Request latency by endpoint
  - Cache hit/miss ratio
  - RabbitMQ queue depth
  - Active DB connections
  - Loan approval rate (custom business metric)
- [ ] Prometheus scraping `/actuator/prometheus`
- [ ] Grafana dashboard (JSON provisioned in `infra/grafana/`)
- [ ] Health checks: `/actuator/health` with DB, Redis, RabbitMQ, ES indicators

### Phase 19: Graceful Shutdown (Topic 21)
**Goal:** The app stops cleanly under all conditions.

- [ ] `server.shutdown=graceful` in config
- [ ] `spring.lifecycle.timeout-per-shutdown-phase=30s`
- [ ] RabbitMQ consumers drain current messages before stopping
- [ ] WebSocket connections closed with a close frame
- [ ] In-flight HTTP requests complete before Tomcat stops
- [ ] Verify with a test: start a long-running request, send SIGTERM, confirm it completes

### Phase 20: Scaling & Performance (Topic 23)
**Goal:** Identify and fix bottlenecks.

- [ ] `@EntityGraph` on book listing to solve N+1 (eager-fetch reviews count, loan status)
- [ ] HikariCP connection pool tuning (`maximumPoolSize`, `connectionTimeout`)
- [ ] Query analysis: explain plans on complex queries, add missing indexes if needed
- [ ] Load test with Gatling: simulate 100 concurrent users browsing, borrowing, reviewing
- [ ] Identify bottleneck, fix, re-test, document before/after

### Phase 21: Concurrency Deep Dive (Topic 24)
**Goal:** Concurrency bugs are provoked and resolved.

- [ ] Load test: 10 users simultaneously borrow the same book → only 1 succeeds
- [ ] Optimistic lock retry strategy (catch `OptimisticLockingFailureException`, retry once)
- [ ] `@Async` thread pool: configure `corePoolSize`, `maxPoolSize`, `queueCapacity`
- [ ] Demonstrate thread pool exhaustion and recovery
- [ ] Document: what is thread-safe in Spring (singletons) and what isn't

### Phase 22: Testing (Topic 27)
**Goal:** Confidence that the system works and keeps working.

- [ ] Unit tests: `LoanService` with Mockito (mock repo, verify business rules)
- [ ] Repository tests: `@DataJpaTest` with Testcontainers (real PostgreSQL)
- [ ] API tests: `@SpringBootTest` + MockMvc (full request/response cycle)
- [ ] Integration test: full borrow flow — register → create book → request loan →
      approve → verify state in DB, cache evicted, message published
- [ ] Contract test: verify OpenAPI spec matches actual responses
- [ ] Test coverage report via JaCoCo

### Phase 23: OpenAPI & Documentation (Topic 29)
**Goal:** The API documents itself.

- [ ] SpringDoc OpenAPI dependency
- [ ] `@Operation`, `@Schema`, `@ApiResponse` on all endpoints
- [ ] Example request/response payloads in annotations
- [ ] Business rules documented in operation descriptions
- [ ] Generated spec available at `/api-docs` and Swagger UI at `/swagger-ui`
- [ ] Export spec as `openapi.yaml` and commit to repo

### Phase 24: 12-Factor Audit (Topic 28)
**Goal:** Validate the architecture against the 12-Factor methodology.

- [ ] I. Codebase — one repo, tracked in Git ✓
- [ ] II. Dependencies — declared in `pom.xml`, no system-level assumptions ✓
- [ ] III. Config — environment variables, no secrets in code ✓
- [ ] IV. Backing services — DB, Redis, RabbitMQ, ES, MinIO all treated as attached resources ✓
- [ ] V. Build/release/run — Docker multi-stage build separates stages ✓
- [ ] VI. Processes — app is stateless, all state in backing services ✓
- [ ] VII. Port binding — embedded Tomcat, self-contained ✓
- [ ] VIII. Concurrency — scale by running multiple instances ✓
- [ ] IX. Disposability — graceful shutdown ✓
- [ ] X. Dev/prod parity — Docker Compose mirrors production topology ✓
- [ ] XI. Logs — written to stdout as structured JSON ✓
- [ ] XII. Admin processes — Flyway migrations, scheduled jobs as part of the app ✓
- [ ] Document any gaps with remediation plan

### Phase 25: DevOps (Topic 31)
**Goal:** The project is deployable.

- [ ] Multi-stage `Dockerfile` (build with Maven, run with JRE slim)
- [ ] `docker-compose.yml` orchestrating all services
- [ ] GitHub Actions CI: run tests → build image → push to registry
- [ ] `.env.example` documenting all required environment variables
- [ ] Health check-based container dependencies in Compose

---

## Tech Stack

| Layer              | Technology                        |
|--------------------|-----------------------------------|
| Framework          | Spring Boot 4.x                   |
| Language           | Java 21                           |
| Database           | PostgreSQL 16                     |
| Migrations         | Flyway                            |
| Cache              | Redis 7                           |
| Message Broker     | RabbitMQ 3.13                     |
| Search             | Elasticsearch 8.x                 |
| Object Storage     | MinIO                             |
| Email (dev)        | MailHog                            |
| Monitoring         | Micrometer + Prometheus + Grafana |
| API Docs           | SpringDoc OpenAPI                 |
| GraphQL            | Spring for GraphQL                |
| WebSocket          | Spring WebSocket + STOMP          |
| Testing            | JUnit 5, Mockito, Testcontainers  |
| Build              | Maven                             |
| Container          | Docker + Docker Compose           |
| CI                 | GitHub Actions                    |

---

## Running Locally

```bash
# Start all backing services
docker compose up -d

# Run the app
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Entity Relationship Summary

```
User 1---1 UserProfile             (One-to-One)
Book 1---* BookCopy                 (One-to-Many: a work/edition has many physical copies)
User 1---* BookCopy                 (One-to-Many: a user owns many copies)   [owner_id]
Book 1---* Review                   (One-to-Many: reviews attach to the work, so they aggregate)
User 1---* Review                   (One-to-Many: user writes many reviews)
User *---* Book via Wishlist        (Many-to-Many: join entity with timestamp; wishlist a work)
Loan → BookCopy                     (Many-to-One: a loan is for a specific physical copy)
Loan → User (lender)                (Many-to-One)
Loan → User (borrower)              (Many-to-One)
```

**Model A (catalog/copy split):** `Book` is the catalog entry — a work/edition,
identified by `isbn` (unique; `title` is not, because translations and editions
share titles). `BookCopy` is a physical copy a user owns and lends — it carries
`owner_id` and `is_available`. So you **review a work** (`Review → Book`) but you
**borrow a copy** (`Loan → BookCopy`).

Loan is its own entity (not a join table) because it carries state:
status, request_date, approval_date, due_date, return_date, version.
#   b a c k e n d - c a p s t o n e 
 
 
