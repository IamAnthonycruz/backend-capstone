package com.readshelf.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, Instant> {

    private int maxDays;

    @Override
    public void initialize(ValidDateRange constraint) {
        // Called once before validation; captures the annotation's parameter so isValid can use it.
        this.maxDays = constraint.maxDays();
    }

    @Override
    public boolean isValid(Instant value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        Instant now = Instant.now();
        Instant maxDueDate = now.plus(java.time.Duration.ofDays(this.maxDays));

        // 2. Validate that 'value' is within [now, now + maxDays] inclusive
        // value must not be before 'now' AND must not be after 'maxPastDate'
        if (value.isBefore(now) || value.isAfter(maxDueDate)){
            return false;
        }

        return true;
    }
}