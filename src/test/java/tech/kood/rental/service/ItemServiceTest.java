package tech.kood.rental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.repository.ItemRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemServiceTest {

    private FakeItemRepository repository;
    private ItemService service;

    private static final long OWNER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    @BeforeEach
    void setUp() {
        repository = new FakeItemRepository();
        service = new ItemService(repository);
    }

    // --------------------------------------------------
    // Constructor
    // --------------------------------------------------

    @Test
    void constructorRejectsNullRepository() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ItemService(null));
    }

    // --------------------------------------------------
    // Listing items
    // --------------------------------------------------

    @Test
    void listItemSuccessfully() {
        Item result = service.listItem(
                OWNER_ID,
                " Laptop ",
                " Gaming laptop ",
                100.0);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals(OWNER_ID, result.ownerId());
        assertEquals("Laptop", result.name());
        assertEquals("Gaming laptop", result.description());
        assertEquals(100.0, result.costPerDay());
        assertEquals(ItemStatus.AVAILABLE, result.status());
    }

    @Test
    void listItemRejectsInvalidUserId() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        0, "Laptop", "Description", 100));
    }

    @Test
    void listItemRejectsNegativeUserId() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        -1, "Laptop", "Description", 100));
    }

    @Test
    void listItemRejectsNullName() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        OWNER_ID, null, "Description", 100));
    }

    @Test
    void listItemRejectsBlankName() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        OWNER_ID, "   ", "Description", 100));
    }

    @Test
    void listItemRejectsNullDescription() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        OWNER_ID, "Laptop", null, 100));
    }

    @Test
    void listItemRejectsBlankDescription() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        OWNER_ID, "Laptop", "   ", 100));
    }

    @Test
    void listItemRejectsNegativeCost() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        OWNER_ID, "Laptop", "Description", -1));
    }

    @Test
    void listItemRejectsNaNCost() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        OWNER_ID,
                        "Laptop",
                        "Description",
                        Double.NaN));
    }

    @Test
    void listItemRejectsPositiveInfinityCost() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        OWNER_ID,
                        "Laptop",
                        "Description",
                        Double.POSITIVE_INFINITY));
    }

    @Test
    void listItemRejectsNegativeInfinityCost() {
        assertThrows(
                ValidationException.class,
                () -> service.listItem(
                        OWNER_ID,
                        "Laptop",
                        "Description",
                        Double.NEGATIVE_INFINITY));
    }

    @Test
    void listItemAcceptsZeroCost() {
        Item result = service.listItem(
                OWNER_ID, "Laptop", "Description", 0);

        assertEquals(0, result.costPerDay());
    }

    @Test
    void listItemRejectsNullRepositoryResult() {
        repository.nullInsertResult = true;

        assertThrows(
                IllegalStateException.class,
                () -> service.listItem(
                        OWNER_ID, "Laptop", "Description", 100));
    }

    @Test
    void listItemRejectsSavedItemWithoutId() {
        repository.insertWithoutId = true;

        assertThrows(
                IllegalStateException.class,
                () -> service.listItem(
                        OWNER_ID, "Laptop", "Description", 100));
    }

    @Test
    void listItemHandlesRepositoryException() {
        repository.insertFailure =
                new RuntimeException("Database unavailable");

        assertThrows(
                RuntimeException.class,
                () -> service.listItem(
                        OWNER_ID, "Laptop", "Description", 100));
    }

    // --------------------------------------------------
    // View inventory
    // --------------------------------------------------

    @Test
    void viewMyInventoryUsesFirstPageByDefault() {
        repository.addItem(item(1, OWNER_ID, ItemStatus.AVAILABLE));

        List<Item> result = service.viewMyInventory(OWNER_ID);

        assertEquals(1, result.size());
        assertEquals(5, repository.lastLimit);
        assertEquals(0, repository.lastOffset);
    }

    @Test
    void viewMyInventorySupportsPagination() {
        service.viewMyInventory(OWNER_ID, 3);

        assertEquals(5, repository.lastLimit);
        assertEquals(10, repository.lastOffset);
    }

    @Test
    void viewMyInventoryReturnsEmptyList() {
        List<Item> result = service.viewMyInventory(OWNER_ID);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void viewMyInventoryRejectsInvalidUser() {
        assertThrows(
                ValidationException.class,
                () -> service.viewMyInventory(0));
    }

    @Test
    void viewMyInventoryRejectsPageZero() {
        assertThrows(
                ValidationException.class,
                () -> service.viewMyInventory(OWNER_ID, 0));
    }

    @Test
    void viewMyInventoryRejectsNegativePage() {
        assertThrows(
                ValidationException.class,
                () -> service.viewMyInventory(OWNER_ID, -1));
    }

    @Test
    void viewMyInventoryRejectsPageWithOverflowingOffset() {
        assertThrows(
                ValidationException.class,
                () -> service.viewMyInventory(
                        OWNER_ID, Integer.MAX_VALUE));
    }

    @Test
    void viewMyInventoryRejectsNullRepositoryResult() {
        repository.nullOwnerList = true;

        assertThrows(
                IllegalStateException.class,
                () -> service.viewMyInventory(OWNER_ID));
    }

    @Test
    void viewMyInventoryHandlesRepositoryException() {
        repository.listFailure =
                new RuntimeException("Database unavailable");

        assertThrows(
                RuntimeException.class,
                () -> service.viewMyInventory(OWNER_ID));
    }

    // --------------------------------------------------
    // Browse available items
    // --------------------------------------------------

    @Test
    void browseAvailableItemsUsesFirstPageByDefault() {
        repository.addItem(
                item(1, OTHER_USER_ID, ItemStatus.AVAILABLE));

        List<Item> result =
                service.browseAvailableItems(OWNER_ID);

        assertEquals(1, result.size());
        assertEquals(5, repository.lastLimit);
        assertEquals(0, repository.lastOffset);
    }

    @Test
    void browseAvailableItemsSupportsPagination() {
        service.browseAvailableItems(OWNER_ID, 2);

        assertEquals(5, repository.lastLimit);
        assertEquals(5, repository.lastOffset);
    }

    @Test
    void browseAvailableItemsReturnsEmptyList() {
        List<Item> result =
                service.browseAvailableItems(OWNER_ID);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void browseAvailableItemsRejectsInvalidUser() {
        assertThrows(
                ValidationException.class,
                () -> service.browseAvailableItems(0));
    }

    @Test
    void browseAvailableItemsRejectsInvalidPage() {
        assertThrows(
                ValidationException.class,
                () -> service.browseAvailableItems(OWNER_ID, 0));
    }

    @Test
    void browseAvailableItemsRejectsOverflowingPage() {
        assertThrows(
                ValidationException.class,
                () -> service.browseAvailableItems(
                        OWNER_ID, Integer.MAX_VALUE));
    }

    @Test
    void browseAvailableItemsRejectsNullRepositoryResult() {
        repository.nullAvailableList = true;

        assertThrows(
                IllegalStateException.class,
                () -> service.browseAvailableItems(OWNER_ID));
    }

    @Test
    void browseAvailableItemsHandlesRepositoryException() {
        repository.listFailure =
                new RuntimeException("Database unavailable");

        assertThrows(
                RuntimeException.class,
                () -> service.browseAvailableItems(OWNER_ID));
    }

    // --------------------------------------------------
    // Delist items
    // --------------------------------------------------

    @Test
    void delistItemSuccessfully() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.AVAILABLE));

        Item result = service.delistItem(OWNER_ID, 1);

        assertEquals(ItemStatus.UNLISTED, result.status());
    }

    @Test
    void delistItemRejectsInvalidUser() {
        assertThrows(
                ValidationException.class,
                () -> service.delistItem(0, 1));
    }

    @Test
    void delistItemRejectsInvalidItemId() {
        assertThrows(
                ValidationException.class,
                () -> service.delistItem(OWNER_ID, 0));
    }

    @Test
    void delistItemRejectsMissingItem() {
        assertThrows(
                ValidationException.class,
                () -> service.delistItem(OWNER_ID, 99));
    }

    @Test
    void delistItemRejectsWrongOwner() {
        repository.addItem(
                item(1, OTHER_USER_ID, ItemStatus.AVAILABLE));

        assertThrows(
                ValidationException.class,
                () -> service.delistItem(OWNER_ID, 1));
    }

    @Test
    void delistItemRejectsRentedItem() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.RENTED));

        assertThrows(
                ValidationException.class,
                () -> service.delistItem(OWNER_ID, 1));
    }

    @Test
    void delistItemRejectsAlreadyUnlistedItem() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.UNLISTED));

        assertThrows(
                ValidationException.class,
                () -> service.delistItem(OWNER_ID, 1));
    }

    @Test
    void delistItemRejectsFailedStatusChange() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.AVAILABLE));

        repository.statusChangeResult = false;

        assertThrows(
                ValidationException.class,
                () -> service.delistItem(OWNER_ID, 1));
    }

    @Test
    void delistItemHandlesRepositoryException() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.AVAILABLE));

        repository.statusFailure =
                new RuntimeException("Database unavailable");

        assertThrows(
                RuntimeException.class,
                () -> service.delistItem(OWNER_ID, 1));
    }

    // --------------------------------------------------
    // Relist items
    // --------------------------------------------------

    @Test
    void relistItemSuccessfully() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.UNLISTED));

        Item result = service.relistItem(OWNER_ID, 1);

        assertEquals(ItemStatus.AVAILABLE, result.status());
    }

    @Test
    void relistItemRejectsInvalidUser() {
        assertThrows(
                ValidationException.class,
                () -> service.relistItem(0, 1));
    }

    @Test
    void relistItemRejectsInvalidItemId() {
        assertThrows(
                ValidationException.class,
                () -> service.relistItem(OWNER_ID, 0));
    }

    @Test
    void relistItemRejectsMissingItem() {
        assertThrows(
                ValidationException.class,
                () -> service.relistItem(OWNER_ID, 99));
    }

    @Test
    void relistItemRejectsWrongOwner() {
        repository.addItem(
                item(1, OTHER_USER_ID, ItemStatus.UNLISTED));

        assertThrows(
                ValidationException.class,
                () -> service.relistItem(OWNER_ID, 1));
    }

    @Test
    void relistItemRejectsAvailableItem() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.AVAILABLE));

        assertThrows(
                ValidationException.class,
                () -> service.relistItem(OWNER_ID, 1));
    }

    @Test
    void relistItemRejectsRentedItem() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.RENTED));

        assertThrows(
                ValidationException.class,
                () -> service.relistItem(OWNER_ID, 1));
    }

    @Test
    void relistItemRejectsFailedStatusChange() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.UNLISTED));

        repository.statusChangeResult = false;

        assertThrows(
                ValidationException.class,
                () -> service.relistItem(OWNER_ID, 1));
    }

    @Test
    void relistItemHandlesRepositoryException() {
        repository.addItem(
                item(1, OWNER_ID, ItemStatus.UNLISTED));

        repository.statusFailure =
                new RuntimeException("Database unavailable");

        assertThrows(
                RuntimeException.class,
                () -> service.relistItem(OWNER_ID, 1));
    }

    // --------------------------------------------------
    // Get item
    // --------------------------------------------------

    @Test
    void getItemSuccessfully() {
        Item expected =
                item(1, OWNER_ID, ItemStatus.AVAILABLE);

        repository.addItem(expected);

        Item result = service.getItem(1);

        assertEquals(expected, result);
    }

    @Test
    void getItemRejectsInvalidId() {
        assertThrows(
                ValidationException.class,
                () -> service.getItem(0));
    }

    @Test
    void getItemRejectsNegativeId() {
        assertThrows(
                ValidationException.class,
                () -> service.getItem(-1));
    }

    @Test
    void getItemRejectsMissingItem() {
        assertThrows(
                ValidationException.class,
                () -> service.getItem(99));
    }

    @Test
    void getItemRejectsNullRepositoryResult() {
        repository.nullFindResult = true;

        assertThrows(
                IllegalStateException.class,
                () -> service.getItem(1));
    }

    @Test
    void getItemHandlesRepositoryException() {
        repository.findFailure =
                new RuntimeException("Database unavailable");

        assertThrows(
                RuntimeException.class,
                () -> service.getItem(1));
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private Item item(
            long id,
            long ownerId,
            ItemStatus status) {

        return new Item(
                id,
                ownerId,
                "Test item",
                "Test description",
                50.0,
                status);
    }

    /**
     * In-memory test double.
     *
     * It extends the actual repository so ItemService can use it
     * without requiring a real SQLite database.
     */
    private static class FakeItemRepository extends ItemRepository {

        private final Map<Long, Item> data = new HashMap<>();

        private long nextId = 1;

        private int lastLimit;
        private int lastOffset;

        private boolean nullInsertResult;
        private boolean insertWithoutId;
        private boolean nullFindResult;
        private boolean nullOwnerList;
        private boolean nullAvailableList;

        private boolean statusChangeResult = true;

        private RuntimeException insertFailure;
        private RuntimeException findFailure;
        private RuntimeException listFailure;
        private RuntimeException statusFailure;

        FakeItemRepository() {
            super(new DatabaseConnection("target/test-item-service.db"));
        }

        void addItem(Item item) {
            data.put(item.id(), item);
            nextId = Math.max(nextId, item.id() + 1);
        }

        @Override
        public Item insert(Item item) {
            if (insertFailure != null) {
                throw insertFailure;
            }

            if (nullInsertResult) {
                return null;
            }

            if (insertWithoutId) {
                return new Item(
                        null,
                        item.ownerId(),
                        item.name(),
                        item.description(),
                        item.costPerDay(),
                        item.status());
            }

            Item saved = new Item(
                    nextId++,
                    item.ownerId(),
                    item.name(),
                    item.description(),
                    item.costPerDay(),
                    item.status());

            data.put(saved.id(), saved);

            return saved;
        }

        @Override
        public Optional<Item> findById(long id) {
            if (findFailure != null) {
                throw findFailure;
            }

            if (nullFindResult) {
                return null;
            }

            return Optional.ofNullable(data.get(id));
        }

        @Override
        public List<Item> listByOwner(
                long ownerId,
                int limit,
                int offset) {

            if (listFailure != null) {
                throw listFailure;
            }

            if (nullOwnerList) {
                return null;
            }

            lastLimit = limit;
            lastOffset = offset;

            return data.values().stream()
                    .filter(item -> item.ownerId() == ownerId)
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Item> listAvailableNotOwnedBy(
                long userId,
                int limit,
                int offset) {

            if (listFailure != null) {
                throw listFailure;
            }

            if (nullAvailableList) {
                return null;
            }

            lastLimit = limit;
            lastOffset = offset;

            return data.values().stream()
                    .filter(item ->
                            item.status() == ItemStatus.AVAILABLE
                                    && item.ownerId() != userId)
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean changeStatus(
                long id,
                ItemStatus expected,
                ItemStatus next) {

            if (statusFailure != null) {
                throw statusFailure;
            }

            if (!statusChangeResult) {
                return false;
            }

            Item existing = data.get(id);

            if (existing == null || existing.status() != expected) {
                return false;
            }

            Item updated = new Item(
                    existing.id(),
                    existing.ownerId(),
                    existing.name(),
                    existing.description(),
                    existing.costPerDay(),
                    next);

            data.put(id, updated);

            return true;
        }
    }
}
