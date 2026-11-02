package tech.kood.rental.domain;

/**
 * Thrown when an input is rejected before it reaches the database.
 * The message is safe to show to the user.
 * The entity, field and reason are for the log.
 * RuntimeException means callers are not forced to declare it everywhere.
 */
public class ValidationException extends RuntimeException {

    private final String entity;   // which object was checked, e.g. "User"
    private final String field;    // which field was wrong, e.g. "username"
    private final String reason;   // short code for the log, e.g. "blank"

    public ValidationException(String entity, String field, String reason, String userMessage) {
        super(userMessage);        // the readable sentence the user will see
        this.entity = entity;
        this.field = field;
        this.reason = reason;
    }

    public String getEntity() {
        return entity;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}