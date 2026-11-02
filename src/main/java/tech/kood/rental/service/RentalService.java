package tech.kood.rental.service;

import tech.kood.rental.domain.ValidationException;
import tech.kood.rental.infrastructure.StructuredLogger;

public class RentalService {

    public RentalService() {
    }

    public void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            StructuredLogger.warn("validation.failed | entity=User field=username reason=blank_value");
            throw new ValidationException(
                    "User",
                    "username",
                    "blank_value",
                    "Username cannot be blank."
            );
        }
    }

    public void validateRentalDuration(int days) {
        if (days <= 0) {
            StructuredLogger.warn("validation.failed | entity=Rental field=durationDays reason=invalid_duration");
            throw new ValidationException(
                    "Rental",
                    "durationDays",
                    "invalid_duration",
                    "Rental duration must be greater than zero."
                );
        }
    }

    public void validateItemCost(double cost) {
        if (!Double.isFinite(cost) || cost < 0) {
            StructuredLogger.warn("validation.failed | entity=Item field=cost reason=invalid_value");
            throw new ValidationException(
                    "Item", 
                    "cost", 
                    "invalid_value", 
                    "Item cost cannot be negative or invalid."
            );
        }
    }

    public void validateNotSelfRental(long ownerId, long renterId) {
        if (ownerId == renterId) {
            StructuredLogger.warn("validation.failed | entity=Rental field=renterId reason=self_rental_denied");
            throw new ValidationException(
                    "Rental",
                    "renterId",
                    "self_rental_denied",
                    "You cannot rent your own item."
            );
        }
    }

    public void validateOwnership(long ownerId, long currentUserId) {
        if (ownerId != currentUserId) {
            StructuredLogger.warn("validation.failed | entity=Item field=ownerId reason=unauthorized_management");
            throw new ValidationException(
                    "Item",
                    "ownerId",
                    "unauthorized_management",
                    "You can only manage items that you own."
            );
        }
    }

    public void validateItemAvailable(boolean available) {
        if (!available) {
            StructuredLogger.warn("validation.failed | entity=Item field=status reason=item_unavailable");
            throw new ValidationException(
                    "Item",
                    "status",
                    "item_unavailable",
                    "This item is not available for rental."
            );
        }
    }

    public void validateUsernameAvailable(String username, boolean usernameAlreadyExists) {
        validateUsername(username);

        if (usernameAlreadyExists) {
            StructuredLogger.warn("validation.failed | entity=User field=username reason=duplicate_username");
            throw new ValidationException(
                    "User",
                    "username",
                    "duplicate_username",
                    "That username is already taken."
            );
        }
    }
}
