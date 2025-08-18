package com.concerto.omnichannel.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidChannelValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidChannel {
    String message() default "Invalid channel";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
