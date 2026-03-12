package ch.so.agi.terrainvis.config;

import java.util.Locale;

public record BBox(double minX, double minY, double maxX, double maxY) {
    public BBox {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            throw new IllegalArgumentException("BBOX coordinates must be finite.");
        }
        if (minX >= maxX || minY >= maxY) {
            throw new IllegalArgumentException("BBOX must satisfy minX < maxX and minY < maxY.");
        }
    }

    public static BBox parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("BBOX must contain four comma-separated numbers.");
        }
        try {
            return new BBox(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid BBOX: " + value, e);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f", minX, minY, maxX, maxY);
    }
}
