
package tech.kood.rental.repository;

import tech.kood.rental.domain.Rental;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.exception.DatabaseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class RentalRepository {

    private static final String COLUMNS =
            "id, item_id, renter_id, days, rented_at, returned_at";

    private static final DateTimeFormatter STORED_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatabaseConnection database;

    public RentalRepository(DatabaseConnection database) {
        this.database = database;
    }

    public Rental insert(Rental rental) {
        try (Connection connection = database.getConnection()) {
            return insert(connection, rental);
        } catch (SQLException e) {
            throw new DatabaseException("insert rental", e);
        }
    }

    public Rental insert(Connection connection, Rental rental)
            throws SQLException {

        String sql = "INSERT INTO rentals (item_id, renter_id, days) "
                + "VALUES (?, ?, ?)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setLong(1, rental.itemId());
            statement.setLong(2, rental.renterId());
            statement.setInt(3, rental.days());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException(
                            "The inserted rental ID could not be retrieved.");
                }

                Rental saved = readById(connection, keys.getLong(1));

                if (saved == null) {
                    throw new SQLException(
                            "The inserted rental could not be read back.");
                }

                return saved;
            }
        }
    }

    public Optional<Rental> findById(long id) {
        try (Connection connection = database.getConnection()) {
            return Optional.ofNullable(readById(connection, id));
        } catch (SQLException e) {
            throw new DatabaseException("find rental by id", e);
        }
    }

    public Optional<Rental> findById(Connection connection, long id)
            throws SQLException {
        return Optional.ofNullable(readById(connection, id));
    }

    public Optional<Rental> findActiveByItemId(long itemId) {
        try (Connection connection = database.getConnection()) {
            return findActiveByItemId(connection, itemId);
        } catch (SQLException e) {
            throw new DatabaseException("find active rental by item id", e);
        }
    }

    public Optional<Rental> findActiveByItemId(
            Connection connection, long itemId) throws SQLException {

        String sql = "SELECT " + COLUMNS
                + " FROM rentals WHERE item_id = ? AND returned_at IS NULL "
                + "ORDER BY id DESC LIMIT 1";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);

            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? Optional.of(map(row))
                        : Optional.empty();
            }
        }
    }

    public boolean confirmReturn(long rentalId) {
        try (Connection connection = database.getConnection()) {
            return confirmReturn(connection, rentalId);
        } catch (SQLException e) {
            throw new DatabaseException("confirm rental return", e);
        }
    }

    public boolean confirmReturn(Connection connection, long rentalId)
            throws SQLException {

        String sql = "UPDATE rentals "
                + "SET returned_at = strftime('%Y-%m-%d %H:%M:%S', 'now') "
                + "WHERE id = ? AND returned_at IS NULL";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, rentalId);
            return statement.executeUpdate() > 0;
        }
    }

    private Rental readById(Connection connection, long id)
            throws SQLException {

        String sql = "SELECT " + COLUMNS + " FROM rentals WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? map(row) : null;
            }
        }
    }

    private Rental map(ResultSet row) throws SQLException {
        String rentedAtValue = row.getString("rented_at");
        String returnedAtValue = row.getString("returned_at");

        LocalDateTime rentedAt = rentedAtValue == null
                ? null
                : LocalDateTime.parse(rentedAtValue, STORED_TIME);

        LocalDateTime returnedAt = returnedAtValue == null
                ? null
                : LocalDateTime.parse(returnedAtValue, STORED_TIME);

        return new Rental(
                row.getLong("id"),
                row.getLong("item_id"),
                row.getLong("renter_id"),
                row.getInt("days"),
                rentedAt,
                returnedAt
        );
    }
}
