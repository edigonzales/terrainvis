package ch.so.agi.terrainvis.render;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LayerResampling {
    BILINEAR("bilinear"),
    NEAREST("nearest"),
    MAX("max");

    private final String jsonValue;

    LayerResampling(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static LayerResampling fromJson(String value) {
        if (value == null || value.isBlank()) {
            return BILINEAR;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (LayerResampling mode : values()) {
            if (mode.jsonValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported resampling: " + value + " (expected: bilinear, nearest or max)");
    }

    @JsonValue
    @Override
    public String toString() {
        return jsonValue;
    }
}
