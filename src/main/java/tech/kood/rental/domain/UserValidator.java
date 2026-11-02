package tech.kood.rental.domain;

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

    private UserValidator() {
        // utility class: nobody should create one
    }

    // All the rules, used when someone registers
    public static void validateRegistration(String username, String password) {
        requireUsername(username);
        // trim() so that "  bob  " counts as the 3 characters that will be stored
        int length = username.trim().length();
        if (length < USERNAME_MIN) {
            throw new ValidationException("User", "username", "too_short",
                    "username must be at least " + USERNAME_MIN + " characters");
        }
        if (length > USERNAME_MAX) {
            throw new ValidationException("User", "username", "too_long",
                    "username must be at most " + USERNAME_MAX + " characters");
        }

        requirePassword(password);
        if (password.length() < PASSWORD_MIN) {
            throw new ValidationException("User", "password", "too_short",
                    "password must be at least " + PASSWORD_MIN + " characters");
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