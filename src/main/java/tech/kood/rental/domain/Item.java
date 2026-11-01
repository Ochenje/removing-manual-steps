package tech.kood.rental.domain;

/**
 * One row of the items table.
 * A record is a short way to write a plain data holder in Java:
 * it creates the constructor and the getters for us.
 */
public record Item(
        Long id,               // null until the database assigns it
        long ownerId,          // the id of the user who listed it
        String name,
        String description,
        double costPerDay,     // REAL in schema.sql, and a double in the validation code
        ItemStatus status      // AVAILABLE, RENTED or UNLISTED
) {
}