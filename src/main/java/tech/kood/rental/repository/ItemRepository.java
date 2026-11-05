
package tech.kood.rental.repository;

import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.exception.DatabaseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles all database operations involving items.
 * Ownership and rental rules are enforced by the service layer.
 */
public class ItemRepository {

    private static final String COLUMNS =
            "id, owner_id, name, description, cost_per_day, status";

    private final DatabaseConnection database;

    public ItemRepository(DatabaseConnection database) {
        this.database = database;
    }

    public Item insert(Item item) {
        String sql = "INSERT INTO items "
                + "(owner_id, name, description, cost_per_day, status) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setLong(1, item.ownerId());
            statement.setString(2, item.name());
            statement.setString(3, item.description());
            statement.setDouble(4, item.costPerDay());
            statement.setString(5, item.status().name());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The inserted item ID could not be retrieved.");
                }

                Item saved = readById(connection, keys.getLong(1));

                if (saved == null) {
                    throw new SQLException("The inserted item could not be read back.");
                }

                return saved;
            }

        } catch (SQLException e) {
            throw new DatabaseException("insert item", e);
        }
    }

    public Optional<Item> findById(long id) {
        try (Connection connection = database.getConnection()) {
            return Optional.ofNullable(readById(connection, id));
        } catch (SQLException e) {
            throw new DatabaseException("find item by id", e);
        }
    }

    /**
     * Finds an item using a connection supplied by the service.
     * The caller is responsible for managing the connection.
     */
    public Optional<Item> findById(Connection connection, long id)
            throws SQLException {
        return Optional.ofNullable(readById(connection, id));
    }

    public List<Item> listAvailableNotOwnedBy(
            long userId, int limit, int offset) {

        String sql = "SELECT " + COLUMNS + " FROM items "
                + "WHERE status = 'AVAILABLE' AND owner_id <> ? "
                + "ORDER BY cost_per_day DESC, name, id LIMIT ? OFFSET ?";

        return list("list available items", sql, userId, limit, offset);
    }

    public long countAvailableNotOwnedBy(long userId) {
        return count(
                "count available items",
                "SELECT COUNT(*) FROM items "
                        + "WHERE status = 'AVAILABLE' AND owner_id <> ?",
                userId);
    }

    public List<Item> listByOwner(long ownerId, int limit, int offset) {
        String sql = "SELECT " + COLUMNS + " FROM items "
                + "WHERE owner_id = ? ORDER BY id LIMIT ? OFFSET ?";

        return list("list items by owner", sql, ownerId, limit, offset);
    }

    public long countByOwner(long ownerId) {
        return count(
                "count items by owner",
                "SELECT COUNT(*) FROM items WHERE owner_id = ?",
                ownerId);
    }

    public List<Item> listRentedByOwner(long ownerId, int limit, int offset) {
        String sql = "SELECT " + COLUMNS + " FROM items "
                + "WHERE owner_id = ? AND status = 'RENTED' "
                + "ORDER BY id LIMIT ? OFFSET ?";

        return list("list rented items by owner", sql, ownerId, limit, offset);
    }

    public long countRentedByOwner(long ownerId) {
        return count(
                "count rented items by owner",
                "SELECT COUNT(*) FROM items "
                        + "WHERE owner_id = ? AND status = 'RENTED'",
                ownerId);
    }

    public boolean changeStatus(
            long id, ItemStatus expected, ItemStatus next) {

        try (Connection connection = database.getConnection()) {
            return changeStatus(connection, id, expected, next);
        } catch (SQLException e) {
            throw new DatabaseException("change item status", e);
        }
    }

    /**
     * Changes an item's status using a connection supplied by the service.
     * The update succeeds only when the current status matches the expected status.
     */
    public boolean changeStatus(
            Connection connection,
            long id,
            ItemStatus expected,
            ItemStatus next
    ) throws SQLException {

        String sql = "UPDATE items SET status = ? WHERE id = ? AND status = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, next.name());
            statement.setLong(2, id);
            statement.setString(3, expected.name());

            return statement.executeUpdate() > 0;
        }
    }

    private long count(String operation, String sql, long ownerId) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("The count could not be retrieved.");
                }
                return row.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException(operation, e);
        }
    }

    private List<Item> list(
            String operation, String sql, long id, int limit, int offset) {

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);
            statement.setInt(2, limit);
            statement.setInt(3, offset);

            try (ResultSet rows = statement.executeQuery()) {
                List<Item> items = new ArrayList<>();

                while (rows.next()) {
                    items.add(map(rows));
                }

                return items;
            }

        } catch (SQLException e) {
            throw new DatabaseException(operation, e);
        }
    }

    private Item readById(Connection connection, long id)
            throws SQLException {

        String sql = "SELECT " + COLUMNS + " FROM items WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? map(row) : null;
            }
        }
    }

    private Item map(ResultSet row) throws SQLException {
        return new Item(
                row.getLong("id"),
                row.getLong("owner_id"),
                row.getString("name"),
                row.getString("description"),
                row.getDouble("cost_per_day"),
                ItemStatus.valueOf(row.getString("status"))
        );
    }
}
