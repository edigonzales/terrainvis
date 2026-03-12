package ch.so.agi.terrainvis.util;

public final class NoData {
    private static final double EPSILON = 1.0e-6;

    private NoData() {
    }

    public static boolean isNoData(double value, double noDataValue) {
        if (Double.isNaN(noDataValue)) {
            return Double.isNaN(value);
        }
        return Double.isNaN(value) || Math.abs(value - noDataValue) <= EPSILON;
    }

    public static boolean isValid(double value, double noDataValue) {
        return !isNoData(value, noDataValue);
    }
}
