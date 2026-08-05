# ReadShelf — Status

_Last updated: 2026-07-24_

## Where we are

**Setup + Phase 1 (Walking Skeleton): complete.** Spring Boot 4.0.6 / Java 21,
full docker-compose stack (Postgres 16, Redis, RabbitMQ, Elasticsearch, MinIO,
MailHog), `dev`/`prod` profiles, `GET /api/v1/health`.

**Phase 2 (Data Model & CRUD): COMPLETE.** All CRUD slices built + verified
(full relationship chain round-trips through the API; FK resolution + fail-fast 400s work).

**Phase 3 (RESTful Design & Serialization): COMPLETE.** Offset pagination + sorting +
param validation (all 6 list endpoints), 409-on-conflict, keyset/cursor pagination (loans),
nested route (`/books/{id}/reviews`), ETags + 304, HATEOAS `_links` (loan), v2 book detail
(URI versioning + JPQL aggregate). All verified live. See Phase 3 progress below.

**Phase 4 (Validation & Transformation): COMPLETE.** Two hand-written custom constraints
(`@NoSelfLoan` class-level, `@ValidDateRange` field-level with a `maxDays` window),
Bean Validation coverage filled across all request DTOs, and a structured field→message
error handler (`@RestControllerAdvice`). All verified live. See Phase 4 progress below.

**Phase 5 (Authentication & Authorization): COMPLETE** (one item deferred to Phase 7).
Stateless JWT (access + refresh **rotation** with reuse detection), `/api/v1/auth`
register/login/refresh, BCrypt hashing, `@PreAuthorize` method security (role + ownership),
`SecurityContextHolder` as request context. All verified live. See Phase 5 progress below.

**Phase 6 (Middlewares & Request Context): COMPLETE.** Four cross-cutting pieces in a new
`com.readshelf.web` package: `CorrelationIdFilter` (honor-and-validate inbound id → MDC +
response header), `RequestLoggingFilter` (one access-log line per request), `RateLimitFilter`
(per-user/-IP Redis sorted-set sliding window, 429 on overflow), and a `RequestContext` facade.
Explicit filter ordering across BOTH chains. All verified live. See Phase 6 progress below.

**Phase 7 (Business Logic Layer): COMPLETE.** Domain rules now live in the services.
`LoanService` gained create-time guards (can't borrow your own copy, copy must be available,
max 3 "active" loans) and the loan state machine as dedicated action endpoints
(`approve`/`pickup`/`return`) — each guarded by role (403) + current state (409).
`ReviewService` enforces "review only a work you've borrowed" via an encapsulated loan-side
query. Optimistic locking (`@Version`) on `Loan` + `BookCopy` (migration V10), with a lost
race mapped to 409. `@Transactional` on the two-entity writes. Code-complete + compiles; the
concurrency *load test* is deliberately left to Phase 21. See Phase 7 progress below.

**Phase 8 (Error Handling): COMPLETE.** One global `@RestControllerAdvice`
(`system.GlobalExceptionHandler`) extending `ResponseEntityExceptionHandler`, emitting
RFC 7807 `ProblemDetail` (`application/problem+json`) as the single error shape. Six
HTTP-agnostic domain exceptions (`BookNotFoundException`, `BookAlreadyLentException`,
`LoanLimitExceededException`, `UnauthorizedLoanActionException` + `LoanNotFoundException`,
`IllegalLoanStateException`), wired through `LoanService`/`BookService`. Validation → **422**
(override) with field errors as an `errors` extension member; optimistic lock → 409;
framework errors (malformed body, etc.) inherited as ProblemDetail for free. `RateLimitFilter`
now **fails open** when Redis is unreachable. Old `ValidationExceptionHandler` retired. All
verified live (422/400/404 all returned `problem+json`). See Phase 8 progress below.

**Phase 9 (Database Transactions & Painful Migrations): COMPLETE.** Expand/contract rename
of `books.summary` → `books.description` across three migrations (V11 add+backfill, V12 drop
old column, V13 two-step NOT NULL), decoupled from the public API (request DTO field stays
`summary`; the service maps it to the renamed column and defaults NULLs so the new NOT NULL
holds). Programmatic transaction (`TransactionTemplate`) scoping just the write in
`LoanService.create()`. **Transactional outbox pattern**: an `outbox` table (V14, JSONB
payload), a `LOAN_REQUESTED` event written in the SAME tx as the loan, and a `@Scheduled`
`OutboxPoller` that drains + "publishes" (logs; real broker is Phase 11) + stamps
`processed_at`. All verified live end-to-end. See Phase 9 progress below.

**Phase 10 (Caching): COMPLETE.** Redis-backed Spring Cache. `@Cacheable` on `GET books/{id}`
and `GET users/{id}/profile` (cache-aside read); `@CacheEvict` on book update/delete and profile
upsert; loan-approval evict is a documented no-op (availability lives on the uncached `BookCopy`,
not the cached `books` region). Per-region TTLs (books 10m, userProfiles 30m) + a type-bound JSON
value serializer per region. **Fail-open**: a `CacheErrorHandler` logs + swallows cache errors so
a Redis outage degrades to the DB instead of 500ing — bounded by a **60ms Redis command/connect
timeout** so a dead Redis fails fast (0.19s) instead of blocking ~60s. All verified live
end-to-end. See Phase 10 progress below.

## Phase 2 progress

### Done
- [x] **Flyway migrations V1–V8** (ddl-auto: none). Users, user_profiles, books,
      book_copies, reviews, loans, wishlists, indexes. Applied + verified in Postgres.
      Adopted **Model A** (catalog/copy split) — see `book_modeling` memory.
- [x] **JPA entities (all 7)** with full relationship mapping, verified by clean boot:
      User↔UserProfile (1-1), User/Book→BookCopy (1-many), User/Book→Review,
      Loan→lender/borrower/book_copy (3 @ManyToOne), User↔Book via Wishlist.
      LAZY fetch on @ManyToOne, `@Enumerated(STRING)` on loan status.
- [x] **Indexes (V8)** — FK columns + status/due_date/author.
- [x] **Repositories** — `JpaRepository` per entity; `UserRepository` has
      `findByEmail`/`findByUsername` (derived). `BookRepository` has the
      🟢 easy + 🟡 medium hand-written queries (native SQL; verified via psql).
- [x] **`Book` CRUD slice — full + verified end-to-end** (POST/GET-list/GET-one/PUT/DELETE):
      `Controller → Service → Mapper → Repository`, request/response DTOs, Bean
      Validation (`@NotBlank`/`@Size`/`@ISBN`), correct status codes
      (201+Location, 200, 204, 404). Tested live with curl.

### Remaining
- Nothing in Phase 2 — all CRUD slices done (Book, User, Review, BookCopy, Wishlist, Loan).
- Conventions for relationship entities: request DTOs carry FK ids; service resolves
  them fail-fast (`findById().orElseThrow` -> 400). Mapper sets scalars only; service
  attaches resolved relations. Wishlist has no PUT (nothing mutable); Loan has no PUT
  (transitions are Phase 7). Responses expose related entities as ids, not nested objects.

## Phase 3 progress

### Done
- [x] **Offset pagination + sorting on ALL list endpoints** (Book, User, Review,
      BookCopy, Wishlist, Loan). Each returns `utils.PagedResponse<T>` — a generic,
      self-owned HAL-style envelope (`_metadata` {page, perPage, totalPages, totalCount}
      + `records`), built via `PagedResponse.from(Page<T>)`. Chosen over serializing
      Spring's `Page` directly (unstable JSON contract → logs a warning).
- [x] **Sort whitelist per entity** — `*SortField` enum bound from `?sortBy`; unknown
      value rejected at binding time → 400. Each constant maps to the entity property;
      sort always appends `id` as a unique tiebreaker (stable paging).
- [x] **Page-param validation** — `@Min(0)` page, `@Min(1) @Max(100)` size → 400 on
      out-of-range. NOTE: **no `@Validated` on the controller** — in Spring Boot 4 that
      opts into the legacy AOP path (ConstraintViolationException → 500); omitting it
      uses native method validation (HandlerMethodValidationException → 400). See
      `feedback_param_validation_400` memory.

- [x] **409 on conflict** — duplicate Review / Wishlist (UNIQUE(user_id, book_id)) now
      returns 409, not 500. Pattern: `saveAndFlush` inside try (forces the INSERT so the
      violation fires synchronously, survives future @Transactional), catch
      `DataIntegrityViolationException` → `ResponseStatusException(CONFLICT, msg)`.
      NOTE: broad catch assumes the only integrity error reachable at save is the unique
      one (FKs resolved fail-fast → 400; DTO validated). Refine constraint-specifically in Phase 8.

- [x] **🔴 Keyset / cursor pagination (loans)** — `GET /api/v1/loans/keyset` (coexists
      with the offset `GET /loans`). Native `@Query` with the tuple predicate
      `(due_date, id) > (:dueDate, :id)` (JPQL can't express row-value comparison) +
      `ORDER BY due_date, id LIMIT :limit`; separate `findFirstPage` for the no-cursor
      case. Opaque cursor = base64url of `"<dueDate>#<id>"` via a self-encoding
      `LoanCursor` record. `nextCursor` null when `size < limit`. Response = generic
      `utils.CursorPage<T>` (records + nullable nextCursor, omitted from JSON when null).
      Verified live: paged limit=3 over 7 seeded loans incl. a due_date TIE — no skips,
      no dupes, tie ordered by id, last page drops the cursor. `limit` guarded 1–100 → 400.
      NOTE: query filters `due_date IS NOT NULL` (loans get a due date only on approval).
      Malformed/garbage cursor currently → 500 (decode throws); harden in Phase 8.

- [x] **Nested route** — `GET /api/v1/books/{bookId}/reviews` (reviews rooted at their
      book). Handler lives on `BookController` (option a: URL rooted at the parent → parent's
      controller owns `/books/**`), which now depends on `ReviewService`. Derived query
      `ReviewRepository.findByBook_Id(UUID, Pageable)` (underscore = explicit relation
      traversal). `ReviewService.findByBook` does an `existsById` 404 guard BEFORE querying
      so a bad bookId → 404, not 200-empty. Reuses the offset `PagedResponse` + sort/validation.
      Verified: book-with-reviews → 200+records; book-no-reviews → 200+empty; bad id → 404.

- [x] **ETags + 304 (conditional GET)** — `GET /api/v1/books/{id}` now emits a STRONG
      ETag derived from `updatedAt.toEpochMilli()` (quoted: `"<millis>"`). Handler reads
      `If-None-Match` (`@RequestHeader`, optional); if it equals the current ETag →
      `304 Not Modified` (no body, ETag still set); else `200` + body + ETag. Logic lives
      in the controller (HTTP concern); `updatedAt` was added to `BookResponseDTO` + mapper
      so the version is reachable. Chose version-derived (b) over body-hash filter (a) to
      learn the mechanics. Verified via .NET HttpClient (curl/PowerShell mangles quoted
      header values): match→304, stale→200, and after a PUT the old ETag→200 with a new tag.
      NOTE: per-resource only so far (not list endpoints); weak ETags / `checkNotModified`
      helper not used. Could DRY the ETag building if more resources adopt it.

- [x] **HATEOAS `_links` (Loan)** — `GET /api/v1/loans/{id}` now returns a HAL-style
      `_links` map keyed by relation (`self`, `lender`, `borrower`, `bookCopy`), each a
      `utils.Link(href, method)` with an ABSOLUTE url + verb. Chose map-keyed-by-rel (vs
      a list with `rel` as a field — would duplicate the key) and absolute urls (vs
      relative). Because absolute urls need request context, link-building lives in the
      controller (`buildLinks`, via `ServletUriComponentsBuilder.fromCurrentContextPath()`),
      not the mapper; the mapper builds the DTO with `links=null` and the controller attaches
      them through a `withLinks(...)` copy (records are immutable). `_links` is `@JsonInclude
      NON_NULL` so it's omitted when absent. Verified live: all 4 absolute links present.
      NOTE: only on the single-loan GET so far (not list/keyset). State-dependent action
      links (e.g. `approve` only when REQUESTED) deferred to Phase 7 — HashMap chosen now
      so they can be added conditionally without a rewrite.

- [x] **v2 book detail shape (URI versioning)** — `GET /api/v2/books/{id}` returns a
      richer `BookDetailV2DTO` = v1 fields + `averageRating` (nullable Double) + `reviewCount`.
      URI versioning (chosen over media-type/query-param, consistent with `/v1`) in a separate
      `BookV2Controller` (`/api/v2/books`). The aggregate is built straight into the DTO by a
      JPQL **constructor expression** (`SELECT new ...BookDetailV2DTO(...)`) over the mapped
      `Book LEFT JOIN b.reviews` with `GROUP BY` on all six non-aggregate fields — no mapper,
      no Object[] casting. `LEFT JOIN` keeps zero-review books (AVG→null, COUNT→0); missing
      book → `Optional.empty` → 404. Verified live: 1 review→5.0/1, 0 reviews→null/0, after a
      2nd review→4.0/2 (real averaging), bad id→404.

### Remaining (Phase 3)
Nothing — Phase 3 complete. ✅ (Optional carry-overs if desired later: keyset pagination on
`/reviews` too; Jackson custom date format / `@JsonView`; widening ETags & `_links` to more
endpoints. None blocking.)

## Phase 4 progress

### Done
- [x] **Custom constraints live in `com.readshelf.validation`** — annotation + a
      `ConstraintValidator` per constraint, wired via `@Constraint(validatedBy = ...)`.
      Decision: did NOT build a custom `@ISBN` (README's example) — Hibernate's
      `org.hibernate.validator.constraints.ISBN` already covers format; reinventing it
      adds no value. Satisfied the "write a custom constraint" goal with two domain-useful
      ones instead.
- [x] **`@NoSelfLoan` (class-level)** on `LoanRequestDTO` — rejects `lenderId == borrowerId`.
      Class-level (`@Target(TYPE)`) because the check is cross-field, so the validator's
      target is the whole DTO (`ConstraintValidator<NoSelfLoan, LoanRequestDTO>`), not a
      single field. `isValid` returns `true` on null value / null ids (lets `@NotNull` own
      null-rejection — the Bean Validation idiom), compares with `.equals()` not `==`.
      Produces a **global** error (no field), surfaced under the object-name key.
- [x] **`@ValidDateRange` (field-level)** on `dueDate` — due date must fall within
      `[now, now + maxDays]`, default `maxDays = 90`. Two bounds (not just `@Future`, which
      only does "not past") so it's genuinely custom and earns the name; the `maxDays()`
      annotation param is captured in `initialize()`. Inclusive bounds via `!isBefore(now)`
      / `!isAfter(latest)`. Null `dueDate` → valid (optional field). `ConstraintValidator
      <ValidDateRange, Instant>`.
- [x] **Bean Validation coverage filled** across request DTOs. Principle locked: a
      `@Size(max=...)` should **mirror the DB column width** where one exists, else it's an
      API **policy** bound. `username` → `@Size(max=255)` (mirrors `VARCHAR(255)`);
      `password` → `@Size(min=8)`, no max (DB is `TEXT`; security-policy floor); `title`/
      `author` → `@Size(max=255)` (policy; DB is `TEXT`/unbounded); review `content` →
      `@Size(max=3000)` (policy). FK ids already `@NotNull`, role `@Pattern`, rating
      `@Min/@Max`, email `@Email` — left as-is.
- [x] **Structured validation errors** — `system.ValidationExceptionHandler`
      (`@RestControllerAdvice`) handles `MethodArgumentNotValidException` → `400` +
      `Map<String,String>` of field→message. Walks BOTH `getFieldErrors()` (field-level)
      and `getGlobalErrors()` (class-level like `@NoSelfLoan`, keyed by object name).
      Verified live: self-loan → `{"loanRequestDTO": "..."}`, past due date →
      `{"dueDate": "..."}`, and both-bad-at-once returns both keys in one 400 response.
      NOTE: only `MethodArgumentNotValidException` (request bodies); param-validation
      (`HandlerMethodValidationException`) not handled here yet. Global-error keys collide
      if a DTO ever gets two class-level constraints — fine with one today.

### Remaining (Phase 4)
Nothing — Phase 4 complete. ✅ (`ValidationExceptionHandler` was generalized into the RFC 7807
`GlobalExceptionHandler` and validation errors switched to 422 — **done in Phase 8**.)

## Phase 5 progress

### Done
- [x] **Stateless JWT security** (`config.SecurityConfig`) — replaces the old permit-all.
      `SessionCreationPolicy.STATELESS`, CSRF off (token in header, not cookie),
      `permitAll` on `/api/v1/auth/**` + health, `anyRequest().authenticated()` last.
      Custom `authenticationEntryPoint` → clean 401 (no login-form redirect).
      NOTE: **must `permitAll` the ERROR dispatch type** (`dispatcherTypeMatchers(
      DispatcherType.ERROR)`) — `OncePerRequestFilter` skips the `/error` re-dispatch, so
      without it every secured `sendError` (e.g. a 403 from `@PreAuthorize`) collapses to 401.
- [x] **`JwtService`** — mint (jjwt 0.13) `sub`=userId, `role` claim, 15-min TTL; `parseClaims`
      verifies signature + exp in one shot (throws on failure). HMAC key from `JwtProperties`
      secret (`readshelf.security.jwt.*`, TTLs as `Duration`).
- [x] **`JwtAuthFilter`** (`OncePerRequestFilter`, plain class — wired via `addFilterBefore`,
      NOT `@Component`, to avoid double registration). Reads `Authorization: Bearer`, populates
      `SecurityContext` (principal = userId String, authority = `ROLE_<role>`). Authenticates
      only; never rejects — authorization is SecurityConfig's job.
- [x] **`/api/v1/auth`** — `register` (forces role BORROWER, BCrypt hash, 409 on dup via
      saveAndFlush/catch), `login` (uniform 401, returns access + refresh), `refresh` (rotation).
- [x] **`@PreAuthorize` method security** (`@EnableMethodSecurity`). Role-only:
      `hasRole('ADMIN')` on `DELETE /books/{id}`. Ownership:
      `authentication.principal == #id.toString()` on `PUT /users/{id}`. Verified: own→200,
      other→403, non-admin delete→403.
- [x] **Refresh token rotation + reuse detection** — `refresh_tokens` table (V9):
      `token_hash` (SHA-256, deterministic so it's lookup-able; raw value returned to client
      once, never stored), `family_id` (rotation lineage), `expires_at` (7d), `revoked`.
      `AuthService.refresh` = lookup-or-401 → reuse check (revoked row ⇒ `revokeFamily` + 401)
      → expiry check → rotate (spend old, issue new in SAME family). Shared `issueRefreshToken`
      helper (login = new family, refresh = same family). Verified live: rotate→200, chain
      continues, replay of spent token→401 AND nukes the whole family (current token→401).
      NOTE: method is `@Transactional(dontRollbackOn = ResponseStatusException.class)` — the
      tx is needed for the `@Modifying revokeFamily` + to keep the lazy `RefreshToken.user`
      proxy initializable; `dontRollbackOn` keeps the reuse-detection revoke from being rolled
      back by the 401 throw.

### Remaining (Phase 5)
- ~~🔜 2 of 4 `@PreAuthorize` rules deferred to Phase 7~~ — **RESOLVED in Phase 7**, but via
      *in-service* authz (Decision A), not `@PreAuthorize`: loan-transition ownership is
      relationship-based (needs the loaded loan), so it lives in the service. See Phase 7 progress.
- (Phase 8) Auth error bodies still leak a stack trace via `/error`; RFC 7807 will fix.

## Phase 6 progress

### Done
- [x] **`CorrelationIdFilter`** (`@Component`, `@Order(HIGHEST_PRECEDENCE)`) — OUTER servlet
      chain, runs BEFORE Spring Security so even a 401 carries an id. **Honors an inbound
      `X-Correlation-Id`** but validates it permissively (allowlist `^[a-zA-Z0-9_\-]{1,50}$`
      — defeats oversized values + log injection); invalid/absent → fresh `UUID`. Puts id in
      MDC + response header. **MDC cleared in `finally`** (ThreadLocal + pooled threads → would
      leak into the next request otherwise). Verified: valid id echoed, bad-chars/too-long → fresh.
- [x] **`RequestLoggingFilter`** (`@Component`, `@Order(HIGHEST_PRECEDENCE + 1)`) — one
      access-log line per request AFTER the chain (status + duration only exist post-handling),
      in a `finally` (a throwing request still logs). `log.info("{} {} -> {} ({}ms)", ...)` with
      SLF4J placeholders (lazy render). Correlation id rides along via MDC, not the message.
- [x] **`RateLimitFilter`** (NOT `@Component` — hand-wired `addFilterAfter(JwtAuthFilter.class)`
      so the SecurityContext is populated and we can key by user). Redis **sorted-set sliding
      window log**, "fair" semantic: `ZREMRANGEBYSCORE` evict → `ZCARD` count → **reject 429 WITHOUT
      recording** (recording rejects = penalty semantic; skipping = window drains on schedule) →
      else `ZADD` (member `now-<uuid>`, unique) → `EXPIRE` (idle keys self-clean). Key =
      `ratelimit:user:<id>` or `ratelimit:ip:<addr>` fallback (bounds anonymous login/register
      abuse). 100/min. Verified live: 100×200 then 429; Redis ZCARD=100 (not 103) proves rejects
      unrecorded; TTL set. NOTE: 3 separate round-trips → not atomic under load (two can pass the
      count check); atomic version is a single Lua script — flagged, not built. `getRemoteAddr()`
      is the proxy IP behind a LB (`X-Forwarded-For` is the hardening fix) — flagged.
- [x] **`RequestContext`** — `final`, private-ctor, all-static facade over the per-request
      thread-locals. `currentUserId()` → `Optional<String>` (empty when anon — guards the
      `AnonymousAuthenticationToken` whose `isAuthenticated()==true`/principal `"anonymousUser"`),
      `currentRoles()` → `Set<String>` (ROLE_ prefix stripped, unmodifiable), `correlationId()`
      → reads `MDC`. `RateLimitFilter.resolveClientId` refactored to use it (DRY, proves it works).
      NOTE: thread-local — an `@Async` thread won't inherit it (gotcha for later phases).
- [x] **Explicit filter ordering** across both chains: OUTER `CorrelationIdFilter` →
      `RequestLoggingFilter` → (Spring Security `FilterChainProxy`: `JwtAuthFilter` →
      `RateLimitFilter`) → controllers.
- [x] **Logging pattern** — `logging.pattern.console` in `application.yml` adds
      `%X{correlationId:-}` so the id appears IN each log line (was invisible on the default
      pattern). Interim; full structured-JSON `logback-spring.xml` is Phase 18.

### Remaining (Phase 6)
Nothing — Phase 6 complete. ✅ (Carry-overs, non-blocking: atomic rate-limit via Lua script;
`X-Forwarded-For` real-client-IP; rate-limit config as `@ConfigurationProperties` vs constants.)

## Phase 7 progress

### Done
- [x] **`LoanService` create-time rules** (in `create`, after FK resolution, before save):
      (1) can't borrow a copy you own — `bookCopy.getOwner().getId().equals(borrower.getId())`
      → **403** (compare IDs, not entities: `User` has no `equals()`, and `create` isn't
      `@Transactional` so the two `User` loads needn't be the same instance); (2) copy must be
      available → **409**; (3) max active loans → **409**, via
      `loanRepository.countByBorrower_IdAndStatusIn(borrowerId, {APPROVED, ACTIVE, OVERDUE})`
      against `MAX_ACTIVE_LOANS = 3`. **Decision:** "active" = *tying up a copy*
      (APPROVED/ACTIVE/OVERDUE), not just physically-held — because approval already reserves
      the copy (`is_available=false`). REQUESTED/RETURNED don't count. `lender != borrower` is
      still enforced upstream by `@NoSelfLoan` (400).
- [x] **Loan state machine as action endpoints** (`POST /loans/{id}/approve|pickup|return`,
      not a blunt PUT). Each follows a 4-beat skeleton: **LOAD** (404) → **AUTHZ** (403) →
      **GUARD** state (409) → **MUTATE** (status + timestamps + copy, save). `approve`
      (REQUESTED→APPROVED, lender only): re-checks copy availability, stamps `approvalDate`,
      **reserves the copy** (`is_available=false`). `pickup` (APPROVED→ACTIVE, borrower only):
      borrower confirms possession; touches only the loan. `returnLoan` (ACTIVE→RETURNED,
      borrower only): stamps `returnDate`, **frees the copy** (`is_available=true`).
      **Decision:** reserve at *approval* (not pickup) so double-booking is caught early.
      Caller id comes from `RequestContext.currentUserId()` in the controller.
- [x] **`ReviewService`: review only a work you've borrowed** — `create` calls
      `loanRepository.hasEverBorrowed(userId, bookId)` before saving; false → **403**.
      **Decision:** "borrowed" = *ever picked up* (ACTIVE/OVERDUE/RETURNED), keyed on pickup as
      the moment of possession — not REQUESTED/APPROVED. **Boundary decision:** the "what counts
      as borrowed" policy is baked into a loan-side `@Query` (`hasEverBorrowed` navigates
      loan→bookCopy→book) rather than exposing the package-private `LoanStatus` to the `review`
      package. One-review-per-work stays the existing DB `UNIQUE` + `catch`→409.
- [x] **Optimistic locking** — `@Version long version` on `Loan` and `BookCopy` (migration
      **V10**, `BIGINT NOT NULL DEFAULT 0`). The race point is two approvals reserving the same
      copy: both read `version=0`, both flip `is_available`, first commit bumps to 1, the second's
      `UPDATE … WHERE version=0` matches 0 rows → `ObjectOptimisticLockingFailureException`. Now
      mapped to **409** by a new handler in `system.ValidationExceptionHandler` (was an unhandled
      500). Formal concurrency proof is Phase 21.
- [x] **`@Transactional`** (jakarta) on the two-entity writes (`approve`, `returnLoan`) so the
      loan + copy mutations commit atomically and dirty-checking flushes the copy without an
      explicit save. `pickup` (one entity) uses `saveAndFlush`.
- [x] **Authorization stays in-service (Decision A).** Loan-transition authz is
      *relationship-based* (caller must be the loaded loan's lender/borrower), so it lives in the
      service next to the state guard — NOT `@PreAuthorize`. Rejected the security-bean approach
      (`@loanSecurity.canApprove(#id, auth)`) because it re-loads the loan on every action for no
      benefit. This resolves the 2 `@PreAuthorize` rules Phase 5 deferred.

### Remaining (Phase 7)
Nothing — Phase 7 complete. ✅ NOTE: `returnLoan` only allows ACTIVE→RETURNED; once Phase 11
introduces the scheduled OVERDUE checker, widen that guard to accept OVERDUE too. The
concurrency/load proof (one of N concurrent borrowers wins) is Phase 21; unit/repo/API tests
are Phase 22.

## Phase 8 progress

### Done
- [x] **One global advice** — `system.GlobalExceptionHandler` (`@RestControllerAdvice`)
      **extends `ResponseEntityExceptionHandler`**. Decision: extend (not hand-roll) so we
      inherit Spring's `ProblemDetail` handling for ~15 framework exceptions (malformed body,
      missing params, type mismatch, ...) and only override what we want. Retired the old
      `ValidationExceptionHandler` (can't have two advices handling the same exception type).
- [x] **RFC 7807 everywhere** — every error is `application/problem+json` with `type`/`title`/
      `status`/`detail` (+ auto `instance`). `type` is a **root-relative** slug
      (`/problems/<x>`) — chosen over relative (`errors/<x>` resolves against the request URL,
      so the same error would render a different `type` per endpoint — defeats the stable key).
- [x] **Six domain exceptions, HTTP-agnostic** (no `HttpStatus` inside — the advice owns the
      mapping). Flat `RuntimeException` subclasses in their **feature packages** (package-by-
      feature, consistent with the rest of the repo), each carrying context + a `super(message)`
      that becomes the `detail`:
      - `book.BookNotFoundException` → 404
      - `loan.BookAlreadyLentException` (copy unavailable, in the *loan* pkg — it's a lending
        rule) → 409
      - `loan.LoanLimitExceededException` (carries borrowerId + max) → 409
      - `loan.UnauthorizedLoanActionException` (carries loanId + action) → 403
      - `loan.LoanNotFoundException` → 404  *(beyond README — reused by all 3 transitions)*
      - `loan.IllegalLoanStateException` (carries loanId + current + required state) → 409
        *(beyond README — wrong-state transitions)*
      **Heuristic locked:** named class when the failure is a domain concept reused across call
      sites or carries context; **inline `ResponseStatusException` for one-offs** (e.g. "can't
      borrow your own copy" — fires once, stays inline; Spring still renders it as ProblemDetail).
- [x] **Wiring** — `LoanService` create + all 3 transitions throw the domain exceptions;
      `BookService`/`BookController` not-found moved OUT of the controller (`Optional` →
      `notFound()`) INTO the service as `BookNotFoundException` (so `update`/`delete`/`getById`
      all flow through the advice). Other entities (User/BookCopy/Review/Wishlist) still 404 via
      the controller `Optional` pattern — see deferred.
- [x] **Validation → 422** — overrode `handleMethodArgumentNotValid` to return
      `UNPROCESSABLE_ENTITY` (was 400) and attach the field→message + global-error walk as an
      `errors` **extension member** (`pd.setProperty("errors", ...)`).
- [x] **Optimistic lock → 409** — ported from the old handler, now a `ProblemDetail`.
- [x] **Downstream failure handling: Redis fail-open** — `RateLimitFilter` wraps the Redis
      section in `try/catch (DataAccessException)`; on outage it logs a warning and lets the
      request PROCEED (rate limiter must not become a hard dependency — a cache blip shouldn't
      take the whole API down). `filterChain.doFilter` moved below the try so the allowed path
      and the fail-open path both reach it once, while the 429 path still returns early. Catch is
      `DataAccessException` (not bare `Exception`) so a real bug still surfaces. ES degrade is a
      no-op until Phase 12.
- [x] **Verified live** — empty register body → **422** `problem+json` with `errors`; malformed
      JSON → **400** `problem+json` (inherited framework handler); GET nonexistent book →
      **404** `problem+json` with our `type`/`title`/`detail`.

### Remaining (Phase 8)
Nothing — Phase 8 complete. ✅ Carry-overs (non-blocking): extend the domain-exception 404
pattern to User/BookCopy/Review/Wishlist for a fully uniform not-found body (or a generic
`ResourceNotFoundException`); malformed keyset cursor (Phase 3 flag) now renders as a generic
500-vs-ProblemDetail — could add a targeted handler; a catch-all `@ExceptionHandler(Exception)`
→ 500 ProblemDetail if we want to guarantee *nothing* leaks a stack trace.

## Phase 9 progress

### Done
- [x] **Expand/contract rename `summary` → `description`** (zero-downtime, three migrations).
      **V11** (expand): `ADD COLUMN description` nullable + `UPDATE ... SET description = summary`
      backfill. **V12** (contract): `DROP COLUMN summary` — gated on "nothing reads the old
      column" (grep-verified first). **Decision:** the two are split so they can straddle a code
      deploy — old code (reads `summary`) and new schema must coexist during a rolling deploy, so
      the drop can't share a migration with the add. Entity/JPQL/mapper updated to
      `description`; **the public API contract is unchanged** — `BookRequestDTO.summary` stays,
      and `BookMapper`/`BookService` translate it to the renamed column, so consumers never see
      the rename.
- [x] **Two-step NOT NULL on `description`** (**V13**). Step 1 backfills remaining NULLs with a
      placeholder (`'No description available'`); step 2 `ALTER COLUMN ... SET NOT NULL`.
      **Order is mandatory:** Postgres validates `SET NOT NULL` immediately via a full scan and
      rolls back if any NULL survives — so the backfill must precede the constraint. (At scale
      the production-safe form is `ADD CHECK (... IS NOT NULL) NOT VALID` → `VALIDATE CONSTRAINT`
      to avoid the long `ACCESS EXCLUSIVE` lock; overkill for this small table, noted in the SQL.)
- [x] **App-layer default for the new NOT NULL** — `BookService.descriptionOrDefault(...)`
      funnels both write paths (`create`/`update`); a null incoming `summary` becomes
      `DEFAULT_DESCRIPTION`. **Decision:** default in the *service*, not a DB column DEFAULT —
      Hibernate always lists mapped columns in its INSERT (an explicit NULL bypasses a column
      DEFAULT), so a DB default wouldn't reliably fire for an ORM-managed field. Mapper stays
      "dumb". Known tradeoff: an empty string `""` still passes through (guard is null-only).
- [x] **Programmatic transaction (`TransactionTemplate`) in `LoanService.create()`** — the FK
      resolution + 3 guards run OUTSIDE any tx (a failed guard throws before a tx opens); only
      the write runs inside `transactionTemplate.execute(...)`. **Decision:** programmatic (not
      `@Transactional`) to scope the tx to just the write and to own the exact block the outbox
      insert joins. Reviewed `approve()`/`returnLoan()`'s declarative `@Transactional` as correct
      (two-entity atomic writes + open persistence context for dirty-check flush).
- [x] **Transactional outbox — write side.** `outbox` table (**V14**): `id`, `event_type`,
      `payload JSONB`, `created_at` (ordering + queued-at), `processed_at` (nullable timestamp =
      publish marker; NULL = pending — chosen over a boolean to keep publish latency for free).
      `outbox.OutboxEvent` entity (`@JdbcTypeCode(SqlTypes.JSON)` binds the `String` payload to
      `jsonb`). In `create()`, inside the SAME `execute(...)` tx as the loan save: build a
      `{loanId, lenderId, borrowerId}` payload, serialize with the injected `ObjectMapper`, and
      `outboxRepository.save(...)` a `LOAN_REQUESTED` event → loan + event commit atomically,
      killing the dual-write problem.
- [x] **Transactional outbox — read side.** `@EnableScheduling` on the app; `outbox.OutboxPoller`
      `@Scheduled(fixedDelay = 5000)` drains `findByProcessedAtIsNullOrderByCreatedAtAsc()`
      (oldest first), "publishes" each (`log.info` for now — real RabbitMQ is Phase 11), then sets
      `processedAt = now()` and `save`s. The stamp is what drops the row from the next tick's
      query (no `processed_at` → infinite re-publish). Explicit `save()` needed: the method isn't
      `@Transactional`, so the fetched entities are detached and a bare setter wouldn't flush.
- [x] **Verified live end-to-end** — registered a borrower → JWT → `POST /loans` (**201**,
      REQUESTED). One `outbox` row written with `created_at` **7 ms** after the loan's
      `requestDate` (same tx); poller logged `Publishing outbox event LOAN_REQUESTED: {...}` and
      stamped `processed_at` to the same millisecond; event published **exactly once**, **0**
      unprocessed rows remaining (stamp correctly prevents re-publish).

### Remaining (Phase 9)
Nothing — Phase 9 complete. ✅ Carry-overs (non-blocking): the poller's "publish" is a log
placeholder until **Phase 11** wires the real RabbitMQ send (and at-least-once delivery means
consumers must be idempotent — the same event can re-publish if a publish succeeds but the stamp
fails); a partial `WHERE processed_at IS NULL` index on `outbox` would help once the table grows;
`FOR UPDATE SKIP LOCKED` on the drain query is the multi-instance-safe upgrade.

## Phase 10 progress

### Done
- [x] **Cache-aside reads** — `@Cacheable` on `BookService.getById` (region `books`, key `#id`)
      and `UserProfileService.getByUserId` (region `userProfiles`, key `#userId`). Annotations
      live on the **service** (a Spring proxy), never the controller, and never on a method the
      same class self-invokes (self-invocation bypasses the proxy). Verified live: 1st GET = 2 DB
      queries (miss → DB → populate), 2nd GET = 0 queries (Redis hit); key visible in `redis-cli`.
- [x] **Evict on write** — `@CacheEvict(key = "#id")` on `BookService.update` + `delete`;
      `@CacheEvict(value = userProfiles, key = "#userId")` on `UserProfileService.upsert`.
      **Decision:** evict, not `@CachePut` — CachePut only stays correct while the write's return
      value is byte-identical to what a read caches; it silently serves divergent data until TTL if
      that ever drifts, whereas evict self-heals on the next read. Verified: PUT wiped the key, next
      GET re-queried and returned the new title.
- [x] **Loan-approval evict = documented no-op** — README asks for an evict on approval (it flips
      `BookCopy.is_available`), but in **Model A** availability lives on `BookCopy` (uncached) and
      the cached `books` region (`BookResponseDTO`) carries no availability field. Nothing stale to
      evict; a comment in `LoanService.approve` says to add one if a cached read ever reflects copy
      availability.
- [x] **Per-region TTL + type-bound serialization** (`config.CacheConfig`, `@EnableCaching`).
      `RedisCacheManagerBuilderCustomizer` sets books=10m, userProfiles=30m (default 10m). **Each
      region caches exactly one type**, so each uses a `JacksonJsonRedisSerializer` bound to that
      concrete class (`BookResponseDTO` / `UserProfileResponseDTO`) — reads plain JSON straight back
      into the record, no `@class` hint. The generic `GenericJacksonJsonRedisSerializer` survives
      only as the default fallback (default typing on, validator restricted to `com.readshelf.` /
      `java.util.` / `java.time.`). String keys so they're greppable. **Boot 4 note:**
      `RedisCacheManagerBuilderCustomizer` moved to `org.springframework.boot.cache.autoconfigure`.
- [x] **Fail-open** (`config.CacheErrorConfig implements CachingConfigurer`) — overrides the
      default `SimpleCacheErrorHandler` (which rethrows → 500) with one that logs a warning and
      swallows GET/PUT/EVICT/CLEAR errors, so a cache op failure falls through to the method body
      (DB). **Decision:** a caching layer must never block reads *or* writes; evict-failure staleness
      is bounded by the region TTL. Same philosophy as Phase 8's rate-limiter fail-open.
- [x] **Bounded timeout is what makes fail-open fast** — the error handler only fires once a cache
      op *throws*; without a timeout Lettuce **blocks** ~33–60s (its default command timeout) before
      throwing. Set `spring.data.redis.timeout` + `connect-timeout` = **60ms** so a dead Redis fails
      fast. Verified live (Redis stopped, staying down): GET returned **200 in 0.19s** (was 33s),
      `Cache GET failed (serving from DB) … Redis command timed out` warning fired, 2 DB queries
      served the response. **Tradeoff (known):** 60ms is a *global* command timeout, so under real
      load/GC a healthy Redis could occasionally exceed it and get treated as down — a prod value
      typically sits higher (100–250ms); 60ms is tuned for localhost.

### Remaining (Phase 10)
Nothing — Phase 10 complete. ✅ Carry-overs (non-blocking): a 60ms global command timeout can
false-positive under load (raise for prod, or use a circuit breaker so a known-down Redis is
skipped entirely instead of paying the timeout on every request); caching is read-through only on
two hot GETs — widen if other reads get hot; no cache warming / no metrics on hit-rate yet.

## Phase 11 progress

### Done
- [x] **AMQP topology** (`config.RabbitConfig`) — ONE topic exchange `readshelf.events`, one durable
      queue per *logical consumer*. Phase 11 declares only `readshelf.email.queue` (`loan.#`);
      the search (`book.#`) and webhook (`#`) queues are **deferred to Phases 12 and 16** along with
      their consumers — see "Deferred queues" below. **Decision:** queue-per-consumer because RabbitMQ
      round-robins a queue to exactly ONE consumer (competing consumers) — two *different* consumers
      sharing a queue would split the stream. N instances of the *same* consumer sharing one queue is
      when that behaviour becomes the feature. No fanout exchange: "everything" is just the topic
      pattern `#`, and a second exchange would force the poller to publish twice and know the
      consumer topology. Verified: key `loan.requested` hit email + webhook, correctly missed search.
- [x] **Routing key stored on the outbox row** (`V15`, `OutboxEvent.routingKey`). **Decision:** store,
      don't derive from `event_type` at publish time. Deriving couples stored DB values to the wire
      protocol — a rename silently reroutes to a key no binding matches, and RabbitMQ **discards
      unroutable messages with no error**. Storing puts the routing decision at the producer, in the
      same tx as the business write. Same shape as Debezium's outbox event router.
- [x] **Poller publishes for real** (`OutboxPoller`) — raw bytes + explicit `application/json`
      content type, NOT via the message converter (the payload is already a JSON string; converting
      would double-encode it). `PERSISTENT` delivery mode (durable queue keeps the *definition*;
      persistence keeps the *messages* — you need both). `messageId` = outbox row id, the natural
      dedup key. Still **at-least-once** by design; the answer is idempotent consumers.
- [x] **Retry-then-DLQ failure policy** — all three queues carry `x-dead-letter-exchange` →
      `readshelf.events.dlx` → a **single shared** `readshelf.dlq`. **Decision:** one shared DLQ, not
      one per consumer — the queue-per-consumer argument rests on competing consumers, and *nothing
      consumes a DLQ*; it's an inspection bucket, and `x-death` preserves the original queue/routing
      key. `default-requeue-rejected: false` (Spring's default is `true` = the poison-message hot
      loop). Retry: 4 attempts, 2s → 4s → 8s.
- [x] **Permanent vs transient classification** — the test is *does anything change between attempt 1
      and attempt 4?* Missing loan → `AmqpRejectAndDontRequeueException`; SMTP failure → left to throw
      into the backoff. **Gotcha (found by testing, not by reading):** that exception does **not**
      skip retries on its own — the retry interceptor runs first and retries everything; the exception
      only controls the *destination* (DLX vs requeue). Fixed with a `RabbitListenerRetrySettingsCustomizer`
      whose predicate classifies permanent failures as non-retryable. Verified: 14.1s → **39ms**.
      Conversion failures (`MessageConversionException`, and the `IllegalArgumentException` the type
      mapper throws for an unmapped `__TypeId__`) are classified permanent too: 14s → **8ms**.
      **Boot 4 note:** `RabbitRetryTemplateCustomizer` → `RabbitListenerRetrySettingsCustomizer`, over
      Framework 7's new `org.springframework.core.retry` API.
- [x] **`EmailNotificationConsumer`** — class-level `@RabbitListener` + one `@RabbitHandler` per
      payload type, plus an `isDefault = true` handler that acks-and-ignores. **Why:** the queue is
      bound to `loan.#`, so it carries MIXED event types; a single method typed to `LoanRequestedEvent`
      would have had `loan.overdue` silently coerced into that shape. The poller stamps `__TypeId__`
      from `event_type` and `RabbitConfig.EVENT_TYPE_MAPPING` + `TypePrecedence.TYPE_ID` resolve it —
      `trustedPackages` restricted to `com.readshelf.event` (deserializing a header-named class is an
      RCE shape otherwise). **Add every new event type to that map** or it dead-letters.
- [x] **Thin events** (`event.LoanRequestedEvent`) — IDs only, no `lenderEmail`. **Decision:** fattening
      the event with one consumer's needs leaks that consumer into the producer, and freezes a snapshot
      that goes stale before a replayed/dead-lettered message is retried. Consumers look up current
      state. (Event-carried state transfer is the legitimate opposite trade at high volume.)
- [x] **`LoanNotificationView` projection** — replaced `findById`/`JOIN FETCH` in the consumer.
      `open-in-view: false` + no listener transaction means lazy proxies blow up after the session
      closes; and `User.userProfile` is `@OneToOne(mappedBy)` which is **EAGER by default and cannot be
      made lazy without bytecode enhancement**, so loading two users cost 2 extra queries for data
      nobody read. Projecting two columns: 3 queries → **1**. (Plain `JOIN`, not `JOIN FETCH` —
      illegal in a constructor expression. Inner join is safe only because both FKs are `NOT NULL`.)
- [x] **`readshelf.mail.from`** externalized (`${MAIL_FROM:noreply@readshelf.com}`), constructor-injected.
      Sender is the *app*, not the borrower: forging a `From` you don't own fails SPF/DKIM in prod, and
      it leaked the borrower's address to the lender before approval.

- [x] **`@Scheduled` overdue sweep** (`loan.OverdueLoanChecker`) — flips past-due `ACTIVE` → `OVERDUE`
      and writes a `LOAN_OVERDUE` outbox row. **Decision: emit-once, not a nightly reminder.**
      `loan.overdue` is past tense and means "this loan BECAME overdue" — that happens once. The
      state machine enforces it for free: the query only selects `ACTIVE`, so a flipped loan can
      never be picked up again. No dedup table, no `already_notified` column. This is load-bearing
      for idempotency — if it fired nightly, a consumer could no longer distinguish a duplicate
      delivery from a legitimate second night, and `messageId` would stop being a usable dedup key.
      A nightly *nudge*, if ever wanted, is a separate event, not a lie about this one.
      **Decision: one transaction PER LOAN**, not one around the sweep — a deterministically bad row
      would otherwise roll back the batch and block every other overdue loan again every night
      (same shape as dead-lettering one message instead of stalling the queue). Note that the
      per-loan transaction bounds the *rollback*, not the *loop*: `executeWithoutResult` rethrows, so
      a `try/catch` inside the loop is what keeps the remaining loans alive. Failure counts +
      summary log so "500 flagged" and "0 flagged, 500 failed" don't look alike. Cron externalized
      (`readshelf.jobs.overdue-check-cron`, default `0 0 2 * * *`) so it's testable without editing
      code. **Verified live:** 7 flagged → 7 events published → email consumer's `isDefault` handler
      acked and ignored them (correctly NOT dead-lettered); next tick 20s later found nothing.
- [x] **Remaining producers** — `LOAN_APPROVED` (in `LoanService.approve`, which is already
      `@Transactional`, so the outbox row simply joins that transaction), plus `BOOK_CREATED` /
      `BOOK_UPDATED` in `BookService`. `create()`'s payload switched from `Map.of(...)` to the
      `LoanRequestedEvent` record — with a Map, adding a field to the event leaves the producer
      untouched and the consumer silently gets null; with the record it's a compile error.
      **Verified live:** POST /books → 201 → `BOOK_CREATED` row → published as `book.created` →
      discarded (no binding until Phase 12, as designed). Same for PUT → `BOOK_UPDATED`.
- [x] **`BookService` transaction boundary is a `TransactionTemplate`, NOT `@Transactional`** —
      because `update()` also carries `@CacheEvict`. Eviction defaults to `beforeInvocation=false`
      (fires after the method returns), both are advice on the same proxy call, and Spring does not
      guarantee their relative order. With `@Transactional`, the evict can run BEFORE the commit; in
      that window a concurrent `getById` misses, reads the still-uncommitted OLD row, and
      repopulates the cache — which then stays stale until the region TTL, because the eviction
      already happened and nothing fires another. Committing inside the body makes the order
      explicit. `create()` uses the same shape for consistency (it has nothing to evict).
      **Not proven by test:** the happy path passes under either ordering since it's single
      threaded; provoking the race needs a concurrent reader (Phase 21).
- [x] **Scheduler pool size = 2** (`spring.task.scheduling.pool.size`). Spring's default is **1**, so
      every `@Scheduled` method shares one thread — evidenced live: both `OverdueLoanChecker` and
      `OutboxPoller` logged from `[scheduling-1]`. A long sweep would stall event publishing for its
      whole duration (no loss, the outbox is durable, but events stop flowing). **Decision: this is
      job isolation, not tuning** — the correct value is "how many jobs must not block each other,"
      which needs no load measurement, which is why it belongs here and not in Phase 21.
- [x] **`@Async` moved to Phase 21** — it was listed in both phases; Phase 21 already has it next to
      the Gatling load test that makes pool sizing defensible instead of invented. Phase 11 uses no
      `@Async` at all, and shouldn't: `@Async` is fire-and-forget on a local pool, so work dies with
      the JVM — exactly the guarantee gap the outbox + broker was built to close. The broker IS the
      async mechanism here. RabbitMQ listener concurrency moved to Phase 21 for the same reason
      (genuine tuning, needs load).
- [x] **`returnLoan` accepts `OVERDUE`** (Phase 7 carry-over, now urgent because the sweep made
      `OVERDUE` reachable — all 7 swept loans were briefly unreturnable). `RETURNABLE_STATES`
      (`EnumSet` of `ACTIVE`, `OVERDUE`) is the single source of truth, feeding both the guard and
      the error message. `IllegalLoanStateException` widened to varargs → `Set<LoanStatus>` so a 409
      can say "must be one of [ACTIVE, OVERDUE]" instead of naming one state and misleading the
      caller; single-state call sites (`approve`, `pickup`) are unchanged and keep identical
      messages. **Compile-verified only** — the endpoint was not exercised (needs a borrower JWT).

- [x] **Deferred queues — `SearchIndexConsumer` → Phase 12, `WebhookDispatchConsumer` → Phase 16.**
      The README lists both under Phase 11, but Phase 12 owns the ES index mapping / query side and
      Phase 16 owns `webhook_subscriptions`, HMAC signing, retry backoff, and delivery records. A
      consumer that logs instead of delivering isn't the README item, it's a placeholder pretending
      to be one. **Their QUEUES are deferred too** (removed from `RabbitConfig`, deleted from the
      broker): a queue with no consumer is a growing pile, and buffering events for a consumer two
      phases out is storing garbage. Each phase declares its own queue + binding alongside its
      consumer, and **no producer changes when it does** — the payoff of publishing to an exchange
      rather than a queue. **Known consequence:** until those queues exist, `book.created` matches no
      binding and RabbitMQ discards it silently (no error, no dead letter). Acceptable while nothing
      consumes book events; do not forget it when Phase 12 starts.

- [x] **`@RabbitHandler` for `LoanApprovedEvent` + `LoanOverdueEvent`** — both previously hit the
      `isDefault` handler and were acked without an email. **Recipient for both is the BORROWER**
      (loan.requested is the only one that emails the lender), which inverted what the projection
      needed: `LoanNotificationView` grew from `(lenderEmail, borrowerUsername)` to carry both
      parties' email AND username, so one query serves both directions instead of two
      near-identical ones.
      **Payload vs re-read, decided per field:** the overdue email takes `dueDate` from the
      **event payload**, not from the DB — deliberately against this phase's own "events carry
      IDs, consumers re-read" rule. `dueDate` isn't current state here, it's the fact the event
      *asserts* (the sweep found this loan past due **as of that date**). If the loan were renewed
      between the sweep and the send — retries, a hand-replayed DLQ message — a re-read would put
      a **future** date in an email telling someone they're late. Recipient details still come
      from the projection, because those genuinely are current state.
      Verified by publishing both types straight to the exchange with `__TypeId__` headers
      (consumer-side test; the producers were verified end-to-end earlier): MailHog received
      `To: borrow13064@x.com / Loan Overdue / "Your loan from lender13064 was due on
      2026-07-01T00:00:00Z"` and the matching Loan Approved, with the DLQ unchanged.
      *Known wart:* `Instant.toString()` in a user-facing email is ugly; formatting it means
      picking a timezone to render in, which is a real decision, left open.
- [x] **`OutboxCleanupJob` — nightly, deletes PUBLISHED rows older than a week.** Nothing else ever
      deleted from `outbox` (the poller only stamps `processedAt`), so the table grew forever and
      the poller's "find work" query scanned all of it to find the same few unprocessed rows.
      **Why keep them at all for a week:** a surviving row answers "did we ever emit an event for
      this?", which `loans` cannot. Without it, a missing notification is ambiguous between "never
      written" and "written, sent, cleaned up" — different bugs, different fixes. A week covers
      "someone noticed and asked."
      **`processed_at IS NOT NULL` is load-bearing, not a filter for tidiness:** an OLD *unpublished*
      row is a **stuck event**, not garbage. Deleting it would destroy the only evidence and drop
      the event permanently, so the query cannot see those rows at all.
      **Batched** — 500 rows per transaction, one transaction *per batch*, capped at 100 batches
      per run. A single `DELETE` of the whole backlog holds row locks and accumulates dead tuples
      for the entire transaction; a single transaction wrapped around the loop would do the same
      thing and defeat the batching. `LIMIT` lives in a subquery because Postgres has no
      `DELETE ... LIMIT`; native rather than JPQL for the same reason (and it's a set operation
      with no entities to load). Cutoff is computed **once**, outside the loop, so it can't drift
      forward mid-run and make eligibility depend on how long the run took.
      Verified in two halves: the exact `DELETE` run in psql against a 30-day-old *unprocessed*
      sentinel → `DELETE 5` (LIMIT respected), processed rows 18→13, **sentinel survived**; then
      the job itself at `*/20 * * * * *` + `PT1S` retention → `deleted 13 rows`, next tick
      `nothing published before …`, table at 0.
- [x] **Scheduler pool size 2 → 3** alongside the cleanup job — same rule as before, the value
      tracks the number of jobs that must not block each other.
- [x] **`LoanService.create()` payload is a typed record**, not `Map.of(...)` — a new field on
      `LoanRequestedEvent` is now a compile error instead of a silently-missing key.

### Remaining (Phase 11)
Nothing — Phase 11 complete. ✅

**`BookService.delete` publishes nothing → moved to Phase 12.** Same reasoning that deferred the
search and webhook queues: the only consumer for a `BookDeletedEvent` is index removal, and the
index doesn't exist until Phase 12. Publishing it now would mean an event with no binding, which
RabbitMQ discards silently — a producer written against a consumer nobody has built. Phase 12 adds
the event, the `EVENT_TYPE_MAPPING` entry, and the producer call together. **Known consequence
until then: a deleted book would linger in the search index** — which is only a real defect once
there IS an index.

## Conventions locked this phase
- **Layering:** Controller = HTTP only; `@Service` = logic + entity↔DTO (owns the
  mapper + repo, takes/returns DTOs); Mapper = `@Component` implementing generic
  `utils.ObjectMapper<Req,Resp,Entity>`; DTOs = records.
- **update():** fetch-then-mutate (preserves created_at/id; @UpdateTimestamp bumps updated_at).
- See `phase2_crud_layering` memory for the full convention.

## ⚠️ Temporary / deferred
- ~~`config.SecurityConfig` = permit-all~~ — **RESOLVED in Phase 5** (stateless JWT +
  `@PreAuthorize`). See Phase 5 progress.
- ~~`@Transactional` on service write methods → Phase 7~~ — **RESOLVED in Phase 7** on the
  two-entity loan writes (`approve`, `returnLoan`). Single-entity writes still rely on
  `save`/`saveAndFlush`'s own tx.
- ⚠️ **Auth 401/403 bodies are NOT problem+json yet.** The security `AuthenticationEntryPoint`
  / `AccessDeniedHandler` write the response *before* the DispatcherServlet, so
  `GlobalExceptionHandler` never sees them — a rejected request still gets the default Spring
  `/error` JSON (`{timestamp,status,error,message,path}`), confirmed live. To unify, the entry
  point/denied handler must write a `ProblemDetail` themselves. Deferred (Phase 17 hardening).
- 🔴 keyset/cursor pagination query (loans) → Phase 3.
- ISBN normalization: `@ISBN` accepts hyphens, so the same ISBN in two formats can
  create two rows (UNIQUE doesn't catch it). Normalize-before-save → later.
- Naming nit: `utils.ObjectMapper` collides conceptually with Jackson's `ObjectMapper`
  (suggested rename to `EntityMapper`, not yet done).
- ⚠️ **Jackson 3 on Spring Boot 4:** the auto-configured bean is `tools.jackson.databind.
  ObjectMapper` (databind/core moved to the `tools.jackson` package); the Jackson-2
  `com.fasterxml.jackson.databind.ObjectMapper` has no bean and won't autowire. Annotations
  stayed at `com.fasterxml.jackson.annotation.*`. Jackson 3 exceptions are **unchecked**
  (`JacksonException extends RuntimeException`) — no `try/catch` forced on `writeValueAsString`.
- ⚠️ **`V15` broke the expand/contract rule** — it adds `routing_key`, backfills, AND sets `NOT NULL`
  in one migration, while an older build that doesn't populate the column was still running. That's
  a new constraint deployed ahead of the code that satisfies it — exactly what splitting V11/V12 was
  meant to avoid. Correct sequencing: V15 adds nullable → deploy code → V16 sets `NOT NULL`. Harmless
  on localhost with one instance; do not copy this shape into a real deployment.
- Phase 11 consumers are **not idempotent yet**. The outbox is at-least-once, so a duplicate delivery
  currently sends a duplicate email. `messageId` (the outbox row id) is the dedup key when that's worth
  addressing — re-indexing a document is naturally safe, sending an email twice is not.
- Rate limiter (Phase 6): not atomic (3 round-trips) → Lua script later; `getRemoteAddr()`
  is the proxy IP behind a LB → `X-Forwarded-For` later; limits are constants → could be
  `@ConfigurationProperties`. Full structured-JSON logging (`logback-spring.xml`) → Phase 18.

## Phase boundary reminder
Basic CRUD = Phase 2. REST polish (pagination, nested routes, 409, ETags, HATEOAS,
v2 shapes) = Phase 3. Validation depth + custom constraints = Phase 4. Auth (JWT,
refresh rotation, `@PreAuthorize`) = Phase 5. Cross-cutting filters + request context
(correlation id, request logging, rate limit, `RequestContext`) = Phase 6. Loan state
transitions + their ownership rules + service `@Transactional` = Phase 7. RFC 7807 problem
details + domain exceptions + global advice + Redis fail-open = Phase 8. DB transactions
(programmatic `TransactionTemplate` + transactional outbox) + painful migrations
(expand/contract rename, two-step NOT NULL) = Phase 9. Redis-backed caching (cache-aside
`@Cacheable`/`@CacheEvict`, per-region TTL, fail-open + bounded timeout) = Phase 10. Real broker
publish of outbox events = Phase 11.

## Working agreement
Learning project. Claude handles scaffolding/config/boilerplate (incl. pure
pattern-replication); human owns new logic, patterns, and design decisions. Guide
with Polya; hand off `TODO(human)` only where there's a genuine decision.

## Run locally
```
docker compose up -d        # or let spring-boot-docker-compose manage it
./mvnw spring-boot:run
```