package com.veylor.relay.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailSanitizerTest {

    @Test
    void testGmailSanitization() {
        assertEquals("john.doe@example.com", EmailSanitizer.sanitize("john.doe@example.com"));
        assertEquals("johndoe@gmail.com", EmailSanitizer.sanitize("John.Doe@gmail.com"));
        assertEquals("johndoe@gmail.com", EmailSanitizer.sanitize("johndoe+spam@gmail.com"));
        assertEquals("johndoe@gmail.com", EmailSanitizer.sanitize("john.doe+spam@gmail.com"));
        assertEquals("johndoe@googlemail.com", EmailSanitizer.sanitize("john.doe+spam@googlemail.com"));
        assertEquals("johndoe@gmail.com", EmailSanitizer.sanitize("  johndoe@gmail.com  "));
    }

    @Test
    void testEdgeCases() {
        assertNull(EmailSanitizer.sanitize(null));
        assertEquals("", EmailSanitizer.sanitize(""));
        assertEquals("invalidemail", EmailSanitizer.sanitize("InvalidEmail"));
    }
}
