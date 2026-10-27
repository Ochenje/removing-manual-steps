package tech.kood.rental.domain;

import java.time.LocalDateTime;

/**
 * One row of the users table.
 * A record is a short way to write a plain data holder in Java:
 * it creates the constructor and the getters for us.
 */
public record User(
        Long id,                    // null until the database assigns it
        String username,            // unique, chosen at registration
        String passwordHash,        // the bcrypt hash, never the plain password
        LocalDateTime createdAt     // when the account was made
) {

    // By default a record prints every field, including the hash.
    // We override it so the hash can never leak into a log line.
    @Override
    public String toString() {
        return "User[id=" + id + ", username=" + username + "]";
    }
}