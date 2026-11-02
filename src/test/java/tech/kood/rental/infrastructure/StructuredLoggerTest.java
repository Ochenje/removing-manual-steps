
package tech.kood.rental.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.logging.Formatter;

import static org.junit.jupiter.api.Assertions.*;

class StructuredLoggerTest {

    private Logger logger;
    private ByteArrayOutputStream output;
    private Handler handler;
    private boolean originalParentHandlers;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("tech.kood.rental");
        originalParentHandlers = logger.getUseParentHandlers();
        originalLevel = logger.getLevel();

        output = new ByteArrayOutputStream();

        handler = new StreamHandler(output, new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + System.lineSeparator();
            }
        });

        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        handler.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(handler);
        handler.close();
        logger.setLevel(originalLevel);
        logger.setUseParentHandlers(originalParentHandlers);
    }

    private String capture(Runnable action) {
        action.run();
        handler.flush();
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void infoLogsInfoEvent() {
        String result = capture(
                () -> StructuredLogger.info("TEST_INFO_EVENT")
        );

        assertTrue(result.contains("INFO"));
        assertTrue(result.contains("TEST_INFO_EVENT"));
    }

    @Test
    void warnLogsWarningEvent() {
        String result = capture(
                () -> StructuredLogger.warn("TEST_WARNING_EVENT")
        );

        assertTrue(result.contains("WARN"));
        assertTrue(result.contains("TEST_WARNING_EVENT"));
    }

    @Test
    void errorLogsErrorEvent() {
        String result = capture(
                () -> StructuredLogger.error("TEST_ERROR_EVENT")
        );

        assertTrue(result.contains("ERROR"));
        assertTrue(result.contains("TEST_ERROR_EVENT"));
    }

    @Test
    void errorLogsException() {
        IllegalStateException exception =
                new IllegalStateException("Test exception");

        LogRecord[] capturedRecord = new LogRecord[1];

        Handler testHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                capturedRecord[0] = record;
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        logger.addHandler(testHandler);

        try {
            StructuredLogger.error(
                    "DATABASE_OPERATION_FAILED",
                    exception
            );

            assertNotNull(capturedRecord[0]);
            assertEquals(exception, capturedRecord[0].getThrown());
            assertTrue(
                    capturedRecord[0].getMessage()
                            .contains("DATABASE_OPERATION_FAILED")
            );
        } finally {
            logger.removeHandler(testHandler);
        }
    }

    @Test
    void nullEventUsesUnspecifiedEvent() {
        String result = capture(() -> StructuredLogger.info(null));

        assertTrue(result.contains("UNSPECIFIED_EVENT"));
    }

    @Test
    void blankEventUsesUnspecifiedEvent() {
        String result = capture(() -> StructuredLogger.info("   "));

        assertTrue(result.contains("UNSPECIFIED_EVENT"));
    }

    @Test
    void newlineIsReplacedWithSpace() {
        String result = capture(
                () -> StructuredLogger.info("FIRST\nSECOND")
        );

        assertTrue(result.contains("FIRST SECOND"));
        assertFalse(result.contains("FIRST\nSECOND"));
    }

    @Test
    void carriageReturnIsReplacedWithSpace() {
        String result = capture(
                () -> StructuredLogger.info("FIRST\rSECOND")
        );

        assertTrue(result.contains("FIRST SECOND"));
    }

    @Test
    void pipeIsReplacedWithSlash() {
        String result = capture(
                () -> StructuredLogger.info("FIRST|SECOND")
        );

        assertTrue(result.contains("FIRST/SECOND"));
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        String result = capture(
                () -> StructuredLogger.info("  CLEAN_EVENT  ")
        );

        assertTrue(result.contains("CLEAN_EVENT"));
        assertFalse(result.contains("  CLEAN_EVENT  "));
    }
}
