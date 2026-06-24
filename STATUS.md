# ReadShelf — Status

_Last updated: 2026-06-19_

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

## Conventions locked this phase
- **Layering:** Controller = HTTP only; `@Service` = logic + entity↔DTO (owns the
  mapper + repo, takes/returns DTOs); Mapper = `@Component` implementing generic
  `utils.ObjectMapper<Req,Resp,Entity>`; DTOs = records.
- **update():** fetch-then-mutate (preserves created_at/id; @UpdateTimestamp bumps updated_at).
- See `phase2_crud_layering` memory for the full convention.

## ⚠️ Temporary / deferred
- **`config.SecurityConfig` = permit-all + CSRF disabled** — DEV ONLY so CRUD is
  testable without the default generated password. **MUST be replaced in Phase 5**
  with stateless JWT + `@PreAuthorize`. (Spring Security is on the classpath.)
- `@Transactional` on service write methods → Phase 7.
- 🔴 keyset/cursor pagination query (loans) → Phase 3.
- ISBN normalization: `@ISBN` accepts hyphens, so the same ISBN in two formats can
  create two rows (UNIQUE doesn't catch it). Normalize-before-save → later.
- Naming nit: `utils.ObjectMapper` collides conceptually with Jackson's `ObjectMapper`
  (suggested rename to `EntityMapper`, not yet done).

## Phase boundary reminder
Basic CRUD = Phase 2. REST polish (pagination, nested routes, 409, ETags, HATEOAS,
v2 shapes) = Phase 3. Validation depth + custom constraints = Phase 4.

## Working agreement
Learning project. Claude handles scaffolding/config/boilerplate (incl. pure
pattern-replication); human owns new logic, patterns, and design decisions. Guide
with Polya; hand off `TODO(human)` only where there's a genuine decision.

## Run locally
```
docker compose up -d        # or let spring-boot-docker-compose manage it
./mvnw spring-boot:run
```