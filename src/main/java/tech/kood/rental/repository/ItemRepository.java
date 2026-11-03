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
 * The only place that reads and writes the items table.
 * Every method opens its own connection and closes it again, even when it fails.
 * Failures become a DatabaseException, so no SQLite wording reaches the user.
 * Who may do what (ownership) is decided in the service, not here.
 */
public class ItemRepository {

    private static final String COLUMNS = "id, owner_id, name, description, cost_per_day, status";

    // Knows how to hand out connections
    private final DatabaseConnection database;

    public ItemRepository(DatabaseConnection database) {
        this.database = database;
    }

    // Saves a new item and returns it as stored (with its id filled in)
    public Item insert(Item item) {
        // The ? marks are blanks we fill in below, so odd characters
        // in a name can never be treated as SQL commands
        String sql = "INSERT INTO items (owner_id, name, description, cost_per_day, status) "
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

            // Ask the database which id it just handed out, then read the row back
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                Item saved = readById(connection, keys.getLong(1));
                if (saved == null) {
                    throw new SQLException("the inserted item could not be read back");
                }
                return saved;
            }

        } catch (SQLException e) {
            // TODO (after StructuredLogger is wired in): log the failure with the operation name
            throw new DatabaseException("insert item", e);
        }
    }

    // Finds an item by id. A missing id is normal, so we return an empty box.
    public Optional<Item> findById(long id) {
        try (Connection connection = database.getConnection()) {
            return Optional.ofNullable(readById(connection, id));
        } catch (SQLException e) {
            throw new DatabaseException("find item by id", e);
        }
    }

    // The browse screen: available items that are not the user's own, highest cost first.
    // limit and offset give one page. The service asks for one more than a page,
    // to know whether there is a next page.
    public List<Item> listAvailableNotOwnedBy(long userId, int limit, int offset) {
        String sql = "SELECT " + COLUMNS + " FROM items "
                + "WHERE status = 'AVAILABLE' AND owner_id <> ? "
                + "ORDER BY cost_per_day DESC, name, id LIMIT ? OFFSET ?";
        return list("list available items", sql, userId, limit, offset);
    }

    // The "my inventory" screen: every item the user owns, whatever its status
    public List<Item> listByOwner(long ownerId, int limit, int offset) {
        String sql = "SELECT " + COLUMNS + " FROM items "
                + "WHERE owner_id = ? ORDER BY id LIMIT ? OFFSET ?";
        return list("list items by owner", sql, ownerId, limit, offset);
    }

    // Changes the status only if the item is still in the expected one.
    // Returns false when it is not (or the item is gone), so a stale click
    // never overwrites a change someone else made in between.
    public boolean changeStatus(long id, ItemStatus expected, ItemStatus next) {
        String sql = "UPDATE items SET status = ? WHERE id = ? AND status = ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, next.name());
            statement.setLong(2, id);
            statement.setString(3, expected.name());
            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DatabaseException("change item status", e);
        }
    }

    // ---------- helpers ----------

    // Runs a list query that takes (id, limit, offset) and reads every row it returns
    private List<Item> list(String operation, String sql, long id, int limit, int offset) {
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

    // Reads one item on a connection that is already open. Returns null when there is no such id.
    private Item readById(Connection connection, long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM items WHERE id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? map(row) : null;
            }
        }
    }

    // Turns one database row into an Item object.
    // The status column only holds the three known words (schema.sql enforces it).
    private Item map(ResultSet row) throws SQLException {
        return new Item(
                row.getLong("id"),
                row.getLong("owner_id"),
                row.getString("name"),
                row.getString("description"),
                row.getDouble("cost_per_day"),
                ItemStatus.valueOf(row.getString("status")));
    }
}