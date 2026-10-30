package tech.kood.rental.domain;

/**
 * The three states an item can be in.
 * The names match the status column in schema.sql exactly.
 */
public enum ItemStatus {
    AVAILABLE,   // listed and free, so other users can rent it
    RENTED,      // currently out with a renter
    UNLISTED     // hidden by its owner, so nobody can rent it
}