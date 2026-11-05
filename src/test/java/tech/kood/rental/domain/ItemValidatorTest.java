package tech.kood.rental.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemValidatorTest {

    // Runs the validation and hands back the exception, so each test can inspect it
    private ValidationException error(String name, String description, double cost) {
        return assertThrows(ValidationException.class,
                () -> ItemValidator.validate(name, description, cost));
    }

    @Test
    void goodItemIsAccepted() {
        assertDoesNotThrow(() -> ItemValidator.validate("Drill", "Cordless drill", 4));
    }

    @Test
    void blankNameIsRejected() {
        // null, empty and whitespace-only all count as blank
        for (String blank : new String[]{null, "", "   "}) {
            ValidationException e = error(blank, "Cordless drill", 4);

            assertEquals("Item", e.getEntity());
            assertEquals("name", e.getField());
            assertEquals("blank", e.getReason());
            assertEquals("item name must not be empty", e.getMessage());
        }
    }

    @Test
    void blankDescriptionIsRejected() {
        for (String blank : new String[]{null, "", "   "}) {
            ValidationException e = error("Drill", blank, 4);

            assertEquals("description", e.getField());
            assertEquals("blank", e.getReason());
            assertEquals("item description must not be empty", e.getMessage());
        }
    }

    @Test
    void negativeCostIsRejected() {
        ValidationException e = error("Drill", "Cordless drill", -1);

        // the same entity, field and reason as the brief's log example
        assertEquals("Item", e.getEntity());
        assertEquals("cost", e.getField());
        assertEquals("negative", e.getReason());
        assertEquals("cost per day must not be negative", e.getMessage());
    }

    @Test
    void freeItemIsAccepted() {
        assertDoesNotThrow(() -> ItemValidator.validate("Drill", "Cordless drill", 0));
    }

    @Test
    void notARealNumberIsRejected() {
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY}) {
            ValidationException e = error("Drill", "Cordless drill", bad);

            assertEquals("cost", e.getField());
            assertEquals("invalid", e.getReason());
        }
    }

    @Test
    void nameIsCheckedBeforeCost() {
        // when two things are wrong, the first rule in the list is the one reported
        ValidationException e = error("", "Cordless drill", -1);

        assertEquals("name", e.getField());
    }

    @Test
    void normalizeRemovesPaddingSpaces() {
        assertEquals("Drill", ItemValidator.normalize("  Drill  "));
    }

    @Test
    void normalizeLeavesNullAlone() {
        assertNull(ItemValidator.normalize(null));
    }
}