package com.veylor.relay.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailSecurityValidator {

    @Value("${spring.mail.properties.mail.smtp.auth:false}")
    private boolean authEnabled;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}")
    private boolean starttlsEnabled;

    @Value("${spring.mail.properties.mail.smtp.starttls.required:false}")
    private boolean starttlsRequired;

    @PostConstruct
    public void validate() {
        if (authEnabled && (!starttlsEnabled || !starttlsRequired)) {
            throw new IllegalStateException(
                    "Security validation failed: SMTP authentication is enabled but STARTTLS is not enabled or not required. " +
                    "To prevent credentials from being sent in plaintext, both spring.mail.properties.mail.smtp.starttls.enable " +
                    "and spring.mail.properties.mail.smtp.starttls.required must be set to true in application.properties."
            );
        }
    }
}
