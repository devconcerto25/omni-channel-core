package com.concerto.omnichannel.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;

public class ValidChannelValidator implements ConstraintValidator<ValidChannel, String> {

    private static final List<String> VALID_CHANNELS = Arrays.asList("POS", "ATM", "UPI", "BBPS", "PG");

    @Override
    public void initialize(ValidChannel constraintAnnotation) {
        // Initialization code if needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return VALID_CHANNELS.contains(value.toUpperCase());
    }
}

