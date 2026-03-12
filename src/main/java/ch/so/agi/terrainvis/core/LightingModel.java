package ch.so.agi.terrainvis.core;

import ch.so.agi.terrainvis.config.LightingParameters;

public final class LightingModel {
    private LightingModel() {
    }

    public static double visibleContribution(Vector3 direction, LightingParameters parameters, double radianceFactor) {
        return visibleContribution(direction.normalize(), prepare(parameters), radianceFactor);
    }

    public static double visibleContribution(Vector3 direction, PreparedLighting preparedLighting, double radianceFactor) {
        double contribution = radianceFactor * preparedLighting.skyPower();
        if (preparedLighting.sunEnabled() && direction.dot(preparedLighting.sunDirection()) >= preparedLighting.sunCosHalfAngle()) {
            contribution += radianceFactor * preparedLighting.sunPower();
        }
        return contribution;
    }

    public static PreparedLighting prepare(LightingParameters parameters) {
        if (parameters.sunPower() <= 0.0) {
            return new PreparedLighting(parameters.skyPower(), 0.0, new Vector3(0.0, 0.0, 1.0), false, -1.0);
        }
        return new PreparedLighting(
                parameters.skyPower(),
                parameters.sunPower(),
                sunDirection(parameters),
                true,
                Math.cos(Math.toRadians(parameters.sunAngularDiameterDegrees()) / 2.0));
    }

    public static Vector3 sunDirection(LightingParameters parameters) {
        double azimuth = Math.toRadians(parameters.sunAzimuthDegrees());
        double elevation = Math.toRadians(parameters.sunElevationDegrees());
        double horizontal = Math.cos(elevation);
        double x = horizontal * Math.sin(azimuth);
        double y = -horizontal * Math.cos(azimuth);
        double z = Math.sin(elevation);
        return new Vector3(x, y, z).normalize();
    }

    public record PreparedLighting(
            double skyPower,
            double sunPower,
            Vector3 sunDirection,
            boolean sunEnabled,
            double sunCosHalfAngle) {
    }
}
