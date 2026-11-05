package tech.kood.rental.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Hands out connections to the SQLite database file and builds the tables on first start.
 * Every caller gets its own fresh connection and must close it when its job is done.
 * Failures are thrown as SQLException, and the layer above turns them into readable messages.
 */
public class DatabaseConnection {

    private final Path file;   // where the database file lives, e.g. data/app.db
    private final String url;  // the address SQLite needs, built from the file path

    public DatabaseConnection(String databasePath) {
        this.file = Path.of(databasePath).toAbsolutePath();
        // SQLite is happy with forward slashes on every system, Windows included
        this.url = "jdbc:sqlite:" + file.toString().replace('\\', '/');
        // TODO (after the logger is merged): log "db.open" when the database opens
    }

    // A fresh connection for ONE job. Use it in try-with-resources so it always closes:
    //     try (Connection connection = db.getConnection()) { ... }
    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        // SQLite ignores foreign keys unless every single connection switches them on
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            connection.close();   // do not leave a half-ready connection open
            throw e;
        }
        return connection;
    }

    // Creates the data folder and the tables if they are missing. Safe to run on every start.
    // If this throws, the database cannot be opened and the app should log it and exit.
    public void initialize() throws SQLException {
        try {
            Path folder = file.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
        } catch (IOException e) {
            throw new SQLException("could not create the database folder", e);
        }

        List<String> statements = splitStatements(readSchema());
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    // Reads schema.sql from inside the application
    private String readSchema() throws SQLException {
        try (InputStream input = DatabaseConnection.class.getResourceAsStream("/schema.sql")) {
            if (input == null) {
                throw new SQLException("schema.sql was not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("schema.sql could not be read", e);
        }
    }

    // Cuts the schema file into single statements:
    // first the "--" comments are dropped, then the text is split at each semicolon
    static List<String> splitStatements(String schema) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : schema.split("\\R")) {
            int comment = line.indexOf("--");
            cleaned.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }

        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            if (!part.isBlank()) {
                statements.add(part.trim());
            }
        }
        return statements;
    }
}