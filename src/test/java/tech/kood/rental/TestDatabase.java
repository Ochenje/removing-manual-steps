package tech.kood.rental;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Provides a temporary, isolated SQLite database for tests.
 *
 * The database is destroyed when its connection is closed.
 */
public final class TestDatabase implements AutoCloseable {

    private final Connection connection;

    private TestDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    /**
     * Creates a fresh in-memory database.
     */
    public static TestDatabase create() throws SQLException {
        return new TestDatabase();
    }

    /**
     * Returns the connection used by this test database.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Creates tables using the existing application schema.
     * This reads schema.sql but does not modify that file.
     */
    public void initializeSchema() throws SQLException, IOException {
        try (InputStream input =
                     TestDatabase.class.getResourceAsStream("/schema.sql")) {

            if (input == null) {
                throw new IOException(
                        "Could not find schema.sql on the test classpath."
                );
            }

            String schema = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            try (Statement statement = connection.createStatement()) {
                for (String sql : schema.split(";")) {
                    String command = sql.trim();

                    if (!command.isEmpty()) {
                        statement.execute(command);
                    }
                }
            }
        }
    }

    /**
     * Closes the connection and releases the in-memory database.
     */
    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
