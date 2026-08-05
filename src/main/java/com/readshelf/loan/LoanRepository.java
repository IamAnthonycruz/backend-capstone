package com.readshelf.loan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    // Derived query: counts a borrower's loans whose status is in the given set.
    // Used to enforce the "max N active loans" rule (active = APPROVED/ACTIVE/OVERDUE).
    long countByBorrower_IdAndStatusIn(UUID borrowerId, Collection<LoanStatus> statuses);

    // Has this borrower ever picked up a copy of this book? Navigates loan -> bookCopy
    // -> book. The "picked-up" policy (ACTIVE/OVERDUE/RETURNED) is baked in HERE so the
    // review module can ask the question without knowing loan statuses. Enforces
    // "you can only review a work you've borrowed."
    @Query("""
            SELECT COUNT(l) > 0 FROM Loan l
            WHERE l.borrower.id = :borrowerId
              AND l.bookCopy.book.id = :bookId
              AND l.status IN (com.readshelf.loan.LoanStatus.ACTIVE,
                               com.readshelf.loan.LoanStatus.OVERDUE,
                               com.readshelf.loan.LoanStatus.RETURNED)
            """)
    boolean hasEverBorrowed(@Param("borrowerId") UUID borrowerId, @Param("bookId") UUID bookId);

    @Query(value = "SELECT * FROM loans " +
            "WHERE (due_date, id) > (:dueDate, :id) AND due_date IS NOT NULL " +
            "ORDER BY due_date ASC, id ASC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Loan> findLoansAfterCursor(
            @Param("dueDate") Instant dueDate,
            @Param("id") UUID id,
            @Param("limit") int limit
    );

    // First page: no cursor yet, so no tuple predicate — just the start of the ordering.
    @Query(value = "SELECT * FROM loans " +
            "WHERE due_date IS NOT NULL " +
            "ORDER BY due_date ASC, id ASC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Loan> findFirstPage(@Param("limit") int limit);

    // Notification lookup for the AMQP listeners. Projects the columns an email actually needs
    // instead of loading the Loan entity, which would pull in lender + borrower and then one
    // eager UserProfile per user — three queries for a handful of strings.
    //
    // Plain JOIN, not JOIN FETCH: a constructor expression has no entity to hang a fetched
    // association off, so FETCH is illegal here. Inner JOIN is safe only because lender_id and
    // borrower_id are NOT NULL — if either were nullable, a real loan would return zero rows
    // and the consumer would misread that as "deleted" and permanently reject the message.
    @Query("""
            SELECT new com.readshelf.loan.LoanNotificationView(
                       lu.email, lu.username, bu.email, bu.username)
            FROM Loan l
            JOIN l.lender lu
            JOIN l.borrower bu
            WHERE l.id = :id
            """)
    Optional<LoanNotificationView> findNotificationView(@Param("id") UUID id);

    List<Loan> findByStatusAndDueDateBefore(LoanStatus status, Instant cutoff);
}