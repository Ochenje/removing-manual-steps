package tech.kood.rental.domain;

import java.nio.charset.StandardCharsets;

/**
 * Checks User input before it reaches the service and the database.
 * It throws a ValidationException with a readable message when a rule is broken.
 * It never puts the password into a message, so nothing can leak into the log.
 */
public final class UserValidator {

    // The limits live here, so changing a rule is a one-line edit
    static final int USERNAME_MIN = 3;
    static final int USERNAME_MAX = 30;
    static final int PASSWORD_MIN = 8;
    static final int PASSWORD_MAX_BYTES = 72;

    private UserValidator() {
        // utility class: nobody should create one
    }

    // The form of the username that gets stored: no padding spaces.
    // The service calls this before saving, so " bob " is stored as "bob".
    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    // All the rules, used when someone registers
    public static void validateRegistration(String username, String password) {
        requireUsername(username);

        // Measure the normalized name, because that is the one that will be stored
        int length = normalizeUsername(username).length();
        if (length < USERNAME_MIN) {
            throw new ValidationException("User", "username", "too_short",
                    "username must be at least " + USERNAME_MIN + " characters");
        }
        if (length > USERNAME_MAX) {
            throw new ValidationException("User", "username", "too_long",
                    "username must be at most " + USERNAME_MAX + " characters");
        }

        // Check for null or blank passwords before performing other checks
        requirePassword(password);

        if (password.length() < PASSWORD_MIN) {
            throw new ValidationException("User", "password", "too_short",
                    "password must be at least " + PASSWORD_MIN + " characters");
        }

        // BCrypt supports a maximum of 72 UTF-8 bytes
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new ValidationException("User", "password", "too_long",
                    "password must not exceed " + PASSWORD_MAX_BYTES + " UTF-8 bytes");
        }
    }

    // Only the blank checks, used at login so we don't reveal the password rules
    public static void validateLogin(String username, String password) {
        requireUsername(username);
        requirePassword(password);
    }

    private static void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("User", "username", "blank",
                    "username must not be empty");
        }
    }

    private static void requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ValidationException("User", "password", "blank",
                    "password must not be empty");
        }
    }
}