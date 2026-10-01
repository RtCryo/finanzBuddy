package com.finanzbuddy.api.auth.adapter.in.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ByteLengthValidator implements ConstraintValidator<ByteLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(ByteLength constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotBlank handle null/empty values.
        }
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        return byteLength >= min && byteLength <= max;
    }
}
