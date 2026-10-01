package com.finanzbuddy.api.auth.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ByteLengthValidatorTest {

    private final ByteLengthValidator validator = new ByteLengthValidator();

    @Test
    void nullIsValid() {
        validator.initialize(annotated(6, 72));
        assertThat(validator.isValid(null, context())).isTrue();
    }

    @Test
    void asciiWithinBoundsIsValid() {
        validator.initialize(annotated(6, 72));
        assertThat(validator.isValid("password123", context())).isTrue();
    }

    @Test
    void tooShortIsInvalid() {
        validator.initialize(annotated(6, 72));
        assertThat(validator.isValid("short", context())).isFalse();
    }

    @Test
    void tooLongByBytesIsInvalid() {
        validator.initialize(annotated(6, 10));
        // 12 bytes (4 cyrillic characters x 3 bytes each), but only 4 characters.
        assertThat(validator.isValid("пароль", context())).isFalse();
    }

    private ByteLength annotated(int min, int max) {
        ByteLength annotation = Mockito.mock(ByteLength.class);
        Mockito.when(annotation.min()).thenReturn(min);
        Mockito.when(annotation.max()).thenReturn(max);
        return annotation;
    }

    private ConstraintValidatorContext context() {
        return Mockito.mock(ConstraintValidatorContext.class);
    }
}
