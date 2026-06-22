package com.readshelf.loan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {
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