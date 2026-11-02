package tech.kood.rental.infrastructure;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes application events in the required format:
 * TIMESTAMP | LEVEL | EVENT
 */
public final class StructuredLogger {

    private static final Logger LOGGER =
            Logger.getLogger("tech.kood.rental");

    private StructuredLogger() {
        // Utility class; do not instantiate.
    }

    public static void info(String event) {
        write("INFO", Level.INFO, event, null);
    }

    public static void warn(String event) {
        write("WARN", Level.WARNING, event, null);
    }

    public static void error(String event) {
        write("ERROR", Level.SEVERE, event, null);
    }

    public static void error(String event, Throwable exception) {
        write("ERROR", Level.SEVERE, event, exception);
    }

    private static void write(
            String label,
            Level javaLevel,
            String event,
            Throwable exception) {

        String safeEvent = sanitize(event);
        String timestamp = Instant.now().toString();

        String message = timestamp + " | "
                + label + " | "
                + safeEvent;

        if (exception == null) {
            LOGGER.log(javaLevel, message);
        } else {
            LOGGER.log(javaLevel, message, exception);
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "UNSPECIFIED_EVENT";
        }

        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('|', '/')
                .trim();
    }
}
