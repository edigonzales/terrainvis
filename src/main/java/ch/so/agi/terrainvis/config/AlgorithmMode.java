package ch.so.agi.terrainvis.config;

import java.util.Locale;

public enum AlgorithmMode {
    EXACT("exact"),
    HORIZON("horizon");

    private final String cliValue;

    AlgorithmMode(String cliValue) {
        this.cliValue = cliValue;
    }

    public static AlgorithmMode fromCliValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("algorithm is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AlgorithmMode mode : values()) {
            if (mode.cliValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported algorithm: " + value + " (expected: exact or horizon)");
    }

    @Override
    public String toString() {
        return cliValue;
    }
}
