package com.readshelf.validation;

import com.readshelf.loan.LoanRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoSelfLoanValidator implements ConstraintValidator<NoSelfLoan, LoanRequestDTO> {

    @Override
    public boolean isValid(LoanRequestDTO value, ConstraintValidatorContext context) {
        // 1. Handle null value object according to JSR-380 specifications
        if (value == null) {
            return true;
        }

        // 2. Safely check for internal nulls before evaluating business logic
        if (value.lenderId() == null || value.borrowerId() == null) {
            return true;
        }

        // 3. Main business constraint: lender cannot be borrower
        return !value.lenderId().equals(value.borrowerId());
    }
}