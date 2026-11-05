package tech.kood.rental.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.exception.DatabaseException;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemRepositoryTest {

    // JUnit gives every test its own empty temporary folder and deletes it afterwards,
    // so your real data folder is never touched
    @TempDir
    Path tempDir;

    private ItemRepository items;
    private long siim;      // id of the first user
    private long meelis;    // id of the second user

    @BeforeEach
    void setUp() throws Exception {
        // a brand new database file with the tables built, for every single test
        DatabaseConnection db = new DatabaseConnection(tempDir.resolve("app.db").toString());
        db.initialize();
        items = new ItemRepository(db);

        UserRepository users = new UserRepository(db);
        siim = users.insert("siim", "hash").id();
        meelis = users.insert("meelis", "hash").id();
    }

    // ---------- helpers ----------

    private Item newItem(long owner, String name, double cost, ItemStatus status) {
        return new Item(null, owner, name, "About " + name, cost, status);
    }

    private static List<String> names(List<Item> list) {
        return list.stream().map(Item::name).toList();
    }

    // A repository whose database folder does not exist, so every connection attempt fails
    private ItemRepository brokenRepository() {
        return new ItemRepository(
                new DatabaseConnection(tempDir.resolve("missing/app.db").toString()));
    }

    // ---------- saving and finding ----------

    @Test
    void insertReturnsTheStoredItem() {
        Item saved = items.insert(newItem(siim, "Drill", 4.5, ItemStatus.AVAILABLE));

        assertNotNull(saved.id());
        assertEquals(siim, saved.ownerId());
        assertEquals("Drill", saved.name());
        assertEquals("About Drill", saved.description());
        assertEquals(4.5, saved.costPerDay());
        assertEquals(ItemStatus.AVAILABLE, saved.status());
    }

    @Test
    void insertedItemCanBeFoundById() {
        Item saved = items.insert(newItem(siim, "Drill", 4, ItemStatus.AVAILABLE));

        Optional<Item> found = items.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals(saved, found.get());
    }

    @Test
    void unknownIdGivesAnEmptyResult() {
        assertTrue(items.findById(999).isEmpty());
    }

    @Test
    void oddCharactersAreStoredAsPlainText() {
        // quotes and SQL words must be treated as text, never as commands
        String odd = "o'brien\"; DROP TABLE items; --";

        Item saved = items.insert(newItem(siim, odd, 4, ItemStatus.AVAILABLE));

        assertEquals(odd, items.findById(saved.id()).get().name());
        // the items table still works
        assertDoesNotThrow(() -> items.insert(newItem(siim, "Drill", 4, ItemStatus.AVAILABLE)));
    }

    // ---------- the browse screen ----------

    @Test
    void browseListHasOnlyOthersAvailableItemsHighestCostFirst() {
        items.insert(newItem(siim, "Drill", 4, ItemStatus.AVAILABLE));            // mine: left out
        items.insert(newItem(meelis, "Saw", 5, ItemStatus.AVAILABLE));
        items.insert(newItem(meelis, "Washer", 8, ItemStatus.AVAILABLE));
        items.insert(newItem(meelis, "Rented saw", 3, ItemStatus.RENTED));        // not available: left out
        items.insert(newItem(meelis, "Hidden saw", 3, ItemStatus.UNLISTED));      // not available: left out

        List<Item> page = items.listAvailableNotOwnedBy(siim, 10, 0);

        assertEquals(List.of("Washer", "Saw"), names(page));
    }

    @Test
    void browseListIsCutIntoPages() {
        items.insert(newItem(meelis, "A", 8, ItemStatus.AVAILABLE));
        items.insert(newItem(meelis, "B", 6, ItemStatus.AVAILABLE));
        items.insert(newItem(meelis, "C", 5, ItemStatus.AVAILABLE));

        assertEquals(List.of("A", "B"), names(items.listAvailableNotOwnedBy(siim, 2, 0)));
        assertEquals(List.of("C"), names(items.listAvailableNotOwnedBy(siim, 2, 2)));
        assertTrue(items.listAvailableNotOwnedBy(siim, 2, 4).isEmpty());
    }

    @Test
    void browseListIsEmptyWhenNothingIsAvailable() {
        assertTrue(items.listAvailableNotOwnedBy(siim, 5, 0).isEmpty());
    }

    // ---------- my inventory ----------

    @Test
    void inventoryHasAllOfMyItemsWhateverTheirStatusAndNobodyElses() {
        items.insert(newItem(siim, "Drill", 4, ItemStatus.AVAILABLE));
        items.insert(newItem(siim, "Sprayer", 7, ItemStatus.UNLISTED));
        items.insert(newItem(siim, "Cutter", 4, ItemStatus.RENTED));
        items.insert(newItem(meelis, "Saw", 5, ItemStatus.AVAILABLE));            // not mine: left out

        List<Item> mine = items.listByOwner(siim, 10, 0);

        assertEquals(List.of("Drill", "Sprayer", "Cutter"), names(mine));
    }

    // ---------- changing the status ----------

    @Test
    void statusChangesWhenItIsStillWhatWasExpected() {
        Item saved = items.insert(newItem(siim, "Drill", 4, ItemStatus.AVAILABLE));

        assertTrue(items.changeStatus(saved.id(), ItemStatus.AVAILABLE, ItemStatus.UNLISTED));

        assertEquals(ItemStatus.UNLISTED, items.findById(saved.id()).get().status());
    }

    @Test
    void staleChangeIsRefusedAndNothingIsOverwritten() {
        Item saved = items.insert(newItem(siim, "Drill", 4, ItemStatus.UNLISTED));

        // someone expects it to be AVAILABLE, but it was delisted in between
        assertFalse(items.changeStatus(saved.id(), ItemStatus.AVAILABLE, ItemStatus.RENTED));

        assertEquals(ItemStatus.UNLISTED, items.findById(saved.id()).get().status());
    }

    @Test
    void changingAnUnknownItemChangesNothing() {
        assertFalse(items.changeStatus(999, ItemStatus.AVAILABLE, ItemStatus.UNLISTED));
    }

    // ---------- rejected input and database problems ----------

    @Test
    void itemMustHaveARealOwner() {
        // there is no user 999, so the database refuses the item
        DatabaseException e = assertThrows(DatabaseException.class,
                () -> items.insert(newItem(999, "Drill", 4, ItemStatus.AVAILABLE)));

        assertEquals("insert item", e.getOperation());
        assertInstanceOf(SQLException.class, e.getCause());   // the detail is kept for the log
    }

    @Test
    void repositoryKeepsWorkingAfterAFailure() {
        assertThrows(DatabaseException.class,
                () -> items.insert(newItem(999, "Drill", 4, ItemStatus.AVAILABLE)));

        // a failed call must not leave anything stuck
        assertDoesNotThrow(() -> items.insert(newItem(siim, "Drill", 4, ItemStatus.AVAILABLE)));
    }

    @Test
    void unusableDatabaseGivesAReadableMessageOnEveryOperation() {
        ItemRepository broken = brokenRepository();

        assertFailsReadably("insert item",
                () -> broken.insert(newItem(siim, "Drill", 4, ItemStatus.AVAILABLE)));
        assertFailsReadably("find item by id", () -> broken.findById(1));
        assertFailsReadably("list available items", () -> broken.listAvailableNotOwnedBy(1, 5, 0));
        assertFailsReadably("list items by owner", () -> broken.listByOwner(1, 5, 0));
        assertFailsReadably("change item status",
                () -> broken.changeStatus(1, ItemStatus.AVAILABLE, ItemStatus.UNLISTED));
    }

    // Checks that the call fails with a DatabaseException for the right operation,
    // with no SQLite wording in the message and the original error kept for the log
    private void assertFailsReadably(String operation, org.junit.jupiter.api.function.Executable call) {
        DatabaseException e = assertThrows(DatabaseException.class, call);

        assertEquals(operation, e.getOperation());
        assertFalse(e.getMessage().toLowerCase().contains("sqlite"));
        assertInstanceOf(SQLException.class, e.getCause());
    }
}