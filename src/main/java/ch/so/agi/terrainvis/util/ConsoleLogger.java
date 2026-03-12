package ch.so.agi.terrainvis.util;

import java.io.PrintWriter;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ConsoleLogger {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final PrintWriter stdout;
    private final PrintWriter stderr;
    private final Clock clock;
    private final boolean verboseEnabled;
    private final Object lock = new Object();

    public ConsoleLogger(PrintWriter stdout, PrintWriter stderr, Clock clock, boolean verboseEnabled) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.clock = clock;
        this.verboseEnabled = verboseEnabled;
    }

    public static ConsoleLogger system(boolean verboseEnabled) {
        return new ConsoleLogger(new PrintWriter(System.out, true), new PrintWriter(System.err, true), Clock.systemDefaultZone(), verboseEnabled);
    }

    public boolean isVerboseEnabled() {
        return verboseEnabled;
    }

    public void info(String message, Object... args) {
        log(stdout, "INFO", message, args);
    }

    public void warn(String message, Object... args) {
        log(stderr, "WARN", message, args);
    }

    public void error(String message, Object... args) {
        log(stderr, "ERROR", message, args);
    }

    public void verbose(String message, Object... args) {
        if (!verboseEnabled) {
            return;
        }
        log(stdout, "VERBOSE", message, args);
    }

    private void log(PrintWriter writer, String level, String message, Object... args) {
        String rendered = args.length == 0 ? message : String.format(Locale.ROOT, message, args);
        String timestamp = LocalTime.now(clock).format(TIME_FORMATTER);
        synchronized (lock) {
            writer.printf("[%s] [%s] %s%n", timestamp, level, rendered);
            writer.flush();
        }
    }
}
