package com.readshelf.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

/**
 * Field-level constraint for a loan due date: must fall within [now, now + maxDays].
 * Validated by {@link ValidDateRangeValidator}. A null value is treated as valid
 * (use @NotNull separately if the date is required).
 */
@Documented
@Constraint(validatedBy = ValidDateRangeValidator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateRange {

    /** Maximum number of days from now that the due date may be set. */
    int maxDays() default 90;

    String message() default "due date must be in the future and within the allowed loan window";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}