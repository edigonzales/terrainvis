package ch.so.agi.terrainvis.render;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BlendMode {
    NORMAL("normal"),
    MULTIPLY("multiply");

    private final String jsonValue;

    BlendMode(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static BlendMode fromJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("blendMode is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (BlendMode mode : values()) {
            if (mode.jsonValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported blendMode: " + value + " (expected: normal or multiply)");
    }

    @JsonValue
    @Override
    public String toString() {
        return jsonValue;
    }
}
