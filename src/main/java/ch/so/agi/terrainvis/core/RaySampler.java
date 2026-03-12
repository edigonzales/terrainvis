package ch.so.agi.terrainvis.core;

import ch.so.agi.terrainvis.config.LightingParameters;

public final class RaySampler {
    private static final long BASE_SEED = 1423L;
    private static final int DIRECTION_SEGMENTS = 16;
    private static final double UNIT_SCALE = 0x1.0p-53;

    public Vector3 primaryDirection(int tileId, int pixelIndex, int rayIndex, LightingParameters parameters) {
        int raysPerDirection = Math.max(1, parameters.raysPerPixel() / DIRECTION_SEGMENTS);
        int phiSegment = Math.min(DIRECTION_SEGMENTS - 1, rayIndex / raysPerDirection);
        int thetaSegment = rayIndex % raysPerDirection;

        double rndTheta = positiveUnit(seed(tileId, pixelIndex, rayIndex, 0));
        double rndPhi = positiveUnit(seed(tileId, pixelIndex, rayIndex, 1));

        double stratifiedTheta = (thetaSegment + rndTheta) / raysPerDirection;
        double thetaValue = parameters.bias() == 1.0 ? stratifiedTheta : Math.pow(stratifiedTheta, parameters.bias());
        double phiValue = (phiSegment + rndPhi) / DIRECTION_SEGMENTS;
        return sampleLocalHemisphere(phiValue, thetaValue);
    }

    public Vector3 bounceDirection(int tileId, int pixelIndex, int rayIndex, int bounceIndex, Vector3 normal) {
        double rndTheta = positiveUnit(seed(tileId, pixelIndex, rayIndex, 100 + bounceIndex * 2L));
        double rndPhi = positiveUnit(seed(tileId, pixelIndex, rayIndex, 101 + bounceIndex * 2L));
        Vector3 local = sampleLocalHemisphere(rndPhi, rndTheta);
        return orientToNormal(local, normal);
    }

    private Vector3 sampleLocalHemisphere(double phiSample, double thetaSample) {
        double z = Math.sqrt(thetaSample);
        double radial = Math.sqrt(Math.max(0.0, 1.0 - thetaSample));
        double phi = Math.PI * 2.0 * phiSample;
        return new Vector3(radial * Math.cos(phi), radial * Math.sin(phi), z);
    }

    private Vector3 orientToNormal(Vector3 local, Vector3 normal) {
        Vector3 n = normal.normalize();
        Vector3 helper = Math.abs(n.z()) < 0.999 ? new Vector3(0.0, 0.0, 1.0) : new Vector3(1.0, 0.0, 0.0);
        Vector3 tangent = helper.cross(n).normalize();
        Vector3 bitangent = n.cross(tangent).normalize();
        return tangent.multiply(local.x()).add(bitangent.multiply(local.y())).add(n.multiply(local.z()));
    }

    private long seed(int tileId, int pixelIndex, int rayIndex, long salt) {
        long value = BASE_SEED;
        value ^= mix(tileId + 0x9E3779B97F4A7C15L);
        value ^= mix(pixelIndex + 0xC2B2AE3D27D4EB4FL);
        value ^= mix(rayIndex + 0x165667B19E3779F9L);
        value ^= mix(salt);
        return value;
    }

    private double positiveUnit(long seed) {
        double value = ((mix(seed) >>> 11) * UNIT_SCALE);
        return value == 0.0 ? Math.ulp(1.0) : value;
    }

    private long mix(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
