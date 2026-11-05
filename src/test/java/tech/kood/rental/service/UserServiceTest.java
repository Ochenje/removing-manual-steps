package tech.kood.rental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.kood.rental.domain.User;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.UserRepository;
import tech.kood.rental.security.PasswordHasher;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private FakeUserRepository repository;
    private PasswordHasher passwordHasher;
    private UserService userService;

    @BeforeEach
    void setUp() {
        repository = new FakeUserRepository();
        passwordHasher = new PasswordHasher();
        userService = new UserService(repository, passwordHasher);
    }

    // ---------------- CONSTRUCTOR TESTS ----------------

    @Test
    void constructorRejectsNullRepository() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserService(null, passwordHasher));
    }

    @Test
    void constructorRejectsNullPasswordHasher() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UserService(repository, null));
    }

    // ---------------- REGISTRATION TESTS ----------------

    @Test
    void registerCreatesUserSuccessfully() {
        long userId = userService.register("  john_doe  ", "password123");

        assertTrue(userId > 0);
        assertTrue(repository.users.containsKey("john_doe"));

        User savedUser = repository.users.get("john_doe");

        assertNotNull(savedUser.passwordHash());
        assertNotEquals("password123", savedUser.passwordHash());
        assertTrue(passwordHasher.verify(
                "password123", savedUser.passwordHash()));
    }

    @Test
    void registerRejectsNullUsername() {
        assertThrows(
                ValidationException.class,
                () -> userService.register(null, "password123"));
    }

    @Test
    void registerRejectsBlankUsername() {
        assertThrows(
                ValidationException.class,
                () -> userService.register("   ", "password123"));
    }

    @Test
    void registerRejectsUsernameTooShort() {
        assertThrows(
                ValidationException.class,
                () -> userService.register("ab", "password123"));
    }

    @Test
    void registerRejectsUsernameTooLong() {
        String username = "a".repeat(31);

        assertThrows(
                ValidationException.class,
                () -> userService.register(username, "password123"));
    }

    @Test
    void registerRejectsInvalidUsernameCharacters() {
        assertThrows(
                ValidationException.class,
                () -> userService.register("john-doe", "password123"));
    }

    @Test
    void registerRejectsNullPassword() {
        assertThrows(
                ValidationException.class,
                () -> userService.register("john_doe", null));
    }

    @Test
    void registerRejectsBlankPassword() {
        assertThrows(
                ValidationException.class,
                () -> userService.register("john_doe", "   "));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        userService.register("john_doe", "password123");

        assertThrows(
                ValidationException.class,
                () -> userService.register("john_doe", "anotherPassword"));
    }

    @Test
    void registerRejectsRepositoryReturningNullUser() {
        repository.returnNullUser = true;

        assertThrows(
                IllegalStateException.class,
                () -> userService.register("john_doe", "password123"));
    }

    @Test
    void registerRejectsUserWithoutGeneratedId() {
        repository.returnUserWithoutId = true;

        assertThrows(
                IllegalStateException.class,
                () -> userService.register("john_doe", "password123"));
    }

    @Test
    void registerRethrowsRepositoryRuntimeException() {
        repository.throwOnInsert = true;

        assertThrows(
                IllegalStateException.class,
                () -> userService.register("john_doe", "password123"));
    }

    // ---------------- LOGIN TESTS ----------------

    @Test
    void loginSucceedsWithCorrectCredentials() {
        userService.register("john_doe", "password123");

        UserService.AuthenticatedUser authenticated =
                userService.login("  john_doe  ", "password123");

        assertNotNull(authenticated);
        assertEquals("john_doe", authenticated.username());
        assertTrue(authenticated.id() > 0);
    }

    @Test
    void loginRejectsNullUsername() {
        assertThrows(
                ValidationException.class,
                () -> userService.login(null, "password123"));
    }

    @Test
    void loginRejectsBlankUsername() {
        assertThrows(
                ValidationException.class,
                () -> userService.login("   ", "password123"));
    }

    @Test
    void loginRejectsInvalidUsername() {
        assertThrows(
                ValidationException.class,
                () -> userService.login("john-doe", "password123"));
    }

    @Test
    void loginRejectsNullPassword() {
        assertThrows(
                ValidationException.class,
                () -> userService.login("john_doe", null));
    }

    @Test
    void loginRejectsBlankPassword() {
        assertThrows(
                ValidationException.class,
                () -> userService.login("john_doe", "   "));
    }

    @Test
    void loginRejectsUnknownUsername() {
        assertThrows(
                ValidationException.class,
                () -> userService.login("unknown_user", "password123"));
    }

    @Test
    void loginRejectsNullRepositoryResult() {
        repository.returnNullOptional = true;

        assertThrows(
                ValidationException.class,
                () -> userService.login("john_doe", "password123"));
    }

    @Test
    void loginRejectsIncorrectPassword() {
        userService.register("john_doe", "password123");

        assertThrows(
                ValidationException.class,
                () -> userService.login("john_doe", "wrongPassword"));
    }

    @Test
    void loginRethrowsRepositoryRuntimeException() {
        repository.throwOnFind = true;

        assertThrows(
                IllegalStateException.class,
                () -> userService.login("john_doe", "password123"));
    }

    // ---------------- FAKE REPOSITORY ----------------
    private static class FakeUserRepository extends UserRepository {

                private final Map<String, User> users = new HashMap<>();

                private long nextId = 1;

                private boolean returnNullUser;
                private boolean returnUserWithoutId;
                private boolean returnNullOptional;
                private boolean throwOnInsert;
                private boolean throwOnFind;
                
                FakeUserRepository() {
                    super(new DatabaseConnection("target/test-user-service.db"));
                }

        @Override
        public boolean existsByUsername(String username) {
            return users.containsKey(username);
        }

        @Override
        public User insert(String username, String passwordHash) {
            if (throwOnInsert) {
                throw new IllegalStateException("Simulated insert failure");
            }

            if (returnNullUser) {
                return null;
            }

            if (returnUserWithoutId) {
                return new User(
                        null,
                        username,
                        passwordHash,
                        LocalDateTime.now());
            }

            User user = new User(
                    nextId++,
                    username,
                    passwordHash,
                    LocalDateTime.now());

            users.put(username, user);

            return user;
        }

        @Override
        public Optional<User> findByUsername(String username) {
            if (throwOnFind) {
                throw new IllegalStateException("Simulated lookup failure");
            }

            if (returnNullOptional) {
                return null;
            }

            return Optional.ofNullable(users.get(username));
        }
    }
}