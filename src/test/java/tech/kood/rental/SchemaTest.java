package tech.kood.rental;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds a fresh in-memory SQLite database from schema.sql and checks its rules.
 */
class SchemaTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        // a brand new, empty database for every single test
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        // SQLite ignores foreign keys unless each connection switches them on
        run("PRAGMA foreign_keys = ON");
        loadSchema();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    // ---------- helpers ----------

    // Runs one SQL statement
    private void run(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    // Returns the first column of the first row as text, or null when there is no row
    private String single(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    // Reads schema.sql and runs each statement in it
    private void loadSchema() throws IOException, SQLException {
        try (InputStream input = SchemaTest.class.getResourceAsStream("/schema.sql")) {
            assertNotNull(input, "schema.sql must be on the classpath");
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (String part : schema.split(";")) {
                if (!part.isBlank()) {
                    run(part);
                }
            }
        }
    }

    // Two users (1 = owner, 2 = renter) and one item owned by user 1
    private void seedUsersAndItem() throws SQLException {
        run("INSERT INTO users (id, username, password) VALUES (1, 'owner', 'hash')");
        run("INSERT INTO users (id, username, password) VALUES (2, 'renter', 'hash')");
        run("INSERT INTO items (id, owner_id, name, description, cost_per_day) "
                + "VALUES (1, 1, 'Drill', 'Cordless drill', 4)");
    }

    // ---------- the tests ----------

    @Test
    void createsAllThreeTables() throws SQLException {
        String count = single("SELECT COUNT(*) FROM sqlite_master "
                + "WHERE type = 'table' AND name IN ('users', 'items', 'rentals')");

        assertEquals("3", count);
    }

    @Test
    void loadingTheSchemaTwiceIsSafe() {
        // IF NOT EXISTS means the app can run the file on every start
        assertDoesNotThrow(this::loadSchema);
    }

    @Test
    void duplicateUsernameIsRejected() throws SQLException {
        run("INSERT INTO users (username, password) VALUES ('siim', 'hash')");

        assertThrows(SQLException.class,
                () -> run("INSERT INTO users (username, password) VALUES ('siim', 'hash')"));
    }

    @Test
    void blankUsernameIsRejected() {
        assertThrows(SQLException.class,
                () -> run("INSERT INTO users (username, password) VALUES ('   ', 'hash')"));
    }

    @Test
    void itemMustHaveARealOwner() {
        // there is no user 999, so the database must refuse the item
        assertThrows(SQLException.class,
                () -> run("INSERT INTO items (owner_id, name, description, cost_per_day) "
                        + "VALUES (999, 'Drill', 'x', 4)"));
    }

    @Test
    void negativeCostIsRejected() throws SQLException {
        seedUsersAndItem();

        assertThrows(SQLException.class,
                () -> run("INSERT INTO items (owner_id, name, description, cost_per_day) "
                        + "VALUES (1, 'Saw', 'x', -1)"));
    }

    @Test
    void unknownStatusIsRejected() throws SQLException {
        seedUsersAndItem();

        assertThrows(SQLException.class,
                () -> run("INSERT INTO items (owner_id, name, description, cost_per_day, status) "
                        + "VALUES (1, 'Saw', 'x', 4, 'BROKEN')"));
    }

    @Test
    void rentalOfZeroDaysIsRejected() throws SQLException {
        seedUsersAndItem();

        assertThrows(SQLException.class,
                () -> run("INSERT INTO rentals (item_id, renter_id, days) VALUES (1, 2, 0)"));
    }

    @Test
    void rentalMustPointAtARealItem() throws SQLException {
        seedUsersAndItem();

        // there is no item 999
        assertThrows(SQLException.class,
                () -> run("INSERT INTO rentals (item_id, renter_id, days) VALUES (999, 2, 3)"));
    }

    @Test
    void goodDataIsAcceptedAndDefaultsAreFilledIn() throws SQLException {
        seedUsersAndItem();
        run("INSERT INTO rentals (id, item_id, renter_id, days) VALUES (1, 1, 2, 3)");

        assertEquals("AVAILABLE", single("SELECT status FROM items WHERE id = 1"));
        assertNotNull(single("SELECT created_at FROM users WHERE id = 1"));
        assertNotNull(single("SELECT rented_at FROM rentals WHERE id = 1"));
        assertNull(single("SELECT returned_at FROM rentals WHERE id = 1"));
    }
}