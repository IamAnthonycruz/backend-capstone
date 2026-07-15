# ReadShelf — Status

_Last updated: 2026-07-14_

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
- Rate limiter (Phase 6): not atomic (3 round-trips) → Lua script later; `getRemoteAddr()`
  is the proxy IP behind a LB → `X-Forwarded-For` later; limits are constants → could be
  `@ConfigurationProperties`. Full structured-JSON logging (`logback-spring.xml`) → Phase 18.

## Phase boundary reminder
Basic CRUD = Phase 2. REST polish (pagination, nested routes, 409, ETags, HATEOAS,
v2 shapes) = Phase 3. Validation depth + custom constraints = Phase 4. Auth (JWT,
refresh rotation, `@PreAuthorize`) = Phase 5. Cross-cutting filters + request context
(correlation id, request logging, rate limit, `RequestContext`) = Phase 6. Loan state
transitions + their ownership rules + service `@Transactional` = Phase 7. RFC 7807 problem
details + domain exceptions + global advice + Redis fail-open = Phase 8.

## Working agreement
Learning project. Claude handles scaffolding/config/boilerplate (incl. pure
pattern-replication); human owns new logic, patterns, and design decisions. Guide
with Polya; hand off `TODO(human)` only where there's a genuine decision.

## Run locally
```
docker compose up -d        # or let spring-boot-docker-compose manage it
./mvnw spring-boot:run
```