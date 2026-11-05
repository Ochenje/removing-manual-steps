package tech.kood.rental.service;

import tech.kood.rental.domain.Item;
import tech.kood.rental.domain.ItemStatus;
import tech.kood.rental.domain.Rental;
import tech.kood.rental.domain.RentalValidator;
import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.DatabaseConnection;
import tech.kood.rental.infrastructure.StructuredLogger;
import tech.kood.rental.repository.ItemRepository;
import tech.kood.rental.repository.RentalRepository;
import tech.kood.rental.repository.exception.DatabaseException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class RentalService {

    private final DatabaseConnection database;
    private final ItemRepository items;
    private final RentalRepository rentals;

    /**
     * Keeps the standalone validation helpers available to their callers.
     */
    public RentalService() {
        this.database = null;
        this.items = null;
        this.rentals = null;
    }

    public RentalService(
            DatabaseConnection database,
            ItemRepository items,
            RentalRepository rentals) {
        if (database == null || items == null || rentals == null) {
            throw new IllegalArgumentException(
                    "Database and repositories must not be null");
        }
        this.database = database;
        this.items = items;
        this.rentals = rentals;
    }

    /**
     * Rents an available item from another user and records both changes atomically.
     */
    public Rental rentItem(long renterId, long itemId, int days) {
        requireConfigured();
        try {
            RentalValidator.validate(itemId, renterId, days);
        } catch (ValidationException exception) {
            StructuredLogger.warn(
                    "rental.validation.rejected | field=" + exception.getField()
                            + " | reason=" + exception.getReason());
            throw exception;
        }

        StructuredLogger.info(
                "rental.creation.started",
                Map.of("item_id", itemId, "renter_id", renterId));

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Item item = items.findById(connection, itemId)
                        .orElseThrow(() -> rejection(
                                "item", "NOT_FOUND",
                                "The requested item could not be found."));

                validateNotSelfRental(item.ownerId(), renterId);
                validateItemAvailable(item.status() == ItemStatus.AVAILABLE);

                if (!items.changeStatus(
                        connection,
                        itemId,
                        ItemStatus.AVAILABLE,
                        ItemStatus.RENTED)) {
                    throw rejection(
                            "status", "STATE_CHANGED",
                            "This item is no longer available. Please refresh the listings.");
                }

                Rental saved = rentals.insert(
                        connection,
                        new Rental(null, itemId, renterId, days, null, null));
                connection.commit();

                StructuredLogger.info(
                        "rental.created",
                        Map.of(
                                "rental_id", saved.id(),
                                "item_id", itemId,
                                "renter_id", renterId));
                return saved;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (ValidationException exception) {
            throw exception;
        } catch (SQLException exception) {
            DatabaseException failure =
                    new DatabaseException("create rental", exception);
            StructuredLogger.error(
                    "db.query",
                    Map.of("op", "rental.create"),
                    failure);
            throw failure;
        } catch (DatabaseException exception) {
            StructuredLogger.error(
                    "db.query",
                    Map.of("op", "rental.create"),
                    exception);
            throw exception;
        }
    }

    /**
     * Returns the active rental details only to the owner of the rented item.
     */
    public Rental getActiveRentalForOwner(long ownerId, long itemId) {
        requireConfigured();
        validateUserId(ownerId);
        validateItemId(itemId);

        try (Connection connection = database.getConnection()) {
            Item item = items.findById(connection, itemId)
                    .orElseThrow(() -> rejection(
                            "item", "NOT_FOUND",
                            "The requested item could not be found."));
            validateOwnership(item.ownerId(), ownerId);
            if (item.status() != ItemStatus.RENTED) {
                throw rejection(
                        "status", "INVALID_STATE",
                        "This item does not have an active rental.");
            }
            return rentals.findActiveByItemId(connection, itemId)
                    .orElseThrow(() -> rejection(
                            "rental", "NOT_FOUND",
                            "No active rental was found for this item."));
        } catch (ValidationException exception) {
            throw exception;
        } catch (SQLException exception) {
            DatabaseException failure =
                    new DatabaseException("read active rental details", exception);
            StructuredLogger.error(
                    "db.query",
                    Map.of("op", "rental.details.read"),
                    failure);
            throw failure;
        } catch (DatabaseException exception) {
            StructuredLogger.error(
                    "db.query",
                    Map.of("op", "rental.details.read"),
                    exception);
            throw exception;
        }
    }

    /**
     * Confirms an active return only when called by the item's owner.
     */
    public Rental confirmReturn(long ownerId, long itemId) {
        requireConfigured();
        validateUserId(ownerId);
        validateItemId(itemId);

        StructuredLogger.info(
                "rental.return.started",
                Map.of("item_id", itemId, "owner_id", ownerId));

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Item item = items.findById(connection, itemId)
                        .orElseThrow(() -> rejection(
                                "item", "NOT_FOUND",
                                "The requested item could not be found."));

                validateOwnership(item.ownerId(), ownerId);
                if (item.status() != ItemStatus.RENTED) {
                    throw rejection(
                            "status", "INVALID_STATE",
                            "This item does not have an active rental.");
                }

                Rental activeRental =
                        rentals.findActiveByItemId(connection, itemId)
                                .orElseThrow(() -> rejection(
                                        "rental", "NOT_FOUND",
                                        "No active rental was found for this item."));

                if (!rentals.confirmReturn(connection, activeRental.id())) {
                    throw rejection(
                            "rental", "STATE_CHANGED",
                            "This rental has already been returned.");
                }
                if (!items.changeStatus(
                        connection,
                        itemId,
                        ItemStatus.RENTED,
                        ItemStatus.AVAILABLE)) {
                    throw rejection(
                            "status", "STATE_CHANGED",
                            "The item status has changed. Please refresh and try again.");
                }

                Rental returned = rentals.findById(connection, activeRental.id())
                        .orElseThrow(() -> new SQLException(
                                "The returned rental could not be read back."));
                connection.commit();

                StructuredLogger.info(
                        "rental.return.confirmed",
                        Map.of(
                                "rental_id", returned.id(),
                                "item_id", itemId,
                                "owner_id", ownerId));
                return returned;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (ValidationException exception) {
            throw exception;
        } catch (SQLException exception) {
            DatabaseException failure =
                    new DatabaseException("confirm rental return", exception);
            StructuredLogger.error(
                    "db.query",
                    Map.of("op", "rental.return.confirm"),
                    failure);
            throw failure;
        } catch (DatabaseException exception) {
            StructuredLogger.error(
                    "db.query",
                    Map.of("op", "rental.return.confirm"),
                    exception);
            throw exception;
        }
    }

    private void requireConfigured() {
        if (database == null || items == null || rentals == null) {
            throw new IllegalStateException(
                    "Rental operations require a database and repositories.");
        }
    }

    private static void rollback(
            Connection connection,
            Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            StructuredLogger.error(
                    "db.transaction.rollback.failed", rollbackFailure);
        }
    }

    private static ValidationException rejection(
            String field,
            String reason,
            String message) {
        StructuredLogger.warn(
                "validation.failed | entity=Rental"
                        + " field=" + field
                        + " reason=" + reason);
        return new ValidationException(
                "Rental", field, reason, message);
    }

    private static void validateUserId(long userId) {
        if (userId <= 0) {
            throw rejection(
                    "ownerId", "INVALID_ID",
                    "A valid authenticated user is required.");
        }
    }

    private static void validateItemId(long itemId) {
        if (itemId <= 0) {
            throw rejection(
                    "item", "INVALID_ID",
                    "A valid item ID is required.");
        }
    }

    public void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw rejection(
                    "username", "BLANK_VALUE",
                    "Username cannot be blank.");
        }

        if (username.length() < 3 || username.length() > 30) {
            throw rejection(
                    "username", "INVALID_LENGTH",
                    "Username must be between 3 and 30 characters.");
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw rejection(
                    "username", "INVALID_FORMAT",
                    "Username can only contain letters, numbers, and underscores.");
        }

        StructuredLogger.info(
                "validation.success | entity=User field=username");
    }

    public void validateRentalDuration(int days) {
        RentalValidator.validate(1, 1, days);

        StructuredLogger.info(
                "validation.success | entity=Rental field=durationDays");
    }

    public void validateItemCost(double cost) {
        if (!Double.isFinite(cost) || cost < 0) {
            throw rejection(
                    "cost", "INVALID_VALUE",
                    "Item cost must be a finite, non-negative value.");
        }

        StructuredLogger.info(
                "validation.success | entity=Item field=cost");
    }

    public void validateNotSelfRental(long ownerId, long renterId) {
        if (ownerId <= 0 || renterId <= 0) {
            throw rejection(
                    "userId", "INVALID_ID",
                    "Owner and renter IDs must be valid positive numbers.");
        }

        if (ownerId == renterId) {
            throw rejection(
                    "renterId", "SELF_RENTAL_DENIED",
                    "You cannot rent your own item.");
        }

        StructuredLogger.info(
                "validation.success | entity=Rental field=renterId");
    }

    public void validateOwnership(long ownerId, long currentUserId) {
        if (ownerId <= 0 || currentUserId <= 0) {
            throw rejection(
                    "ownerId", "INVALID_ID",
                    "Owner and current user IDs must be valid positive numbers.");
        }

        if (ownerId != currentUserId) {
            throw rejection(
                    "ownerId", "UNAUTHORIZED_MANAGEMENT",
                    "You can only manage items that you own.");
        }

        StructuredLogger.info(
                "validation.success | entity=Item field=ownerId");
    }

    public void validateItemAvailable(boolean available) {
        if (!available) {
            throw rejection(
                    "status", "ITEM_UNAVAILABLE",
                    "This item is not available for rental.");
        }

        StructuredLogger.info(
                "validation.success | entity=Item field=status");
    }

    public void validateUsernameAvailable(
            String username,
            boolean usernameAlreadyExists) {
        validateUsername(username);

        if (usernameAlreadyExists) {
            throw rejection(
                    "username", "DUPLICATE_USERNAME",
                    "That username is already taken.");
        }

        StructuredLogger.info(
                "validation.success | entity=User field=username_availability");
    }
}
