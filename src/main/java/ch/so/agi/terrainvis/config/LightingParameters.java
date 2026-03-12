package ch.so.agi.terrainvis.config;

public record LightingParameters(
        int maxBounces,
        int raysPerPixel,
        double bias,
        double ambientPower,
        double skyPower,
        double sunPower,
        double sunAzimuthDegrees,
        double sunElevationDegrees,
        double sunAngularDiameterDegrees,
        double materialReflectance) {

    public static LightingParameters defaults() {
        return new LightingParameters(0, 1024, 1.0, 0.0, 1.0, 0.0, 0.0, 45.0, 11.4, 1.0);
    }

    public LightingParameters {
        if (maxBounces < 0) {
            throw new IllegalArgumentException("maxBounces must be >= 0");
        }
        if (raysPerPixel <= 0) {
            throw new IllegalArgumentException("raysPerPixel must be > 0");
        }
        if (bias <= 0.0) {
            throw new IllegalArgumentException("bias must be > 0");
        }
        if (ambientPower < 0.0 || skyPower < 0.0 || sunPower < 0.0) {
            throw new IllegalArgumentException("light powers must be >= 0");
        }
        if (sunAzimuthDegrees < 0.0 || sunAzimuthDegrees > 360.0) {
            throw new IllegalArgumentException("sunAzimuth must be in [0, 360]");
        }
        if (sunElevationDegrees < 0.0 || sunElevationDegrees > 90.0) {
            throw new IllegalArgumentException("sunElevation must be in [0, 90]");
        }
        if (sunAngularDiameterDegrees < 0.0 || sunAngularDiameterDegrees > 180.0) {
            throw new IllegalArgumentException("sunAngularDiam must be in [0, 180]");
        }
        if (materialReflectance < 0.0) {
            throw new IllegalArgumentException("materialReflectance must be >= 0");
        }
    }
}
