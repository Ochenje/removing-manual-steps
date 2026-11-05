
package tech.kood.rental.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kood.rental.domain.Rental;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.exception.DatabaseException;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RentalRepositoryTest {

    @TempDir
    Path tempDir;

    private RentalRepository rentals;
    private long ownerId;
    private long renterId;
    private long itemId;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseConnection db =
                new DatabaseConnection(tempDir.resolve("app.db").toString());
        db.initialize();

        UserRepository users = new UserRepository(db);
        ownerId = users.insert("owner", "hash").id();
        renterId = users.insert("renter", "hash").id();

        ItemRepository items = new ItemRepository(db);
        itemId = items.insert(new tech.kood.rental.domain.Item(
                null,
                ownerId,
                "Drill",
                "A power drill",
                5.0,
                tech.kood.rental.domain.ItemStatus.RENTED
        )).id();

        rentals = new RentalRepository(db);
    }

    private Rental newRental() {
        return new Rental(null, itemId, renterId, 3, null, null);
    }

    private RentalRepository brokenRepository() {
        return new RentalRepository(
                new DatabaseConnection(tempDir.resolve("missing/app.db").toString()));
    }

    // ---------- inserting and finding ----------

    @Test
    void insertReturnsTheStoredRental() {
        Rental saved = rentals.insert(newRental());

        assertNotNull(saved.id());
        assertEquals(itemId, saved.itemId());
        assertEquals(renterId, saved.renterId());
        assertEquals(3, saved.days());
        assertNotNull(saved.rentedAt());
        assertNull(saved.returnedAt());
    }

    @Test
    void insertedRentalCanBeFoundById() {
        Rental saved = rentals.insert(newRental());

        Optional<Rental> found = rentals.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved, found.get());
    }

    @Test
    void unknownIdGivesAnEmptyResult() {
        assertTrue(rentals.findById(999).isEmpty());
    }

    @Test
    void insertedRentalCanBeFoundAsActive() {
        Rental saved = rentals.insert(newRental());

        Optional<Rental> active = rentals.findActiveByItemId(itemId);

        assertTrue(active.isPresent());
        assertEquals(saved.id(), active.get().id());
        assertNull(active.get().returnedAt());
    }

    @Test
    void returnedRentalIsNoLongerActive() {
        Rental saved = rentals.insert(newRental());

        assertTrue(rentals.confirmReturn(saved.id()));

        assertTrue(rentals.findActiveByItemId(itemId).isEmpty());
    }

    @Test
    void itemWithoutActiveRentalGivesAnEmptyResult() {
        assertTrue(rentals.findActiveByItemId(itemId).isEmpty());
    }

    // ---------- confirming returns ----------

    @Test
    void confirmReturnRecordsTheReturnTime() {
        Rental saved = rentals.insert(newRental());

        assertTrue(rentals.confirmReturn(saved.id()));

        Rental returned = rentals.findById(saved.id()).orElseThrow();

        assertNotNull(returned.returnedAt());
        assertFalse(returned.returnedAt().isBefore(saved.rentedAt()));
    }

    @Test
    void confirmingTheSameReturnTwiceIsRefused() {
        Rental saved = rentals.insert(newRental());

        assertTrue(rentals.confirmReturn(saved.id()));

        LocalDateTime firstReturnTime =
                rentals.findById(saved.id()).orElseThrow().returnedAt();

        assertFalse(rentals.confirmReturn(saved.id()));

        LocalDateTime secondReturnTime =
                rentals.findById(saved.id()).orElseThrow().returnedAt();

        assertEquals(firstReturnTime, secondReturnTime);
    }

    @Test
    void confirmingReturnForUnknownRentalChangesNothing() {
        assertFalse(rentals.confirmReturn(999));
    }

    // ---------- database problems ----------

    @Test
    void rentalMustReferenceARealItem() {
        Rental invalid = new Rental(null, 999, renterId, 3, null, null);

        DatabaseException e = assertThrows(DatabaseException.class,
                () -> rentals.insert(invalid));

        assertEquals("insert rental", e.getOperation());
        assertInstanceOf(SQLException.class, e.getCause());
    }

    @Test
    void rentalMustReferenceARealRenter() {
        Rental invalid = new Rental(null, itemId, 999, 3, null, null);

        DatabaseException e = assertThrows(DatabaseException.class,
                () -> rentals.insert(invalid));

        assertEquals("insert rental", e.getOperation());
        assertInstanceOf(SQLException.class, e.getCause());
    }

    @Test
    void repositoryKeepsWorkingAfterAnInsertFailure() {
        Rental invalid = new Rental(null, itemId, 999, 3, null, null);

        assertThrows(DatabaseException.class, () -> rentals.insert(invalid));

        assertDoesNotThrow(() -> rentals.insert(newRental()));
    }

    @Test
    void unusableDatabaseGivesReadableMessages() {
        RentalRepository broken = brokenRepository();

        assertFailsReadably("insert rental", () -> broken.insert(newRental()));
        assertFailsReadably("find rental by id", () -> broken.findById(1));
        assertFailsReadably("find active rental by item id",
                () -> broken.findActiveByItemId(itemId));
        assertFailsReadably("confirm rental return", () -> broken.confirmReturn(1));
    }

    private void assertFailsReadably(
            String operation,
            org.junit.jupiter.api.function.Executable call) {

        DatabaseException e = assertThrows(DatabaseException.class, call);

        assertEquals(operation, e.getOperation());
        assertFalse(e.getMessage().toLowerCase().contains("sqlite"));
        assertInstanceOf(SQLException.class, e.getCause());
    }
}
