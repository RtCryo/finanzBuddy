package com.finanzbuddy.api.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string's UTF-8 byte length is within the specified bounds.
 *
 * <p>Useful for values that are later processed by algorithms with byte-length limits, such as
 * BCrypt (which silently truncates passwords longer than 72 bytes).
 */
@Constraint(validatedBy = ByteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ByteLength {

    String message() default "Value must be between {min} and {max} bytes";

    int min() default 0;

    int max() default Integer.MAX_VALUE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
