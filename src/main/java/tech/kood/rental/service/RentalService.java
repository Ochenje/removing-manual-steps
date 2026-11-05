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
                    "User", "username", "blank_value",
                    "Username cannot be blank."
            );
        }

        if (username.length() < 3 || username.length() > 30) {
            StructuredLogger.warn("validation.failed | entity=User field=username reason=invalid_length");
            throw new ValidationException(
                    "User", "username", "invalid_length",
                    "Username must be between 3 and 30 characters."
            );
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            StructuredLogger.warn("validation.failed | entity=User field=username reason=invalid_format");
            throw new ValidationException(
                    "User", "username", "invalid_format",
                    "Username can only contain letters, numbers, and underscores."
            );
        }

        StructuredLogger.info("validation.success | entity=User field=username");
    }

    public void validateRentalDuration(int days) {
        if (days <= 0 || days > 365) {
            StructuredLogger.warn("validation.failed | entity=Rental field=durationDays reason=invalid_duration");
            throw new ValidationException(
                    "Rental", "durationDays", "invalid_duration",
                    "Rental duration must be between 1 and 365 days."
            );
        }

        StructuredLogger.info("validation.success | entity=Rental field=durationDays");
    }

    public void validateItemCost(double cost) {
        if (!Double.isFinite(cost) || cost < 0) {
            StructuredLogger.warn("validation.failed | entity=Item field=cost reason=invalid_value");
            throw new ValidationException(
                    "Item", "cost", "invalid_value",
                    "Item cost must be a finite, non-negative value."
            );
        }

        StructuredLogger.info("validation.success | entity=Item field=cost");
    }

    public void validateNotSelfRental(long ownerId, long renterId) {
        if (ownerId <= 0 || renterId <= 0) {
            StructuredLogger.warn("validation.failed | entity=Rental field=userId reason=invalid_id");
            throw new ValidationException(
                    "Rental", "userId", "invalid_id",
                    "Owner and renter IDs must be valid positive numbers."
            );
        }

        if (ownerId == renterId) {
            StructuredLogger.warn("validation.failed | entity=Rental field=renterId reason=self_rental_denied");
            throw new ValidationException(
                    "Rental", "renterId", "self_rental_denied",
                    "You cannot rent your own item."
            );
        }

        StructuredLogger.info("validation.success | entity=Rental field=renterId");
    }

    public void validateOwnership(long ownerId, long currentUserId) {
        if (ownerId <= 0 || currentUserId <= 0) {
            StructuredLogger.warn("validation.failed | entity=Item field=ownerId reason=invalid_id");
            throw new ValidationException(
                    "Item", "ownerId", "invalid_id",
                    "Owner and current user IDs must be valid positive numbers."
            );
        }

        if (ownerId != currentUserId) {
            StructuredLogger.warn("validation.failed | entity=Item field=ownerId reason=unauthorized_management");
            throw new ValidationException(
                    "Item", "ownerId", "unauthorized_management",
                    "You can only manage items that you own."
            );
        }

        StructuredLogger.info("validation.success | entity=Item field=ownerId");
    }

    public void validateItemAvailable(boolean available) {
        if (!available) {
            StructuredLogger.warn("validation.failed | entity=Item field=status reason=item_unavailable");
            throw new ValidationException(
                    "Item", "status", "item_unavailable",
                    "This item is not available for rental."
            );
        }

        StructuredLogger.info("validation.success | entity=Item field=status");
    }

    public void validateUsernameAvailable(String username, boolean usernameAlreadyExists) {
        validateUsername(username);

        if (usernameAlreadyExists) {
            StructuredLogger.warn("validation.failed | entity=User field=username reason=duplicate_username");
            throw new ValidationException(
                    "User", "username", "duplicate_username",
                    "That username is already taken."
            );
        }

        StructuredLogger.info("validation.success | entity=User field=username_availability");
    }
}