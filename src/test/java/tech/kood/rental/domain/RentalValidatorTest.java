package tech.kood.rental.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RentalValidatorTest {

    // Runs the validation and hands back the exception, so each test can inspect it
    private ValidationException error(long itemId, long renterId, int days) {
        return assertThrows(ValidationException.class,
                () -> RentalValidator.validate(itemId, renterId, days));
    }

    @Test
    void goodRentalIsAccepted() {
        assertDoesNotThrow(() -> RentalValidator.validate(7, 3, 5));
    }

    @Test
    void oneDayIsTheShortestAllowedRental() {
        assertDoesNotThrow(() -> RentalValidator.validate(7, 3, 1));
    }

    @Test
    void zeroDaysIsRejectedWithTheMessageFromTheBrief() {
        ValidationException e = error(7, 3, 0);

        assertEquals("Rental", e.getEntity());
        assertEquals("days", e.getField());
        assertEquals("too_small", e.getReason());
        assertEquals("rental duration must be at least 1 day", e.getMessage());
    }

    @Test
    void negativeDaysAreRejected() {
        ValidationException e = error(7, 3, -2);

        assertEquals("days", e.getField());
        assertEquals("rental duration must be at least 1 day", e.getMessage());
    }

    @Test
    void missingItemIsRejected() {
        ValidationException e = error(0, 3, 5);

        assertEquals("item", e.getField());
        assertEquals("invalid", e.getReason());
        assertEquals("a rental must refer to an item", e.getMessage());
    }

    @Test
    void missingRenterIsRejected() {
        ValidationException e = error(7, 0, 5);

        assertEquals("renter", e.getField());
        assertEquals("a rental must have a renter", e.getMessage());
    }

    @Test
    void firstProblemInTheListIsTheOneReported() {
        // everything is wrong, so the first rule (the item) is the one reported
        ValidationException e = error(0, 0, 0);

        assertEquals("item", e.getField());
    }
}