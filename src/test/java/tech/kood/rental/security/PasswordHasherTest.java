package tech.kood.rental.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    // One hasher shared by all the tests below
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashIsNeverThePlainPassword() {
        String hash = hasher.hash("secret123");

        // The stored value must not be the password itself
        assertNotEquals("secret123", hash);
        // Bcrypt hashes start with $2 (the bcrypt marker)
        assertTrue(hash.startsWith("$2"));
    }

    @Test
    void samePasswordGivesDifferentHashesBecauseOfSalt() {
        String first = hasher.hash("secret123");
        String second = hasher.hash("secret123");

        // The random salt makes every hash unique
        assertNotEquals(first, second);
    }

    @Test
    void rightPasswordPassesVerification() {
        String hash = hasher.hash("secret123");

        assertTrue(hasher.verify("secret123", hash));
    }

    @Test
    void wrongPasswordFailsVerification() {
        String hash = hasher.hash("secret123");

        assertFalse(hasher.verify("wrong-password", hash));
    }

    @Test
    void nullPasswordIsRejected() {
        // hash(null) must throw, not crash with a mystery error
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> hasher.verify(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> hasher.verify("x", null));
    }
}