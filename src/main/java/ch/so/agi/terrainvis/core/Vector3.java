package ch.so.agi.terrainvis.core;

public record Vector3(double x, double y, double z) {
    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 multiply(double factor) {
        return new Vector3(x * factor, y * factor, z * factor);
    }

    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3 cross(Vector3 other) {
        return new Vector3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public Vector3 normalize() {
        double length = length();
        if (length == 0.0) {
            return this;
        }
        return new Vector3(x / length, y / length, z / length);
    }
}
