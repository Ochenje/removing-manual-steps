package tech.kood.rental.service;

import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.StructuredLogger;
import tech.kood.rental.security.PasswordHasher;

import java.util.Optional;

public final class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public long register(String username, String password) {
        StructuredLogger.info("user.registration.started");

        try {
            validateUsername(username);
            validatePassword(password);

            String normalizedUsername = username.trim();

            if (userRepository.existsByUsername(normalizedUsername)) {
                reject("username", "DUPLICATE_USERNAME",
                        "That username is already taken.");
            }

            // Hash the password before storing it.
            String passwordHash = passwordHasher.hash(password);

            // TODO(repository): persist username and passwordHash.
            long userId = userRepository.create(normalizedUsername, passwordHash);

            StructuredLogger.info(
                    "user.registration.succeeded | userId=" + userId);

            return userId;

        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error("user.registration.failed", exception);
            throw exception;
        }
    }

    public AuthenticatedUser login(String username, String password) {
        StructuredLogger.info("user.login.started");

        try {
            validateUsername(username);
            validatePassword(password);

            Optional<UserCredential> result =
                    userRepository.findCredentialsByUsername(username.trim());

            if (result.isEmpty()
                    || !passwordHasher.verify(
                            password, result.get().passwordHash())) {

                StructuredLogger.warn(
                        "user.login.rejected | reason=INVALID_CREDENTIALS");

                throw new ValidationException(
                        "USER", "credentials", "INVALID_CREDENTIALS",
                        "Username or password is incorrect.");
            }

            UserCredential credential = result.get();

            StructuredLogger.info(
                    "user.login.succeeded | userId=" + credential.id());

            return new AuthenticatedUser(
                    credential.id(), credential.username());

        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error("user.login.failed", exception);
            throw exception;
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            reject("username", "REQUIRED", "Username is required.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            reject("password", "REQUIRED", "Password is required.");
        }
    }

    private void reject(String field, String reason, String message) {
        StructuredLogger.warn(
                "user.validation.rejected | field=" + field
                        + " | reason=" + reason);

        throw new ValidationException(
                "USER", field, reason, message);
    }

    /*
     * TODO(repository): replace with the project's actual repository API.
     */
    public interface UserRepository {
        boolean existsByUsername(String username);

        Optional<UserCredential> findCredentialsByUsername(String username);

        long create(String username, String passwordHash);
    }

    public record UserCredential(
            long id, String username, String passwordHash) {}

    public record AuthenticatedUser(long id, String username) {}
}