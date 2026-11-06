package tech.kood.rental.service;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.domain.Rental;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.ItemRepository;
import tech.kood.rental.repository.RentalRepository;
import tech.kood.rental.repository.UserRepository;
import tech.kood.rental.repository.exception.DatabaseException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RentalWorkflowTest {

    @TempDir
    Path tempDir;
    private DatabaseConnection database;
    private ItemRepository items;
    private RentalRepository rentals;
    private RentalService service;
    private long ownerId;
    private long renterId;
    private Item availableItem;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseConnection(tempDir.resolve("rental.db").toString());
        database.initialize();

        UserRepository users = new UserRepository(database);
        ownerId = users.insert("owner", "hash").id();
        renterId = users.insert("renter", "hash").id();
        items = new ItemRepository(database);
        rentals = new RentalRepository(database);
        service = new RentalService(database, items, rentals);
        availableItem = items.insert(new Item(
                null,
                ownerId,
                "Camera",
                "Mirrorless camera",
                15,
                ItemStatus.AVAILABLE));
    }

    @Test
    void rentsAnotherUsersAvailableItemAndPersistsBothChanges() {
        Rental rental = service.rentItem(renterId, availableItem.id(), 3);

        assertNotNull(rental.id());
        assertEquals(availableItem.id(), rental.itemId());
        assertEquals(renterId, rental.renterId());
        assertEquals(3, rental.days());
        assertNotNull(rental.rentedAt());
        assertNull(rental.returnedAt());
        assertEquals(
                ItemStatus.RENTED,
                items.findById(availableItem.id()).orElseThrow().status());
        assertEquals(
                rental.id(),
                rentals.findActiveByItemId(availableItem.id())
                        .orElseThrow().id());
    }

    @Test
    void refusesToRentOwnItemWithoutChangingDatabase() {
        assertThrows(
                ValidationException.class,
                () -> service.rentItem(ownerId, availableItem.id(), 2));

        assertEquals(
                ItemStatus.AVAILABLE,
                items.findById(availableItem.id()).orElseThrow().status());
        assertNull(rentals.findActiveByItemId(availableItem.id()).orElse(null));
    }

    @Test
    void refusesInvalidDurationBeforeChangingDatabase() {
        assertThrows(
                ValidationException.class,
                () -> service.rentItem(renterId, availableItem.id(), 0));

        assertEquals(
                ItemStatus.AVAILABLE,
                items.findById(availableItem.id()).orElseThrow().status());
    }

    @Test
    void rollsBackItemStatusWhenRentalInsertFails() {
        assertThrows(
                DatabaseException.class,
                () -> service.rentItem(999_999, availableItem.id(), 2));

        assertEquals(
                ItemStatus.AVAILABLE,
                items.findById(availableItem.id()).orElseThrow().status());
        assertNull(rentals.findActiveByItemId(availableItem.id()).orElse(null));
    }

    @Test
    void ownerReturnUpdatesRentalAndMakesItemAvailable() {
        Rental active = service.rentItem(renterId, availableItem.id(), 2);

        Rental returned = service.confirmReturn(ownerId, availableItem.id());

        assertEquals(active.id(), returned.id());
        assertNotNull(returned.returnedAt());
        assertEquals(
                ItemStatus.AVAILABLE,
                items.findById(availableItem.id()).orElseThrow().status());
        assertNull(rentals.findActiveByItemId(availableItem.id()).orElse(null));
    }

    @Test
    void onlyOwnerCanReadActiveRentalDetails() {
        Rental active = service.rentItem(renterId, availableItem.id(), 2);

        assertEquals(
                active,
                service.getActiveRentalForOwner(ownerId, availableItem.id()));
        assertThrows(
                ValidationException.class,
                () -> service.getActiveRentalForOwner(
                        renterId, availableItem.id()));
        assertNotNull(rentals.findActiveByItemId(availableItem.id()).orElse(null));
    }

    @Test
    void refusesReturnByNonOwnerWithoutChangingDatabase() {
        service.rentItem(renterId, availableItem.id(), 2);

        assertThrows(
                ValidationException.class,
                () -> service.confirmReturn(renterId, availableItem.id()));

        assertEquals(
                ItemStatus.RENTED,
                items.findById(availableItem.id()).orElseThrow().status());
        assertNotNull(rentals.findActiveByItemId(availableItem.id()).orElse(null));
    }

    @Test
    void refusesReturnWhenThereIsNoActiveRental() {
        assertThrows(
                ValidationException.class,
                () -> service.confirmReturn(ownerId, availableItem.id()));

        assertEquals(
                ItemStatus.AVAILABLE,
                items.findById(availableItem.id()).orElseThrow().status());
    }

    @Test
    void refusesRentalLongerThanOneYearBeforeChangingDatabase() {
        assertThrows(
                ValidationException.class,
                () -> service.rentItem(renterId, availableItem.id(), 366));

        assertEquals(
                ItemStatus.AVAILABLE,
                items.findById(availableItem.id()).orElseThrow().status());
        assertNull(rentals.findActiveByItemId(availableItem.id()).orElse(null));
    }
    @Test
    void refusesToRentNonexistentItem() {
        assertThrows(
                ValidationException.class,
                () -> service.rentItem(renterId, Long.MAX_VALUE, 2));
    }

    @Test
    void refusesToRentAlreadyRentedItem() {
        Item rentedItem = items.insert(new Item(
                null,
                ownerId,
                "Laptop",
                "Already rented laptop",
                20,
                ItemStatus.RENTED));

        assertThrows(
                ValidationException.class,
                () -> service.rentItem(renterId, rentedItem.id(), 2));
    }

    @Test
    void refusesReturnForNonexistentItem() {
        assertThrows(
                ValidationException.class,
                () -> service.confirmReturn(ownerId, Long.MAX_VALUE));
    }

    @Test
    void refusesReturnWhenItemIsNotRented() {
        assertThrows(
                ValidationException.class,
                () -> service.confirmReturn(ownerId, availableItem.id()));
    }

    @Test
    void refusesToReadRentalDetailsForNonexistentItem() {
        assertThrows(
                ValidationException.class,
                () -> service.getActiveRentalForOwner(ownerId, Long.MAX_VALUE));
    }

    @Test
    void refusesToReadRentalDetailsWhenItemIsAvailable() {
        assertThrows(
                ValidationException.class,
                () -> service.getActiveRentalForOwner(ownerId, availableItem.id()));
    }
    @Test
    void refusesReturnWhenRentedItemHasNoActiveRental() {
        Item rentedItem = items.insert(new Item(
                null,
                ownerId,
                "Camera",
                "Rented item without rental record",
                15,
                ItemStatus.RENTED));

        assertThrows(
                ValidationException.class,
                () -> service.confirmReturn(ownerId, rentedItem.id()));
    }
    @Test
    void rollsBackWhenRentalReturnUpdateFails() throws Exception {
        Rental rental = service.rentItem(renterId, availableItem.id(), 3);

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TRIGGER ignore_rental_return
                BEFORE UPDATE OF returned_at ON rentals
                WHEN OLD.returned_at IS NULL
                BEGIN
                    SELECT RAISE(IGNORE);
                END
                """);
        }

        assertThrows(
                ValidationException.class,
                () -> service.confirmReturn(ownerId, availableItem.id()));

        assertEquals(
                ItemStatus.RENTED,
                items.findById(availableItem.id()).orElseThrow().status());

        assertNull(rentals.findById(rental.id()).orElseThrow().returnedAt());
    }
    @Test
    void rollsBackWhenItemStatusUpdateFailsDuringReturn() throws Exception {
        Rental rental = service.rentItem(renterId, availableItem.id(), 3);

        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TRIGGER ignore_item_return_status
                BEFORE UPDATE OF status ON items
                WHEN OLD.status = 'RENTED' AND NEW.status = 'AVAILABLE'
                BEGIN
                    SELECT RAISE(IGNORE);
                END
                """);
        }

        assertThrows(
                ValidationException.class,
                () -> service.confirmReturn(ownerId, availableItem.id()));

        assertEquals(
                ItemStatus.RENTED,
                items.findById(availableItem.id()).orElseThrow().status());

        assertNull(rentals.findById(rental.id()).orElseThrow().returnedAt());
    }

    @Test
    void rejectsNullDatabaseInConfiguredConstructor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RentalService(null, items, rentals)
        );
    }

    @Test
    void rejectsNullItemRepositoryInConfiguredConstructor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RentalService(database, null, rentals)
        );
    }

    @Test
    void rejectsNullRentalRepositoryInConfiguredConstructor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RentalService(database, items, null)
        );
    }
    @Test
    void rejectsInvalidOwnerIdWhenReadingActiveRental() {
        assertThrows(
                ValidationException.class,
                () -> service.getActiveRentalForOwner(
                        0, availableItem.id())
        );
    }

    @Test
    void rejectsInvalidItemIdWhenConfirmingReturn() {
        assertThrows(
                ValidationException.class,
                () -> service.confirmReturn(ownerId, 0)
        );
    }

    @Test
    void propagatesDatabaseExceptionWhenReadingActiveRental() {
        service.rentItem(renterId, availableItem.id(), 3);

        RentalRepository failingRentals = new RentalRepository(database) {
            @Override
            public java.util.Optional<Rental> findActiveByItemId(
                    Connection connection, long itemId)
                    throws java.sql.SQLException {
                throw new DatabaseException(
                        "simulated repository failure",
                        new java.sql.SQLException("simulated failure"));
            }
        };

        RentalService failingService =
                new RentalService(database, items, failingRentals);

        assertThrows(
                DatabaseException.class,
                () -> failingService.getActiveRentalForOwner(
                        ownerId, availableItem.id()));
    }

    @Test
    void propagatesDatabaseExceptionWhenConfirmingReturn() {
        service.rentItem(renterId, availableItem.id(), 3);

        RentalRepository failingRentals = new RentalRepository(database) {
            @Override
            public java.util.Optional<Rental> findActiveByItemId(
                    Connection connection, long itemId)
                    throws java.sql.SQLException {
                throw new DatabaseException(
                        "simulated repository failure",
                        new java.sql.SQLException("simulated failure"));
            }
        };

        RentalService failingService =
                new RentalService(database, items, failingRentals);

        assertThrows(
                DatabaseException.class,
                () -> failingService.confirmReturn(
                        ownerId, availableItem.id()));
    }}
