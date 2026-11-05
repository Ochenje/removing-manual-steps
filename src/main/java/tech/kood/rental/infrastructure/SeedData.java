package tech.kood.rental.infrastructure;

import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.security.PasswordHasher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Builds data/app.db: the schema, four demo users, nine items and one active rental.
 * Run it once from the IDE (the green arrow next to main), then commit the file it makes.
 * Running it again REPLACES the old file, so it is only for making the demo database.
 */
public final class SeedData {

    // Demo-only password for all four demo users. The database stores only its bcrypt hash.
    static final String DEMO_PASSWORD = "password123";

    // The demo users. Their ids are 1 to 4, in this order
    static final List<String> USERNAMES = List.of("siim", "meelis", "liisa", "marili");

    // One item to be lent out
    private record SeedItem(int ownerId, String name, String description,
                            double costPerDay, ItemStatus status) {
    }

    // The demo items. Their ids are 1 to 9, in this order. The owner is a position in USERNAMES
    private static final List<SeedItem> ITEMS = List.of(
            new SeedItem(2, "Pressure washer", "Electric, 1800 psi", 8, ItemStatus.AVAILABLE),
            new SeedItem(3, "Tent (4-person)", "Waterproof, easy to set up", 6, ItemStatus.AVAILABLE),
            new SeedItem(2, "Circular saw", "Corded, 190 mm blade", 5, ItemStatus.AVAILABLE),
            new SeedItem(4, "Ladder", "Aluminium, 3 metres", 5, ItemStatus.AVAILABLE),
            new SeedItem(1, "Drill", "Cordless, two batteries", 4, ItemStatus.AVAILABLE),
            new SeedItem(3, "Wheelbarrow", "Steel tray, pneumatic tyre", 3, ItemStatus.AVAILABLE),
            new SeedItem(4, "Hedge trimmer", "Electric, 60 cm blade", 6, ItemStatus.AVAILABLE),
            new SeedItem(1, "Tile cutter", "Manual, cuts up to 60 cm", 4, ItemStatus.RENTED),
            new SeedItem(1, "Paint sprayer", "Airless, for walls and fences", 7, ItemStatus.UNLISTED));

    private SeedData() {
        // utility class: nobody should create one
    }

    // Run from the IDE with no arguments, this writes data/app.db in the project folder
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length > 0 ? args[0] : "data/app.db");
        build(file);
        System.out.println("Seeded database written to " + file.toAbsolutePath());
    }

    // Builds a fresh database at the given path, replacing any file that is already there
    public static void build(Path file) throws SQLException, IOException {
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Files.deleteIfExists(absolute);

        
        String url = "jdbc:sqlite:" + absolute.toString().replace('\\', '/');
        try (Connection connection = DriverManager.getConnection(url)) {
            // SQLite ignores foreign keys unless every connection switches them on
            run(connection, "PRAGMA foreign_keys = ON");
            loadSchema(connection);
            insertUsers(connection);
            insertItems(connection);
            insertRental(connection);
        }
    }

    // ---------- the steps ----------

    private static void insertUsers(Connection connection) throws SQLException {
        PasswordHasher hasher = new PasswordHasher();
        String sql = "INSERT INTO users (id, username, password) VALUES (?, ?, ?)";

        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (int i = 0; i < USERNAMES.size(); i++) {
                insert.setInt(1, i + 1);
                insert.setString(2, USERNAMES.get(i));
                insert.setString(3, hasher.hash(DEMO_PASSWORD));   // the hash, never the plain password
                insert.executeUpdate();
            }
        }
    }

    private static void insertItems(Connection connection) throws SQLException {
        String sql = "INSERT INTO items (id, owner_id, name, description, cost_per_day, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (int i = 0; i < ITEMS.size(); i++) {
                SeedItem item = ITEMS.get(i);
                insert.setInt(1, i + 1);
                insert.setInt(2, item.ownerId());
                insert.setString(3, item.name());
                insert.setString(4, item.description());
                insert.setDouble(5, item.costPerDay());
                insert.setString(6, item.status().name());
                insert.executeUpdate();
            }
        }
    }

    // meelis (user 2) is renting siim's tile cutter (item 8) for 3 days, so siim can confirm the return
    private static void insertRental(Connection connection) throws SQLException {
        run(connection, "INSERT INTO rentals (id, item_id, renter_id, days) VALUES (1, 8, 2, 3)");
    }

    // ---------- helpers ----------

    // Runs one SQL statement
    private static void run(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    // Reads schema.sql from inside the application and runs each statement in it
    private static void loadSchema(Connection connection) throws SQLException, IOException {
        try (InputStream input = SeedData.class.getResourceAsStream("/schema.sql")) {
            if (input == null) {
                throw new IOException("schema.sql was not found");
            }
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            // drop the "--" comments first, so a semicolon inside a comment cannot cut a statement
            StringBuilder cleaned = new StringBuilder();
            for (String line : schema.split("\\R")) {
                int comment = line.indexOf("--");
                cleaned.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
            }
            for (String part : cleaned.toString().split(";")) {
                if (!part.isBlank()) {
                    run(connection, part.trim());
                }
            }
        }
    }
}