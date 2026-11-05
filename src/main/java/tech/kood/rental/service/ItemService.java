package tech.kood.rental.service;

import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.StructuredLogger;
import tech.kood.rental.repository.ItemRepository;

import java.util.List;
import java.util.Optional;

public final class ItemService {

    private static final int PAGE_SIZE = 5;

    private final ItemRepository items;

    public ItemService(ItemRepository items) {
        if (items == null) {
            throw new IllegalArgumentException(
                    "ItemRepository must not be null");
        }

        this.items = items;
        StructuredLogger.info("item.service.initialized");
    }

    /**
     * Lists an item for the authenticated user.
     * The owner ID comes from the logged-in session,
     * not user input.
     */
    public Item listItem(
            long authenticatedUserId,
            String name,
            String description,
            double costPerDay) {

        StructuredLogger.info(
                "item.listing.started | owner_id=" + authenticatedUserId);

        try {
            validateUserId(authenticatedUserId);
            validateRequired(name, "name");
            validateRequired(description, "description");
            validateCost(costPerDay);

            Item item = new Item(
                    null,
                    authenticatedUserId,
                    name.trim(),
                    description.trim(),
                    costPerDay,
                    ItemStatus.AVAILABLE
            );

            // Insert the item and retrieve its generated database ID.
            Item saved = items.insert(item);

            if (saved == null || saved.id() == null) {
                throw new IllegalStateException(
                        "Item could not be saved successfully.");
            }

            StructuredLogger.info(
                    "item.listed | item_id=" + saved.id()
                            + " owner_id=" + authenticatedUserId);

            return saved;

        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "item.listing.failed | owner_id="
                            + authenticatedUserId,
                    exception);
            throw exception;
        }
    }

    /**
     * Returns the authenticated user's own inventory.
     * Defaults to the first page.
     */
    public List<Item> viewMyInventory(long authenticatedUserId) {
        return viewMyInventory(authenticatedUserId, 1);
    }

    /**
     * Returns the authenticated user's inventory
     * using five items per page.
     */
    public List<Item> viewMyInventory(
            long authenticatedUserId,
            int page) {

        StructuredLogger.info(
                "inventory.view.started | user_id=" + authenticatedUserId);

        try {
            validateUserId(authenticatedUserId);
            int offset = validatePage(page);

            List<Item> inventory =
                    items.listByOwner(
                            authenticatedUserId,
                            PAGE_SIZE,
                            offset
                    );

            if (inventory == null) {
                throw new IllegalStateException(
                        "Inventory could not be retrieved.");
            }

            StructuredLogger.info(
                    "inventory.view.succeeded | user_id="
                            + authenticatedUserId
                            + " page=" + page
                            + " item_count=" + inventory.size());

            return inventory;

        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "inventory.view.failed | user_id="
                            + authenticatedUserId,
                    exception);
            throw exception;
        }
    }

    /**
     * Shows available items belonging to other users.
     * Defaults to the first page.
     */
    public List<Item> browseAvailableItems(long authenticatedUserId) {
        return browseAvailableItems(authenticatedUserId, 1);
    }

    /**
     * Browses available items using five items per page.
     * Excludes items owned by the authenticated user.
     */
    public List<Item> browseAvailableItems(
            long authenticatedUserId,
            int page) {

        StructuredLogger.info(
                "listings.browse.started | user_id=" + authenticatedUserId);

        try {
            validateUserId(authenticatedUserId);
            int offset = validatePage(page);

            List<Item> available =
                    items.listAvailableNotOwnedBy(
                            authenticatedUserId,
                            PAGE_SIZE,
                            offset
                    );

            if (available == null) {
                throw new IllegalStateException(
                        "Available items could not be retrieved.");
            }

            StructuredLogger.info(
                    "listings.browsed | user_id=" + authenticatedUserId
                            + " page=" + page
                            + " result_count=" + available.size());

            return available;

        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "listings.browse.failed | user_id="
                            + authenticatedUserId,
                    exception);
            throw exception;
        }
    }

    /**
     * Allows only the owner to delist an AVAILABLE item.
     */
    public Item delistItem(
            long authenticatedUserId,
            long itemId) {

        StructuredLogger.info(
                "item.delisting.started | item_id=" + itemId
                        + " user_id=" + authenticatedUserId);

        try {
            validateUserId(authenticatedUserId);

            Item item = loadItem(itemId);

            requireOwner(item, authenticatedUserId);

            if (item.status() != ItemStatus.AVAILABLE) {
                reject(
                        "status",
                        "INVALID_STATE",
                        "Only an available item can be delisted.");
            }

            // Change status only if the current database status
            // is still AVAILABLE.
            boolean changed = items.changeStatus(
                    itemId,
                    ItemStatus.AVAILABLE,
                    ItemStatus.UNLISTED
            );

            if (!changed) {
                reject(
                        "status",
                        "STATE_CHANGED",
                        "The item status has changed. Please try again.");
            }

            Item saved = loadItem(itemId);

            StructuredLogger.info(
                    "item.delisted | item_id=" + itemId
                            + " owner_id=" + authenticatedUserId);

            return saved;

        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "item.delisting.failed | item_id=" + itemId,
                    exception);
            throw exception;
        }
    }

    /**
     * Allows only the owner to relist an UNLISTED item.
     */
    public Item relistItem(
            long authenticatedUserId,
            long itemId) {

        StructuredLogger.info(
                "item.relisting.started | item_id=" + itemId
                        + " user_id=" + authenticatedUserId);

        try {
            validateUserId(authenticatedUserId);

            Item item = loadItem(itemId);

            requireOwner(item, authenticatedUserId);

            if (item.status() != ItemStatus.UNLISTED) {
                reject(
                        "status",
                        "INVALID_STATE",
                        "Only an unlisted item can be relisted.");
            }

            // Change status only if the current database status
            // is still UNLISTED.
            boolean changed = items.changeStatus(
                    itemId,
                    ItemStatus.UNLISTED,
                    ItemStatus.AVAILABLE
            );

            if (!changed) {
                reject(
                        "status",
                        "STATE_CHANGED",
                        "The item status has changed. Please try again.");
            }

            Item saved = loadItem(itemId);

            StructuredLogger.info(
                    "item.relisted | item_id=" + itemId
                            + " owner_id=" + authenticatedUserId);

            return saved;

        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "item.relisting.failed | item_id=" + itemId,
                    exception);
            throw exception;
        }
    }

    /**
     * Loads an item and reports a readable error if it does not exist.
     */
    public Item getItem(long itemId) {

        StructuredLogger.info(
                "item.lookup.started | item_id=" + itemId);

        try {
            Item item = loadItem(itemId);

            StructuredLogger.info(
                    "item.lookup.succeeded | item_id=" + itemId);

            return item;

        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            StructuredLogger.error(
                    "item.lookup.failed | item_id=" + itemId,
                    exception);
            throw exception;
        }
    }

    /**
     * Checks ownership against the owner_id of the stored item.
     */
    private void requireOwner(
            Item item,
            long authenticatedUserId) {

        if (item.ownerId() != authenticatedUserId) {
            reject(
                    "ownerId",
                    "OWNERSHIP_DENIED",
                    "You are not allowed to manage this item.");
        }
    }

    /**
     * Retrieves an item using its primary key.
     */
    private Item loadItem(long itemId) {

        if (itemId <= 0) {
            reject(
                    "id",
                    "INVALID_ID",
                    "A valid item ID is required.");
        }

        Optional<Item> result = items.findById(itemId);

        if (result == null) {
            throw new IllegalStateException(
                    "Item repository returned an invalid result.");
        }

        return result.orElseThrow(() ->
                new ValidationException(
                        "ITEM",
                        "id",
                        "NOT_FOUND",
                        "The requested item could not be found."
                ));
    }

    /**
     * Validates the requested page and calculates its database offset.
     */
    private int validatePage(int page) {

        if (page < 1) {
            reject(
                    "page",
                    "INVALID_PAGE",
                    "Page number must be at least 1.");
        }

        long offset = (long) (page - 1) * PAGE_SIZE;

        if (offset > Integer.MAX_VALUE) {
            reject(
                    "page",
                    "INVALID_PAGE",
                    "Requested page is too large.");
        }

        return (int) offset;
    }

    private void validateRequired(String value, String field) {

        if (value == null || value.isBlank()) {
            reject(
                    field,
                    "REQUIRED",
                    field + " is required.");
        }
    }

    private void validateCost(double costPerDay) {

        if (!Double.isFinite(costPerDay) || costPerDay < 0) {
            reject(
                    "costPerDay",
                    "INVALID_COST",
                    "Daily cost must be a valid, non-negative amount.");
        }
    }

    private void validateUserId(long userId) {

        if (userId <= 0) {
            reject(
                    "ownerId",
                    "INVALID_ID",
                    "A valid authenticated user is required.");
        }
    }

    private void reject(
            String field,
            String reason,
            String message) {

        StructuredLogger.warn(
                "validation.failed | entity=Item"
                        + " field=" + field
                        + " reason=" + reason);

        throw new ValidationException(
                "ITEM",
                field,
                reason,
                message);
    }
}