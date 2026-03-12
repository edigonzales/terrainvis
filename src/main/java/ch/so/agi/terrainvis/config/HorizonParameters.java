package ch.so.agi.terrainvis.config;

public record HorizonParameters(int directions, Double radiusMeters) {
    public static HorizonParameters defaults() {
        return new HorizonParameters(32, null);
    }

    public HorizonParameters {
        if (directions <= 0) {
            throw new IllegalArgumentException("horizonDirections must be > 0");
        }
        if (radiusMeters != null && radiusMeters < 0.0) {
            throw new IllegalArgumentException("horizonRadiusMeters must be >= 0");
        }
    }
}
