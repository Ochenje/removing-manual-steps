
package tech.kood.rental.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SeedDataTest {

    @TempDir
    Path tempDir;

    @Test
    void seededDatabaseMeetsDemoRequirements() throws Exception {

        // Build the database inside a temporary folder.
        Path database = tempDir.resolve("seed-test.db");
        SeedData.build(database);

        String url = "jdbc:sqlite:" +
                database.toAbsolutePath().toString().replace('\\', '/');

        try (Connection connection = DriverManager.getConnection(url)) {

            // 1. At least two users.
            assertTrue(count(connection,
                            "SELECT COUNT(*) FROM users") >= 2,
                    "The database must contain at least two users");

            // 2. At least six available items.
            assertTrue(count(connection,
                            "SELECT COUNT(*) FROM items WHERE status = 'AVAILABLE'") >= 6,
                    "The database must contain at least six available items");

            // 3. Passwords must be stored as BCrypt hashes.
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT password FROM users")) {

                while (rs.next()) {
                    String password = rs.getString("password");

                    assertNotEquals(SeedData.DEMO_PASSWORD, password,
                            "A plain-text password was stored");

                    assertTrue(password.matches(
                                    "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$"),
                            "Passwords must be stored as BCrypt hashes");
                }
            }

            // 4. Nobody should rent their own item.
            assertEquals(0, count(connection,
                            "SELECT COUNT(*) FROM rentals r " +
                                    "JOIN items i ON r.item_id = i.id " +
                                    "WHERE r.renter_id = i.owner_id"),
                    "An owner cannot rent their own item");

            // 5. All foreign-key references must be valid.
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "PRAGMA foreign_key_check")) {

                assertFalse(rs.next(),
                        "The database contains broken foreign-key references");
            }

            // 6. Every rented item must have an active rental.
            assertEquals(0, count(connection,
                            "SELECT COUNT(*) FROM items i " +
                                    "WHERE i.status = 'RENTED' " +
                                    "AND NOT EXISTS (" +
                                    "SELECT 1 FROM rentals r WHERE r.item_id = i.id)"),
                    "Every rented item must have a rental record");

            // 7. Every active rental must belong to a rented item.
            assertEquals(0, count(connection,
                            "SELECT COUNT(*) FROM rentals r " +
                                    "JOIN items i ON r.item_id = i.id " +
                                    "WHERE i.status != 'RENTED'"),
                    "An active rental must belong to a rented item");
        }
    }

    private int count(Connection connection, String sql)
            throws Exception {

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
