package tech.kood.rental.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.kood.rental.domain.ValidationException;

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
                ValidationException.class,
                () -> service.validateUsername(null)
        );
    }

    @Test
    void rejectsBlankUsername() {
        assertThrows(
                ValidationException.class,
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
                ValidationException.class,
                () -> service.validateRentalDuration(0)
        );
    }

    @Test
    void rejectsNegativeRentalDuration() {
        assertThrows(
                ValidationException.class,
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
                ValidationException.class,
                () -> service.validateItemCost(-1)
        );
    }

    @Test
    void rejectsInfiniteItemCost() {
        assertThrows(
                ValidationException.class,
                () -> service.validateItemCost(
                        Double.POSITIVE_INFINITY
                )
        );
    }

    @Test
    void rejectsNaNItemCost() {
        assertThrows(
                ValidationException.class,
                () -> service.validateItemCost(Double.NaN)
        );
    }

    @Test
    void rejectsSelfRental() {
        assertThrows(
                ValidationException.class,
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
                ValidationException.class,
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
                ValidationException.class,
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
                ValidationException.class,
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
                ValidationException.class,
                () -> service.validateUsernameAvailable(
                        "   ", false
                )
        );
    }
    
@Test
void rejectsUsernameShorterThanThreeCharacters() {
    assertThrows(
            ValidationException.class,
            () -> service.validateUsername("ab")
    );
}

@Test
void rejectsUsernameLongerThanThirtyCharacters() {
    assertThrows(
            ValidationException.class,
            () -> service.validateUsername("a".repeat(31))
    );
}

@Test
void rejectsUsernameWithInvalidCharacters() {
    assertThrows(
            ValidationException.class,
            () -> service.validateUsername("user@123")
    );
}

@Test
void rejectsRentalDurationGreaterThan365Days() {
    assertThrows(
            ValidationException.class,
            () -> service.validateRentalDuration(366)
    );
}

@Test
void rejectsInvalidOwnerIdForSelfRental() {
    assertThrows(
            ValidationException.class,
            () -> service.validateNotSelfRental(0, 2)
    );
}

@Test
void rejectsInvalidRenterIdForSelfRental() {
    assertThrows(
            ValidationException.class,
            () -> service.validateNotSelfRental(1, 0)
    );
}

@Test
void rejectsInvalidOwnerIdForOwnership() {
    assertThrows(
            ValidationException.class,
            () -> service.validateOwnership(0, 1)
    );
}

@Test
void rejectsInvalidCurrentUserIdForOwnership() {
    assertThrows(
            ValidationException.class,
            () -> service.validateOwnership(1, 0)
    );
}

    @Test
    void refusesRentalOperationWhenNotConfigured() {
        assertThrows(
                IllegalStateException.class,
                () -> service.rentItem(1, 1, 1)
        );
    }

    @Test
    void refusesActiveRentalLookupWhenNotConfigured() {
        assertThrows(
                IllegalStateException.class,
                () -> service.getActiveRentalForOwner(1, 1)
        );
    }

    @Test
    void refusesReturnConfirmationWhenNotConfigured() {
        assertThrows(
                IllegalStateException.class,
                () -> service.confirmReturn(1, 1)
        );
    }
}
