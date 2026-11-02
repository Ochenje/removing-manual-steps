package tech.kood.rental.service;

import tech.kood.rental.infrastructure.StructuredLogger;

public class RentalService {

    public RentalService() {
    }

    public void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            StructuredLogger.warn("USERNAME_VALIDATION_REJECTED");
            throw new IllegalArgumentException(
                    "Username cannot be blank."
            );
        }
    }

    public void validateRentalDuration(int days) {
        if (days <= 0) {
            StructuredLogger.warn("RENTAL_DURATION_REJECTED");
            throw new IllegalArgumentException(
                    "Rental duration must be greater than zero."
            );
        }
    }

    public void validateItemCost(double cost) {
        if (!Double.isFinite(cost) || cost < 0) {
            StructuredLogger.warn("ITEM_COST_REJECTED");
            throw new IllegalArgumentException(
                    "Item cost cannot be negative or invalid."
            );
        }
    }

    public void validateNotSelfRental(
            long ownerId,
            long renterId
    ) {
        if (ownerId == renterId) {
            StructuredLogger.warn("SELF_RENTAL_REJECTED");
            throw new IllegalArgumentException(
                    "You cannot rent your own item."
            );
        }
    }

    public void validateOwnership(
            long ownerId,
            long currentUserId
    ) {
        if (ownerId != currentUserId) {
            StructuredLogger.warn("OWNERSHIP_VALIDATION_REJECTED");
            throw new IllegalArgumentException(
                    "You can only manage items that you own."
            );
        }
    }

    public void validateItemAvailable(boolean available) {
        if (!available) {
            StructuredLogger.warn("ITEM_UNAVAILABLE");
            throw new IllegalArgumentException(
                    "This item is not available for rental."
            );
        }
    }

    public void validateUsernameAvailable(
            String username,
            boolean usernameAlreadyExists
    ) {
        validateUsername(username);

        if (usernameAlreadyExists) {
            StructuredLogger.warn("DUPLICATE_USERNAME_REJECTED");
            throw new IllegalArgumentException(
                    "That username is already taken."
            );
        }
    }
}
