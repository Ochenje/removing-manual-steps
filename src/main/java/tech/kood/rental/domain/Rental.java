package tech.kood.rental.domain;

import java.time.LocalDateTime;

/**
 * One row of the rentals table.
 * A record is a short way to write a plain data holder in Java:
 * it creates the constructor and the getters for us.
 */
public record Rental(
        Long id,                      // null until the database assigns it
        long itemId,                  // which item was borrowed
        long renterId,                // the id of the user who borrowed it
        int days,                     // how many days, at least 1
        LocalDateTime rentedAt,       // when the rental was made
        LocalDateTime returnedAt      // null until the owner confirms the return
) {
}