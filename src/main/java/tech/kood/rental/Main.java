
package tech.kood.rental;

import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.infrastructure.StructuredLogger;
import tech.kood.rental.repository.ItemRepository;
import tech.kood.rental.repository.RentalRepository;
import tech.kood.rental.repository.UserRepository;
import tech.kood.rental.security.PasswordHasher;
import tech.kood.rental.service.ItemService;
import tech.kood.rental.service.RentalService;
import tech.kood.rental.service.UserService;
import tech.kood.rental.transport.CliHandler;

import java.io.IOException;
import java.sql.SQLException;

public final class Main {

    private static final String DEFAULT_DATABASE_PATH = "data/app.db";

    private Main() {
    }

    public static void main(String[] args) {
        String databasePath = args.length > 0
                ? args[0]
                : System.getenv().getOrDefault(
                        "DATABASE_PATH", DEFAULT_DATABASE_PATH);
        String logPath = System.getenv().getOrDefault(
                "LOG_PATH", "logs/app.log");
        try {
            StructuredLogger.configureFileLogging(logPath);
        } catch (IOException exception) {
            System.err.println(
                    "The application could not open its log file: "
                            + exception.getMessage());
            System.exit(1);
            return;
        }

        DatabaseConnection database = new DatabaseConnection(databasePath);

        try {
            database.initialize();
        } catch (SQLException exception) {
            StructuredLogger.error("db.startup.failed", exception);
            System.err.println(
                    "The application could not open its database. Check the log for details.");
            System.exit(1);
            return;
        }

        StructuredLogger.info("application.started");
        try {
            UserRepository users = new UserRepository(database);
            ItemRepository items = new ItemRepository(database);
            RentalRepository rentals = new RentalRepository(database);
            UserService userService =
                    new UserService(users, new PasswordHasher());
            ItemService itemService = new ItemService(items);
            RentalService rentalService =
                    new RentalService(database, items, rentals);

            new CliHandler(userService, itemService, rentalService).start();
        } catch (RuntimeException exception) {
            StructuredLogger.error("application.failed", exception);
            System.err.println(
                    "The application encountered an error. Check the log for details.");
        } finally {
            StructuredLogger.info("application.stopped");
        }
    }
}
