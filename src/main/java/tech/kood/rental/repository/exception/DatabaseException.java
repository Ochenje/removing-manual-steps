package tech.kood.rental.repository.exception;

/**
 * Thrown when the database fails for any reason other than a taken username.
 * The message is safe to show to the user and never contains SQLite wording.
 * The operation name and the original error are kept for the log.
 */
public class DatabaseException extends RuntimeException {

    private final String operation;   // what was being done, e.g. "insert user"

    public DatabaseException(String operation, Throwable cause) {
        super("Something went wrong while saving or loading data. Please try again.", cause);
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }
}