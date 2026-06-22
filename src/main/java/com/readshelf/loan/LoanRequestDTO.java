package com.readshelf.loan;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

// Create a loan request. status is server-set (REQUESTED); requestDate is @CreationTimestamp.
// dueDate is optional at request time. lender/borrower/bookCopy resolved by the service.
public record LoanRequestDTO(
        @NotNull UUID lenderId,
        @NotNull UUID borrowerId,
        @NotNull UUID bookCopyId,
        Instant dueDate
) {
}