package com.readshelf.system;

import com.readshelf.book.BookNotFoundException;
import com.readshelf.loan.BookAlreadyLentException;
import com.readshelf.loan.LoanLimitExceededException;
import com.readshelf.loan.UnauthorizedLoanActionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 8 — one global advice, one response shape (RFC 7807 ProblemDetail).
 *
 * Extends ResponseEntityExceptionHandler so we inherit Spring's ProblemDetail handling
 * for framework exceptions (malformed body, missing params, bean validation, ...) and
 * override only what we want to change. Our four domain exceptions get their own
 * @ExceptionHandler methods below.
 *
 * Replaces the old ValidationExceptionHandler (delete that once this covers validation
 * + optimistic lock).
 *
 * Convention for building a ProblemDetail:
 *   ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
 *   pd.setType(URI.create("https://readshelf.example/problems/<slug>"));  // stable machine key
 *   pd.setTitle("Human-readable summary of the type");
 *   // optional extension members: pd.setProperty("bookId", ...);
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // TODO(human): domain-exception handlers.
    //   Add one @ExceptionHandler method per domain exception, each returning a
    //   ProblemDetail with the right status + a distinct `type` slug + `title`:
    //     - BookNotFoundException          -> 404 NOT_FOUND
    //     - BookAlreadyLentException       -> 409 CONFLICT
    //     - LoanLimitExceededException     -> 409 CONFLICT
    //     - UnauthorizedLoanActionException-> 403 FORBIDDEN
    //   ex.getMessage() already reads well (you built it in the constructor) -> use it
    //   as the `detail`.
    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleBookNotFoundException(BookNotFoundException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problemDetail.setTitle("Book Not Found");
        problemDetail.setType(URI.create("/problems/book-not-found"));
        return problemDetail;
    }

    @ExceptionHandler(BookAlreadyLentException.class)
    public ProblemDetail handleBookAlreadyLentException(BookAlreadyLentException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problemDetail.setTitle("Book Already Lent");
        problemDetail.setType(URI.create("/problems/book-already-lent"));
        return problemDetail;
    }

    @ExceptionHandler(LoanLimitExceededException.class)
    public ProblemDetail handleLoanLimitExceededException(LoanLimitExceededException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problemDetail.setTitle("Loan Limit Exceeded");
        problemDetail.setType(URI.create("/problems/loan-limit-exceeded"));
        return problemDetail;
    }

    @ExceptionHandler(UnauthorizedLoanActionException.class)
    public ProblemDetail handleUnauthorizedLoanActionException(UnauthorizedLoanActionException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problemDetail.setTitle("Unauthorized Loan Action");
        problemDetail.setType(URI.create("/problems/unauthorized-loan-action"));
        return problemDetail;
    }

    // Ported from the old ValidationExceptionHandler, now emitting a ProblemDetail.
    // The race point is two loan approvals reserving the same copy (see LoanService).
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The record you tried to update was modified concurrently. Reload and try again.");
        problemDetail.setTitle("Concurrent Modification");
        problemDetail.setType(URI.create("/problems/optimistic-lock"));
        return problemDetail;
    }

    /**
     * Override Spring's bean-validation handler: return 422 (not the default 400) and
     * attach field -> message details as an "errors" extension member. Walks both
     * getFieldErrors() (field-level) and getGlobalErrors() (class-level, e.g. @NoSelfLoan,
     * keyed by object name) — the same walk the old ValidationExceptionHandler did.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        for (var fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        for (var globalError : ex.getBindingResult().getGlobalErrors()) {
            errors.put(globalError.getObjectName(), globalError.getDefaultMessage());
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "One or more fields are invalid.");
        problemDetail.setTitle("Validation Failed");
        problemDetail.setType(URI.create("/problems/validation-failed"));
        problemDetail.setProperty("errors", errors);

        return ResponseEntity.unprocessableEntity().body(problemDetail);
    }
}
