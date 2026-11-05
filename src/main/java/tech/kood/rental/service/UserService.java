
package tech.kood.rental.service;

import tech.kood.rental.domain.User;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.StructuredLogger;
import tech.kood.rental.repository.UserRepository;
import tech.kood.rental.repository.exception.DuplicateUsernameException;
import tech.kood.rental.security.PasswordHasher;

import java.util.Optional;

public final class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService(
            UserRepository userRepository,
            PasswordHasher passwordHasher) {

        if (userRepository == null) {
            throw new IllegalArgumentException(
                    "UserRepository must not be null");
        }

        if (passwordHasher == null) {
            throw new IllegalArgumentException(
                    "PasswordHasher must not be null");
        }

        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * Registers a new user.
     * The plain password is hashed before it reaches the repository.
     */
    public long register(String username, String password) {
        StructuredLogger.info("user.registration.started");

        try {
            String normalizedUsername = normalizeUsername(username);

            validateUsername(normalizedUsername);
            validatePassword(password);

            if (userRepository.existsByUsername(normalizedUsername)) {
                reject(
                        "username",
                        "DUPLICATE_USERNAME",
                        "That username is already taken.");
            }

            String passwordHash = passwordHasher.hash(password);

            User savedUser =
                    userRepository.insert(normalizedUsername, passwordHash);

            if (savedUser == null || savedUser.id() == null) {
                throw new IllegalStateException(
                        "Repository did not return a saved user ID");
            }

            StructuredLogger.info(
                    "user.registration.succeeded | userId="
                            + savedUser.id());

            return savedUser.id();

        } catch (ValidationException exception) {
            throw exception;

        } catch (DuplicateUsernameException exception) {
            StructuredLogger.warn(
                    "user.validation.rejected | field=username | reason=DUPLICATE_USERNAME");

            throw new ValidationException(
                    "USER",
                    "username",
                    "DUPLICATE_USERNAME",
                    "That username is already taken.");

        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "user.registration.failed", exception);
            throw exception;
        }
    }

    /**
     * Authenticates a user using their username and password.
     */
    public AuthenticatedUser login(
            String username,
            String password) {

        StructuredLogger.info("user.login.started");

        try {
            String normalizedUsername = normalizeUsername(username);

            validateUsername(normalizedUsername);
            validatePassword(password);

            Optional<User> result =
                    userRepository.findByUsername(normalizedUsername);

            if (result == null || result.isEmpty()) {
                rejectInvalidCredentials();
            }

            User user = result.get();

            if (!passwordHasher.verify(
                    password, user.passwordHash())) {
                rejectInvalidCredentials();
            }

            StructuredLogger.info(
                    "user.login.succeeded | userId=" + user.id());

            return new AuthenticatedUser(
                    user.id(),
                    user.username());

        } catch (ValidationException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "user.login.failed", exception);
            throw exception;
        }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            reject(
                    "username",
                    "REQUIRED",
                    "Username is required.");
        }

        return username.trim();
    }

    private void validateUsername(String username) {
        if (username.isBlank()) {
            reject(
                    "username",
                    "REQUIRED",
                    "Username is required.");
        }

        if (username.length() < 3 || username.length() > 30) {
            reject(
                    "username",
                    "INVALID_LENGTH",
                    "Username must be between 3 and 30 characters.");
        }

        if (!username.matches("[A-Za-z0-9_]+")) {
            reject(
                    "username",
                    "INVALID_FORMAT",
                    "Username can only contain letters, numbers, and underscores.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            reject(
                    "password",
                    "REQUIRED",
                    "Password is required.");
        }
    }

    private void rejectInvalidCredentials() {
        StructuredLogger.warn(
                "user.login.rejected | reason=INVALID_CREDENTIALS");

        throw new ValidationException(
                "USER",
                "credentials",
                "INVALID_CREDENTIALS",
                "Username or password is incorrect.");
    }

    private void reject(
            String field,
            String reason,
            String message) {

        StructuredLogger.warn(
                "user.validation.rejected | field=" + field
                        + " | reason=" + reason);

        throw new ValidationException(
                "USER",
                field,
                reason,
                message);
    }

    /**
     * Represents an authenticated user without exposing their password hash.
     */
    public record AuthenticatedUser(
            long id,
            String username) {}
}
