package tech.kood.rental.infrastructure;

import java.time.Instant;
import java.util.Map;
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
        write("INFO", Level.INFO, event, Map.of(), null);
    }

    public static void info(String event, Map<String, ?> fields) {
        write("INFO", Level.INFO, event, fields, null);
    }

    public static void warn(String event) {
        write("WARN", Level.WARNING, event, Map.of(), null);
    }

    public static void error(String event) {
        write("ERROR", Level.SEVERE, event, Map.of(), null);
    }

    public static void error(String event, Throwable exception) {
        write("ERROR", Level.SEVERE, event, Map.of(), exception);
    }

    public static void error(
            String event,
            Map<String, ?> fields,
            Throwable exception) {
        write("ERROR", Level.SEVERE, event, fields, exception);
    }

    private static void write(
            String label,
            Level javaLevel,
            String event,
            Map<String, ?> fields,
            Throwable exception) {

        StringBuilder safeEvent = new StringBuilder(sanitize(event));
        if (fields != null) {
            fields.forEach((key, value) -> safeEvent
                    .append(" | ")
                    .append(sanitize(key))
                    .append('=')
                    .append(sanitize(String.valueOf(value))));
        }
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
