package tech.kood.rental.domain;

/**
 * Checks Item input before it reaches the service and the database.
 * It throws a ValidationException with a readable message when a rule is broken.
 * Checks that need the database (does the item exist, is it yours) belong to the service.
 */
public final class ItemValidator {

    private ItemValidator() {
        // utility class: nobody should create one
    }

    // The form of a text that gets stored: no padding spaces.
    // The service calls this before saving, so " Drill " is stored as "Drill".
    public static String normalize(String text) {
        return text == null ? null : text.trim();
    }

    // All the rules, used when someone lists an item
    public static void validate(String name, String description, double costPerDay) {
        requireText(name, "name", "item name must not be empty");
        requireText(description, "description", "item description must not be empty");

        // NaN and infinity are not real prices
        if (Double.isNaN(costPerDay) || Double.isInfinite(costPerDay)) {
            throw new ValidationException("Item", "cost", "invalid",
                    "cost per day must be a valid number");
        }
        if (costPerDay < 0) {
            throw new ValidationException("Item", "cost", "negative",
                    "cost per day must not be negative");
        }
    }

    // null, empty and whitespace-only all count as blank
    private static void requireText(String text, String field, String message) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Item", field, "blank", message);
        }
    }
}