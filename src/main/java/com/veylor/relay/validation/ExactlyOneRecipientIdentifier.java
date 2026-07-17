package com.veylor.relay.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExactlyOneRecipientIdentifierValidator.class)
@Documented
public @interface ExactlyOneRecipientIdentifier {
    String message() default "Exactly one of email or recipientId must be provided";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
