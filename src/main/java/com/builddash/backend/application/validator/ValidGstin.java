package com.builddash.backend.application.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Format-only validation of the 15-character GSTIN structure. Does not call any external
 * GST-portal verification — that's a future-phase async check (see {@code gstinStatus}).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GstinValidator.class)
public @interface ValidGstin {

    String message() default "GST number format is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
