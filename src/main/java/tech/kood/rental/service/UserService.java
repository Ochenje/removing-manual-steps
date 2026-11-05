
package tech.kood.rental.service;

import tech.kood.rental.domain.User;
import tech.kood.rental.domain.UserValidator;
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
            String normalizedUsername =
                    UserValidator.normalizeUsername(username);
            UserValidator.validateRegistration(normalizedUsername, password);

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
            logValidationRejection(exception);
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
            String normalizedUsername =
                    UserValidator.normalizeUsername(username);
            UserValidator.validateLogin(normalizedUsername, password);
            UserValidator.validateUsername(normalizedUsername);

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
            logValidationRejection(exception);
            throw exception;

        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "user.login.failed", exception);
            throw exception;
        }
    }

    public String usernameForId(long userId) {
        if (userId <= 0) {
            reject(
                    "id",
                    "INVALID_ID",
                    "A valid user ID is required.");
        }

        try {
            Optional<User> result = userRepository.findById(userId);
            if (result == null) {
                throw new IllegalStateException(
                        "User repository returned an invalid result.");
            }
            if (result.isEmpty()) {
                reject(
                        "id",
                        "NOT_FOUND",
                        "Owner information could not be found.");
            }
            return result.get().username();
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "user.lookup.failed | userId=" + userId,
                    exception);
            throw exception;
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

    private void logValidationRejection(ValidationException exception) {
        StructuredLogger.warn(
                "user.validation.rejected | field=" + exception.getField()
                        + " | reason=" + exception.getReason());
    }

    /**
     * Represents an authenticated user without exposing their password hash.
     */
    public record AuthenticatedUser(
            long id,
            String username) {}
}
