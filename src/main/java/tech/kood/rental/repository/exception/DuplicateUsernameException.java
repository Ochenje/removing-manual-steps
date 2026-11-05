package tech.kood.rental.repository.exception;

/**
 * Thrown when a username is already taken.
 * The message is safe to show to the user.
 * The original database error is kept as the cause, for the log.
 */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(Throwable cause) {
        super("That username is already taken.", cause);
    }
}