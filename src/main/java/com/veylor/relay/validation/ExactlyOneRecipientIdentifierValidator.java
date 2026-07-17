package com.veylor.relay.validation;

import com.veylor.relay.dto.NotificationItem;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExactlyOneRecipientIdentifierValidator implements ConstraintValidator<ExactlyOneRecipientIdentifier, NotificationItem> {

    @Override
    public boolean isValid(NotificationItem value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        boolean hasEmail = value.getEmail() != null && !value.getEmail().trim().isEmpty();
        boolean hasRecipientId = value.getRecipientId() != null;
        
        return (hasEmail && !hasRecipientId) || (!hasEmail && hasRecipientId);
    }
}
