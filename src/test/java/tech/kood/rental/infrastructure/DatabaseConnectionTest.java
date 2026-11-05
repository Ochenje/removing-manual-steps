package tech.kood.rental.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConnectionTest {

    // JUnit gives every test its own empty temporary folder and deletes it afterwards,
    // so your real data folder is never touched
    @TempDir
    Path tempDir;

    // ---------- helpers ----------

    // A DatabaseConnection pointing at a file inside the temporary folder
    private DatabaseConnection newDatabase(String relativePath) {
        return new DatabaseConnection(tempDir.resolve(relativePath).toString());
    }

    // Returns the first column of the first row as text, or null when there is no row
    private String single(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    // ---------- setting up the database ----------

    @Test
    void createsMissingFolderFileAndUsersTable() throws Exception {
        // the folder "nested/data" does not exist yet
        DatabaseConnection db = newDatabase("nested/data/app.db");

        db.initialize();

        assertTrue(Files.exists(tempDir.resolve("nested/data/app.db")));
        try (Connection connection = db.getConnection()) {
            // schema.sql on main only has the users table so far.
            // Once the items and rentals tables are merged, this can check for all three.
            assertEquals("1", single(connection,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'users'"));
        }
    }

    @Test
    void runningSetupTwiceKeepsTheData() throws Exception {
        DatabaseConnection db = newDatabase("app.db");
        db.initialize();
        try (Connection connection = db.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username, password) VALUES ('siim', 'hash')");
        }

        // the app runs the setup on every start, so a second run must not wipe anything
        db.initialize();

        try (Connection connection = db.getConnection()) {
            assertEquals("1", single(connection, "SELECT COUNT(*) FROM users"));
        }
    }

    @Test
    void unusablePathFailsWithAReadableMessage() throws Exception {
        // "blocker" is a plain file, so a folder cannot be created inside it
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "I am a file, not a folder");
        DatabaseConnection db = new DatabaseConnection(blocker.resolve("app.db").toString());

        SQLException error = assertThrows(SQLException.class, db::initialize);

        assertEquals("could not create the database folder", error.getMessage());
    }

    // ---------- connections ----------

    @Test
    void foreignKeysAreSwitchedOnForEveryConnection() throws Exception {
        DatabaseConnection db = newDatabase("fk.db");

        try (Connection connection = db.getConnection()) {
            assertEquals("1", single(connection, "PRAGMA foreign_keys"));
        }
    }

    @Test
    void foreignKeyRuleIsActuallyEnforced() throws Exception {
        DatabaseConnection db = newDatabase("fk.db");

        try (Connection connection = db.getConnection();
             Statement statement = connection.createStatement()) {
            // two throwaway tables, so this test does not depend on schema.sql
            statement.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE child (parent_id INTEGER REFERENCES parent(id))");

            // there is no parent 999, so the database must refuse the row
            assertThrows(SQLException.class,
                    () -> statement.execute("INSERT INTO child (parent_id) VALUES (999)"));
        }
    }

    @Test
    void eachCallGetsItsOwnConnection() throws Exception {
        DatabaseConnection db = newDatabase("app.db");
        Connection first = db.getConnection();
        Connection second = db.getConnection();
        try {
            assertNotSame(first, second);

            // closing one must not close the other
            first.close();
            assertFalse(second.isClosed());
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void connectionOpenAndCloseAreLogged() throws Exception {
        Logger logger = Logger.getLogger("tech.kood.rental");
        boolean originalParentHandlers = logger.getUseParentHandlers();
        Level originalLevel = logger.getLevel();
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        try {
            newDatabase("lifecycle.db").getConnection().close();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(originalParentHandlers);
            logger.setLevel(originalLevel);
        }

        assertTrue(messages.stream().anyMatch(
                message -> message.contains("db.connection.opened")));
        assertTrue(messages.stream().anyMatch(
                message -> message.contains("db.connection.closed")));
    }

    // ---------- reading schema.sql ----------

    @Test
    void splittingIgnoresCommentsAndTheirSemicolons() {
        String schema = "-- a comment; with a semicolon\n"
                + "CREATE TABLE a (x INTEGER); -- trailing; comment\n"
                + "CREATE TABLE b (y INTEGER);\n";

        List<String> statements = DatabaseConnection.splitStatements(schema);

        assertEquals(List.of("CREATE TABLE a (x INTEGER)", "CREATE TABLE b (y INTEGER)"), statements);
    }
}