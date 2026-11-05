package tech.kood.rental.domain;

/**
 * Checks Rental input before it reaches the service and the database.
 * It throws a ValidationException with a readable message when a rule is broken.
 * Checks that need the database (does the item exist, is it your own item) belong to the service.
 */
public final class RentalValidator {

    private static final int MAX_DAYS = 365;

    private RentalValidator() {
        // utility class: nobody should create one
    }

    // All the rules, used when someone rents an item
    public static void validate(long itemId, long renterId, int days) {
        if (itemId <= 0) {
            throw new ValidationException("Rental", "item", "invalid",
                    "a rental must refer to an item");
        }
        if (renterId <= 0) {
            throw new ValidationException("Rental", "renter", "invalid",
                    "a rental must have a renter");
        }
        if (days < 1) {
            throw new ValidationException("Rental", "days", "too_small",
                    "rental duration must be at least 1 day");
        }
        if (days > MAX_DAYS) {
            throw new ValidationException("Rental", "days", "too_large",
                    "rental duration must not exceed " + MAX_DAYS + " days");
        }
    }
}