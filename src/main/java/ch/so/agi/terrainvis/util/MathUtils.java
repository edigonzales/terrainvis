package ch.so.agi.terrainvis.util;

public final class MathUtils {
    private MathUtils() {
    }

    public static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int safeToInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Value does not fit into int: " + value);
        }
        return (int) value;
    }
}
