package tech.kood.rental;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void startsAndStopsUsingDatabasePathArgument() throws Exception {
        Path databasePath = tempDir.resolve("main-test.db");

        var originalIn = System.in;
        var originalOut = System.out;
        var originalErr = System.err;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        try {
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8));

            Main.main(new String[]{databasePath.toString()});
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        assertTrue(Files.exists(databasePath),
                "Main should initialize the database");
        assertTrue(Files.size(databasePath) > 0,
                "The initialized database should not be empty");
    }
}