package tech.kood.rental.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kood.rental.domain.User;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.exception.DatabaseException;
import tech.kood.rental.repository.exception.DuplicateUsernameException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {

    // JUnit gives every test its own empty temporary folder and deletes it afterwards,
    // so your real data folder is never touched
    @TempDir
    Path tempDir;

    private DatabaseConnection db;
    private UserRepository users;

    @BeforeEach
    void setUp() throws Exception {
        // a brand new database file with the tables built, for every single test
        db = new DatabaseConnection(tempDir.resolve("app.db").toString());
        db.initialize();
        users = new UserRepository(db);
    }

    // A repository whose database folder does not exist, so every connection attempt fails
    private UserRepository brokenRepository() {
        return new UserRepository(
                new DatabaseConnection(tempDir.resolve("missing/app.db").toString()));
    }

    // ---------- saving and finding ----------

    @Test
    void insertReturnsTheStoredUser() {
        User saved = users.insert("siim", "hash123");

        assertNotNull(saved.id());
        assertEquals("siim", saved.username());
        assertEquals("hash123", saved.passwordHash());
        assertNotNull(saved.createdAt());   // filled in by the database
    }

    @Test
    void insertedUserCanBeFoundById() {
        User saved = users.insert("siim", "hash123");

        Optional<User> found = users.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved, found.get());
    }

    @Test
    void insertedUserCanBeFoundByUsername() {
        User saved = users.insert("siim", "hash123");

        Optional<User> found = users.findByUsername("siim");

        assertTrue(found.isPresent());
        assertEquals(saved, found.get());
    }

    @Test
    void unknownUsernameGivesAnEmptyResult() {
        assertTrue(users.findByUsername("nobody").isEmpty());
    }

    @Test
    void unknownIdGivesAnEmptyResult() {
        assertTrue(users.findById(999).isEmpty());
    }

    @Test
    void existsByUsernameReflectsWhatIsStored() {
        assertFalse(users.existsByUsername("siim"));

        users.insert("siim", "hash123");

        assertTrue(users.existsByUsername("siim"));
    }

    @Test
    void oddCharactersAreStoredAsPlainText() {
        // quotes and SQL words must be treated as text, never as commands
        String odd = "o'brien\"; DROP TABLE users; --";

        users.insert(odd, "hash123");

        assertEquals(odd, users.findByUsername(odd).get().username());
        // the users table still works
        assertDoesNotThrow(() -> users.insert("siim", "hash123"));
    }

    // ---------- rejected input ----------

    @Test
    void duplicateUsernameIsRejectedWithAReadableMessage() {
        users.insert("siim", "hash123");

        DuplicateUsernameException e = assertThrows(DuplicateUsernameException.class,
                () -> users.insert("siim", "other-hash"));

        assertEquals("That username is already taken.", e.getMessage());
        // the original database error is kept for the log
        assertInstanceOf(SQLException.class, e.getCause());
        // the first user is untouched
        assertEquals("hash123", users.findByUsername("siim").get().passwordHash());
    }

    @Test
    void blankUsernameIsNotReportedAsTaken() {
        DatabaseException e = assertThrows(DatabaseException.class,
                () -> users.insert("   ", "hash123"));

        assertEquals("insert user", e.getOperation());
    }

    @Test
    void missingUsernameIsNotReportedAsTaken() {
        // a null name breaks a different rule from a duplicate, and must say so
        DatabaseException e = assertThrows(DatabaseException.class,
                () -> users.insert(null, "hash123"));

        assertEquals("insert user", e.getOperation());
    }

    @Test
    void repositoryKeepsWorkingAfterAFailure() {
        users.insert("siim", "hash123");
        assertThrows(DuplicateUsernameException.class, () -> users.insert("siim", "x"));

        // a failed call must not leave anything stuck
        assertDoesNotThrow(() -> users.insert("liisa", "hash456"));
        assertTrue(users.existsByUsername("liisa"));
    }

    // ---------- database problems ----------

    @Test
    void unusableDatabaseGivesAReadableMessageOnInsert() {
        DatabaseException e = assertThrows(DatabaseException.class,
                () -> brokenRepository().insert("siim", "hash123"));

        assertEquals("insert user", e.getOperation());
        assertFalse(e.getMessage().toLowerCase().contains("sqlite"));   // no SQLite wording for the user
        assertInstanceOf(SQLException.class, e.getCause());              // the detail is kept for the log
    }

    @Test
    void unusableDatabaseGivesAReadableMessageOnFindById() {
        DatabaseException e = assertThrows(DatabaseException.class,
                () -> brokenRepository().findById(1));

        assertEquals("find user by id", e.getOperation());
        assertFalse(e.getMessage().toLowerCase().contains("sqlite"));
    }

    @Test
    void unusableDatabaseGivesAReadableMessageOnFindByUsername() {
        DatabaseException e = assertThrows(DatabaseException.class,
                () -> brokenRepository().findByUsername("siim"));

        assertEquals("find user by username", e.getOperation());
        assertFalse(e.getMessage().toLowerCase().contains("sqlite"));
    }

    @Test
    void unreadableStoredTimeIsReportedAsADatabaseError() throws Exception {
        // put a row with a broken time straight into the table, behind the repository's back
        try (Connection connection = db.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username, password, created_at) "
                    + "VALUES ('odd', 'hash', 'not a time')");
        }

        DatabaseException e = assertThrows(DatabaseException.class,
                () -> users.findByUsername("odd"));

        assertEquals("find user by username", e.getOperation());
    }
}