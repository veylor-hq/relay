package com.veylor.relay.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailSanitizerTest {

    @Test
    void testGmailSanitization() {
        // Gmail/Googlemail: lowercase local-part, remove dots and plus-addressing
        assertEquals("johndoe@gmail.com", EmailSanitizer.sanitize("John.Doe@gmail.com"));
        assertEquals("johndoe@gmail.com", EmailSanitizer.sanitize("johndoe+spam@gmail.com"));
        assertEquals("johndoe@gmail.com", EmailSanitizer.sanitize("john.doe+spam@gmail.com"));
        assertEquals("johndoe@googlemail.com", EmailSanitizer.sanitize("john.doe+spam@googlemail.com"));
        assertEquals("johndoe@gmail.com", EmailSanitizer.sanitize("  johndoe@gmail.com  "));
    }

    @Test
    void testNonGmailDomainPreservesLocalPartCase() {
        // Finding #16: Non-Gmail domains preserve local-part casing, but domain is lowercased
        assertEquals("User@example.com", EmailSanitizer.sanitize("User@Example.com"));
        assertEquals("John.Doe@example.com", EmailSanitizer.sanitize("John.Doe@example.com"));
        assertEquals("Admin@company.org", EmailSanitizer.sanitize("Admin@COMPANY.ORG"));
        assertEquals("CamelCase@test.io", EmailSanitizer.sanitize("CamelCase@Test.IO"));
    }

    @Test
    void testEdgeCases() {
        assertNull(EmailSanitizer.sanitize(null));
        assertEquals("", EmailSanitizer.sanitize(""));
        assertEquals("invalidemail", EmailSanitizer.sanitize("InvalidEmail"));
    }
}
