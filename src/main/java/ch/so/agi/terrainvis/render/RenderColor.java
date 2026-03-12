package ch.so.agi.terrainvis.render;

import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record RenderColor(int red, int green, int blue) {
    private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public RenderColor {
        validateChannel(red, "red");
        validateChannel(green, "green");
        validateChannel(blue, "blue");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RenderColor fromHex(String value) {
        if (value == null || !HEX_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Expected color in #RRGGBB format but got: " + value);
        }
        return new RenderColor(
                Integer.parseInt(value.substring(1, 3), 16),
                Integer.parseInt(value.substring(3, 5), 16),
                Integer.parseInt(value.substring(5, 7), 16));
    }

    @JsonValue
    public String toHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    private static void validateChannel(int channel, String name) {
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException(name + " must be in [0,255]");
        }
    }
}
