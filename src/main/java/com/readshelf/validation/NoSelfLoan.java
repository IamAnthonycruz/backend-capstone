package com.readshelf.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint: a loan's lender and borrower must be different users.
 * Validated by {@link NoSelfLoanValidator}, which receives the whole DTO so it
 * can compare the two fields.
 */
@Documented
@Constraint(validatedBy = NoSelfLoanValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoSelfLoan {

    String message() default "lender and borrower must be different users";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
