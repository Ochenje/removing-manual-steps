package tech.kood.rental.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void toStringNeverShowsThePasswordHash() {
        User user = new User(1L, "siim", "$2b$12$secretHashValue", LocalDateTime.now());

        String text = user.toString();

        // the brief says the password must never reach a log line, plain or hashed
        assertFalse(text.contains("$2b$12$secretHashValue"));
        assertFalse(text.contains("secretHash"));
        assertTrue(text.contains("siim"));
    }

    @Test
    void toStringShowsOnlyTheIdAndTheUsername() {
        User user = new User(7L, "liisa", "hash", LocalDateTime.of(2026, 6, 16, 14, 30));

        assertEquals("User[id=7, username=liisa]", user.toString());
    }
}