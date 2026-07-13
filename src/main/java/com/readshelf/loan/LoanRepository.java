package com.readshelf.loan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
}