package tech.kood.rental.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserValidatorTest {

    // A username and password that follow every rule
    private static final String GOOD_NAME = "siim";
    private static final String GOOD_PASSWORD = "password123";

    // Runs registration validation and hands back the exception, so each test can inspect it
    private ValidationException registrationError(String username, String password) {
        return assertThrows(ValidationException.class,
                () -> UserValidator.validateRegistration(username, password));
    }

    // ---------- normal case ----------

    @Test
    void goodRegistrationIsAccepted() {
        assertDoesNotThrow(() -> UserValidator.validateRegistration(GOOD_NAME, GOOD_PASSWORD));
    }

    // ---------- username ----------

    @Test
    void blankUsernameIsRejected() {
        // null, empty and whitespace-only all count as blank
        for (String blank : new String[]{null, "", "   "}) {
            ValidationException e = registrationError(blank, GOOD_PASSWORD);

            assertEquals("User", e.getEntity());
            assertEquals("username", e.getField());
            assertEquals("blank", e.getReason());
            assertEquals("username must not be empty", e.getMessage());
        }
    }

    @Test
    void tooShortUsernameIsRejected() {
        ValidationException e = registrationError("ab", GOOD_PASSWORD);

        assertEquals("username", e.getField());
        assertEquals("too_short", e.getReason());
        assertEquals("username must be at least 3 characters", e.getMessage());
    }

    @Test
    void paddingDoesNotCountTowardsUsernameLength() {
        // "  ab  " is 6 characters, but only 2 will be stored after trimming
        ValidationException e = registrationError("  ab  ", GOOD_PASSWORD);

        assertEquals("too_short", e.getReason());
    }

    @Test
    void tooLongUsernameIsRejected() {
        ValidationException e = registrationError("a".repeat(31), GOOD_PASSWORD);

        assertEquals("username", e.getField());
        assertEquals("too_long", e.getReason());
        assertEquals("username must be at most 30 characters", e.getMessage());
    }

    @Test
    void usernameAtTheLimitsIsAccepted() {
        // exactly 3 and exactly 30 characters are both fine
        assertDoesNotThrow(() -> UserValidator.validateRegistration("abc", GOOD_PASSWORD));
        assertDoesNotThrow(() -> UserValidator.validateRegistration("a".repeat(30), GOOD_PASSWORD));
    }

    // ---------- password ----------

    @Test
    void blankPasswordIsRejected() {
        for (String blank : new String[]{null, "", "   "}) {
            ValidationException e = registrationError(GOOD_NAME, blank);

            assertEquals("User", e.getEntity());
            assertEquals("password", e.getField());
            assertEquals("blank", e.getReason());
            assertEquals("password must not be empty", e.getMessage());
        }
    }

    @Test
    void tooShortPasswordIsRejected() {
        ValidationException e = registrationError(GOOD_NAME, "short12");   // 7 characters

        assertEquals("password", e.getField());
        assertEquals("too_short", e.getReason());
        assertEquals("password must be at least 8 characters", e.getMessage());
    }

    @Test
    void passwordOfExactlyEightCharactersIsAccepted() {
        assertDoesNotThrow(() -> UserValidator.validateRegistration(GOOD_NAME, "12345678"));
    }

    @Test
    void passwordIsNeverShownInTheMessage() {
        String secret = "tiny7";
        ValidationException e = registrationError(GOOD_NAME, secret);

        // the brief says the password must never reach a message or a log
        assertFalse(e.getMessage().contains(secret));
    }

    // ---------- login ----------

    @Test
    void goodLoginInputIsAccepted() {
        assertDoesNotThrow(() -> UserValidator.validateLogin(GOOD_NAME, GOOD_PASSWORD));
    }

    @Test
    void blankLoginInputIsRejected() {
        assertThrows(ValidationException.class, () -> UserValidator.validateLogin("", GOOD_PASSWORD));
        assertThrows(ValidationException.class, () -> UserValidator.validateLogin(GOOD_NAME, "  "));
        assertThrows(ValidationException.class, () -> UserValidator.validateLogin(null, null));
    }

    @Test
    void loginDoesNotRevealThePasswordRules() {
        // a short password is not a login error, it simply fails to match later
        assertDoesNotThrow(() -> UserValidator.validateLogin("ab", "x"));
    }

    // ---------- normalizing ----------

    @Test
    void normalizeUsernameRemovesPaddingSpaces() {
        assertEquals("bob", UserValidator.normalizeUsername("  bob  "));
    }

    @Test
    void normalizeUsernameLeavesNullAlone() {
        assertNull(UserValidator.normalizeUsername(null));
    }
}