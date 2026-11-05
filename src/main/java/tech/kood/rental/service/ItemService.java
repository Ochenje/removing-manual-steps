package tech.kood.rental.service;

import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.StructuredLogger;

import java.util.List;
import java.util.Optional;

public final class ItemService {

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
     * The owner ID comes from the logged-in session, not user input.
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

            // TODO(repository): insert the item and return its generated ID.
            Item saved = items.save(item);

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
     */
    public List<Item> viewMyInventory(long authenticatedUserId) {
        StructuredLogger.info(
                "inventory.view.started | user_id=" + authenticatedUserId);

        try {
            validateUserId(authenticatedUserId);

            // TODO(repository): query items by their stored owner_id.
            List<Item> inventory =
                    items.findByOwnerId(authenticatedUserId);

            StructuredLogger.info(
                    "inventory.view.succeeded | user_id="
                            + authenticatedUserId
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
     */
    public List<Item> browseAvailableItems(long authenticatedUserId) {
        StructuredLogger.info(
                "listings.browse.started | user_id=" + authenticatedUserId);

        try {
            validateUserId(authenticatedUserId);

            // TODO(repository): query AVAILABLE items where owner_id differs
            // from the authenticated user ID.
            List<Item> available =
                    items.findAvailableNotOwnedBy(authenticatedUserId);

            StructuredLogger.info(
                    "listings.browsed | user_id=" + authenticatedUserId
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
                reject("status", "INVALID_STATE",
                        "Only an available item can be delisted.");
            }

            Item updated = withStatus(item, ItemStatus.UNLISTED);

            // TODO(repository): persist status change conditionally,
            // requiring the stored status to still be AVAILABLE.
            Item saved = items.save(updated);

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
                reject("status", "INVALID_STATE",
                        "Only an unlisted item can be relisted.");
            }

            Item updated = withStatus(item, ItemStatus.AVAILABLE);

            // TODO(repository): persist status change conditionally,
            // requiring the stored status to still be UNLISTED.
            Item saved = items.save(updated);

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
            reject("ownerId", "OWNERSHIP_DENIED",
                    "You are not allowed to manage this item.");
        }
    }

    private Item loadItem(long itemId) {
        if (itemId <= 0) {
            reject("id", "INVALID_ID",
                    "A valid item ID is required.");
        }

        // TODO(repository): fetch the item by primary key.
        Optional<Item> result = items.findById(itemId);

        return result.orElseThrow(() ->
                new ValidationException(
                        "ITEM",
                        "id",
                        "NOT_FOUND",
                        "The requested item could not be found."
                ));
    }

    private Item withStatus(Item item, ItemStatus status) {
        return new Item(
                item.id(),
                item.ownerId(),
                item.name(),
                item.description(),
                item.costPerDay(),
                status
        );
    }

    private void validateRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            reject(field, "REQUIRED",
                    field + " is required.");
        }
    }

    private void validateCost(double costPerDay) {
        if (!Double.isFinite(costPerDay) || costPerDay < 0) {
            reject("costPerDay", "INVALID_COST",
                    "Daily cost must be a valid, non-negative amount.");
        }
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            reject("ownerId", "INVALID_ID",
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
                "ITEM", field, reason, message);
    }

    /*
     * TODO(repository): replace this contract with the existing
     * repository interface when it is available.
     */
    public interface ItemRepository {

        Optional<Item> findById(long itemId);

        Item save(Item item);

        List<Item> findByOwnerId(long ownerId);

        List<Item> findAvailableNotOwnedBy(long userId);
    }
}
