package ch.so.agi.terrainvis.core;

public record Ray(Vector3 origin, Vector3 direction) {
    public Ray {
        if (origin == null || direction == null) {
            throw new IllegalArgumentException("Ray origin and direction must not be null");
        }
    }

    public Vector3 pointAt(double distance) {
        return origin.add(direction.multiply(distance));
    }
}
