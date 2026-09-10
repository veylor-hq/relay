package com.veylor.relay.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionUtilsTest {

    private final String masterKey = "secure-master-key-32-characters!";

    @Test
    void testEncryptDecryptSuccess() {
        String plaintext = "SuperSecretSmtpPass!@#123";
        String encrypted = EncryptionUtils.encrypt(plaintext, masterKey);

        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = EncryptionUtils.decrypt(encrypted, masterKey);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptDecryptWithNull() {
        assertNull(EncryptionUtils.encrypt(null, masterKey));
        assertNull(EncryptionUtils.decrypt(null, masterKey));
    }

    @Test
    void testDecryptWithWrongKeyFails() {
        String plaintext = "secret";
        String encrypted = EncryptionUtils.encrypt(plaintext, masterKey);

        assertThrows(RuntimeException.class, () -> {
            EncryptionUtils.decrypt(encrypted, "wrong-master-key");
        });
    }
}
