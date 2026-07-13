package com.readshelf.loan;

import com.readshelf.utils.CursorPage;
import com.readshelf.utils.Link;
import com.readshelf.utils.PagedResponse;
import com.readshelf.web.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<LoanResponseDTO>> listLoans(
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "REQUEST_DATE") LoanSortField sortBy
    ) {
        return ResponseEntity.ok(loanService.findAll(page, size, sortBy));
    }

    // Keyset (cursor) pagination — coexists with the offset GET above so both are demoable.
    // First page: omit ?cursor. Each response carries nextCursor; echo it back to page on.
    @GetMapping("/keyset")
    public ResponseEntity<CursorPage<LoanResponseDTO>> listLoansByCursor(
            @RequestParam(required = false) String cursor,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(loanService.findByCursor(cursor, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanResponseDTO> getLoan(@PathVariable UUID id) {
        return loanService.findById(id)
                .map(loan -> loan.withLinks(buildLinks(loan)))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Builds the HAL _links for a loan as ABSOLUTE URLs (needs request context, hence here
    // in the controller rather than the mapper).
    private Map<String, Link> buildLinks(LoanResponseDTO loan) {
        String selfHref = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/loans/{id}")
                .buildAndExpand(loan.id())
                .toUriString();
        String lenderHref = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/users/{id}")
                .buildAndExpand(loan.lenderId())
                .toUriString();
        String borrowerHref = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/users/{id}")
                .buildAndExpand(loan.borrowerId())
                .toUriString();
        String bookCopyHref = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/book-copies/{id}")
                .buildAndExpand(loan.bookCopyId())
                .toUriString();
        Map<String, Link> linkMap = new HashMap<>();
        linkMap.put("self", new Link(selfHref, "GET"));
        linkMap.put("lender", new Link(lenderHref, "GET"));
        linkMap.put("borrower", new Link(borrowerHref, "GET"));
        linkMap.put("bookCopy", new Link(bookCopyHref, "GET"));

        return linkMap;
    }

    @PostMapping
    public ResponseEntity<LoanResponseDTO> createLoan(@Valid @RequestBody LoanRequestDTO request) {
        LoanResponseDTO created = loanService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    // --- State transitions (Phase 7) ---
    // Each is a dedicated action endpoint (not a blunt PUT). The caller's identity comes
    // from the security context; the service uses it to authorize the specific transition.

    @PostMapping("/{id}/approve")
    public ResponseEntity<LoanResponseDTO> approveLoan(@PathVariable UUID id) {
        UUID callerId = currentUserId();
        return ResponseEntity.ok(loanService.approve(id, callerId));
    }

    // APPROVED -> ACTIVE. Borrower confirms they now physically have the copy.
    @PostMapping("/{id}/pickup")
    public ResponseEntity<LoanResponseDTO> pickupLoan(@PathVariable UUID id) {
        UUID callerId = currentUserId();
        return ResponseEntity.ok(loanService.pickup(id, callerId));
    }

    // ACTIVE -> RETURNED. Borrower hands the copy back; the copy becomes available again.
    @PostMapping("/{id}/return")
    public ResponseEntity<LoanResponseDTO> returnLoan(@PathVariable UUID id) {
        UUID callerId = currentUserId();
        return ResponseEntity.ok(loanService.returnLoan(id, callerId));
    }

    // Authenticated by SecurityConfig (anyRequest().authenticated()), so this is always present.
    private UUID currentUserId() {
        return RequestContext.currentUserId()
                .map(UUID::fromString)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLoan(@PathVariable UUID id) {
        return loanService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}