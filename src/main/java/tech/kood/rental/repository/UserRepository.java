package tech.kood.rental.repository;

import tech.kood.rental.domain.User;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.exception.DatabaseException;
import tech.kood.rental.repository.exception.DuplicateUsernameException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * The only place that reads and writes the users table.
 * Every method opens its own connection and closes it again, even when it fails.
 * Failures become readable exceptions, so no SQLite wording reaches the user.
 */
public class UserRepository {

    // schema.sql stores times as text like 2026-06-16 14:30:05
    private static final DateTimeFormatter STORED_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String COLUMNS = "id, username, password, created_at";

    // Knows how to hand out connections
    private final DatabaseConnection database;

    public UserRepository(DatabaseConnection database) {
        this.database = database;
    }

    // Saves a new user and returns them as stored (with id and created_at filled in).
    // passwordHash must already be hashed, never the plain password.
    public User insert(String username, String passwordHash) {
        // The ? marks are blanks we fill in below, so odd characters
        // in a name can never be treated as SQL commands
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.executeUpdate();

            // Ask the database which id it just handed out, then read the row back
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                User saved = readById(connection, keys.getLong(1));
                if (saved == null) {
                    throw new SQLException("the inserted user could not be read back");
                }
                return saved;
            }

        } catch (SQLException e) {
            // TODO (after StructuredLogger is merged): log the failure with the operation name
            if (isDuplicateUsername(e)) {
                throw new DuplicateUsernameException(e);
            }
            throw new DatabaseException("insert user", e);
        }
    }

    // Finds a user by id. A missing id is normal, so we return an empty box.
    public Optional<User> findById(long id) {
        try (Connection connection = database.getConnection()) {
            return Optional.ofNullable(readById(connection, id));
        } catch (SQLException e) {
            throw new DatabaseException("find user by id", e);
        }
    }

    // Finds a user by username. A missing name is normal, so we return an empty box.
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE username = ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DatabaseException("find user by username", e);
        }
    }

    // True when the username is already taken (the service uses this before registering)
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    // ---------- helpers ----------

    // Reads one user on a connection that is already open. Returns null when there is no such id.
    private User readById(Connection connection, long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? map(row) : null;
            }
        }
    }

    // Turns one database row into a User object
    private User map(ResultSet row) throws SQLException {
        return new User(
                row.getLong("id"),
                row.getString("username"),
                row.getString("password"),
                parseTime(row.getString("created_at")));
    }

    // A time that cannot be read is reported like any other database problem
    private static LocalDateTime parseTime(String text) throws SQLException {
        try {
            return LocalDateTime.parse(text, STORED_TIME);
        } catch (DateTimeParseException e) {
            throw new SQLException("a stored time could not be read: " + text, e);
        }
    }

    // SQLite words a taken username as "UNIQUE constraint failed: users.username".
    // Other constraint failures (a missing username, a blank one) read differently,
    // so they are never mistaken for a taken name.
    private static boolean isDuplicateUsername(SQLException e) {
        String message = e.getMessage();
        return message != null && message.contains("UNIQUE constraint failed: users.username");
    }
}