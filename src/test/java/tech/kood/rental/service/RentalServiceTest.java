
package tech.kood.rental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RentalServiceTest {

    private RentalService service;

    @BeforeEach
    void setUp() {
        service = new RentalService();
    }

    @Test
    void acceptsValidUsername() {
        assertDoesNotThrow(
                () -> service.validateUsername("tabitha")
        );
    }

    @Test
    void rejectsNullUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateUsername(null)
        );
    }

    @Test
    void rejectsBlankUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateUsername("   ")
        );
    }

    @Test
    void acceptsPositiveRentalDuration() {
        assertDoesNotThrow(
                () -> service.validateRentalDuration(1)
        );
    }

    @Test
    void rejectsZeroRentalDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateRentalDuration(0)
        );
    }

    @Test
    void rejectsNegativeRentalDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateRentalDuration(-1)
        );
    }

    @Test
    void acceptsZeroItemCost() {
        assertDoesNotThrow(
                () -> service.validateItemCost(0)
        );
    }

    @Test
    void rejectsNegativeItemCost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateItemCost(-1)
        );
    }

    @Test
    void rejectsInfiniteItemCost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateItemCost(
                        Double.POSITIVE_INFINITY
                )
        );
    }

    @Test
    void rejectsNaNItemCost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateItemCost(Double.NaN)
        );
    }

    @Test
    void rejectsSelfRental() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateNotSelfRental(1, 1)
        );
    }

    @Test
    void allowsRentalFromAnotherOwner() {
        assertDoesNotThrow(
                () -> service.validateNotSelfRental(1, 2)
        );
    }

    @Test
    void rejectsManagingAnotherUsersItem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateOwnership(1, 2)
        );
    }

    @Test
    void allowsOwnerToManageOwnItem() {
        assertDoesNotThrow(
                () -> service.validateOwnership(1, 1)
        );
    }

    @Test
    void rejectsUnavailableItem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateItemAvailable(false)
        );
    }

    @Test
    void allowsAvailableItem() {
        assertDoesNotThrow(
                () -> service.validateItemAvailable(true)
        );
    }

    @Test
    void rejectsDuplicateUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateUsernameAvailable(
                        "tabitha", true
                )
        );
    }

    @Test
    void acceptsAvailableUsername() {
        assertDoesNotThrow(
                () -> service.validateUsernameAvailable(
                        "tabitha", false
                )
        );
    }

    @Test
    void rejectsBlankUsernameBeforeDuplicateCheck() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateUsernameAvailable(
                        "   ", false
                )
        );
    }
}
