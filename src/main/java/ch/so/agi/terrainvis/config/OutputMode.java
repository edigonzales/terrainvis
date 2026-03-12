package ch.so.agi.terrainvis.config;

import java.util.Locale;

public enum OutputMode {
    TILE_FILES("tile-files"),
    SINGLE_FILE("single-file");

    private final String cliValue;

    OutputMode(String cliValue) {
        this.cliValue = cliValue;
    }

    public static OutputMode fromCliValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("output mode is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (OutputMode mode : values()) {
            if (mode.cliValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported output mode: " + value + " (expected: single-file or tile-files)");
    }

    @Override
    public String toString() {
        return cliValue;
    }
}
