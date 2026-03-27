package com.minimartpos.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthService (password hashing, strength checking).
 * Does NOT require a database connection.
 */
class AuthServiceTest {

    private final AuthService authService = new AuthService();

    @Test
    void hashPassword_shouldProduceBCryptHash() {
        String plain = "TestPassword123!";
        String hash  = authService.hashPassword(plain);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"),
                   "Should be a BCrypt hash");
    }

    @Test
    void verifyPassword_correctPassword_returnsTrue() {
        String plain = "MySecretPass@1";
        String hash  = authService.hashPassword(plain);

        assertTrue(authService.verifyPassword(plain, hash));
    }

    @Test
    void verifyPassword_wrongPassword_returnsFalse() {
        String hash = authService.hashPassword("CorrectPassword");
        assertFalse(authService.verifyPassword("WrongPassword", hash));
    }

    @Test
    void getPasswordStrength_shortPassword_returnsWeak() {
        assertEquals("WEAK", authService.getPasswordStrength("abc"));
        assertEquals("WEAK", authService.getPasswordStrength("abc12"));
    }

    @Test
    void getPasswordStrength_strongPassword_returnsStrong() {
        String strength = authService.getPasswordStrength("MyP@ssw0rd123");
        assertTrue(strength.equals("STRONG") || strength.equals("VERY_STRONG"));
    }

    @Test
    void hashPassword_sameInput_producesDistinctHashes() {
        // BCrypt uses random salt — same input should NEVER produce same hash
        String plain = "SamePassword";
        String hash1 = authService.hashPassword(plain);
        String hash2 = authService.hashPassword(plain);

        assertNotEquals(hash1, hash2, "BCrypt hashes should be unique due to random salt");

        // But both should still verify correctly
        assertTrue(authService.verifyPassword(plain, hash1));
        assertTrue(authService.verifyPassword(plain, hash2));
    }
}
