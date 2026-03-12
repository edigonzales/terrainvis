package ch.so.agi.terrainvis.config;

import java.util.Locale;

public enum OutputDataType {
    FLOAT32("float32"),
    UINT8("uint8");

    private final String cliValue;

    OutputDataType(String cliValue) {
        this.cliValue = cliValue;
    }

    public static OutputDataType fromCliValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("output data type is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (OutputDataType type : values()) {
            if (type.cliValue.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported output data type: " + value + " (expected: float32 or uint8)");
    }

    @Override
    public String toString() {
        return cliValue;
    }
}
