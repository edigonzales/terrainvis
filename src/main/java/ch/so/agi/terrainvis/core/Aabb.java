package ch.so.agi.terrainvis.core;

record Aabb(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
    private static final double EPSILON = 1.0e-9;

    double intersectDistance(Vector3 origin, Vector3 direction, double maxDistance) {
        double tMin = 0.0;
        double tMax = maxDistance;

        double[] axisHit = intersectAxis(origin.x(), direction.x(), minX, maxX, tMin, tMax);
        if (axisHit == null) {
            return Double.POSITIVE_INFINITY;
        }
        tMin = axisHit[0];
        tMax = axisHit[1];

        axisHit = intersectAxis(origin.y(), direction.y(), minY, maxY, tMin, tMax);
        if (axisHit == null) {
            return Double.POSITIVE_INFINITY;
        }
        tMin = axisHit[0];
        tMax = axisHit[1];

        axisHit = intersectAxis(origin.z(), direction.z(), minZ, maxZ, tMin, tMax);
        if (axisHit == null) {
            return Double.POSITIVE_INFINITY;
        }
        tMin = axisHit[0];
        tMax = axisHit[1];

        return tMin > EPSILON ? tMin : (tMax > EPSILON ? tMax : Double.POSITIVE_INFINITY);
    }

    Hit intersect(Vector3 origin, Vector3 direction, double maxDistance, int columnIndex) {
        double distance = intersectDistance(origin, direction, maxDistance);
        if (!Double.isFinite(distance)) {
            return null;
        }
        Vector3 hitPoint = origin.add(direction.multiply(distance));
        double dxMin = Math.abs(hitPoint.x() - minX);
        double dxMax = Math.abs(hitPoint.x() - maxX);
        double dyMin = Math.abs(hitPoint.y() - minY);
        double dyMax = Math.abs(hitPoint.y() - maxY);
        double dzMin = Math.abs(hitPoint.z() - minZ);
        double dzMax = Math.abs(hitPoint.z() - maxZ);

        double minDistanceToFace = dxMin;
        Vector3 normal = new Vector3(-1.0, 0.0, 0.0);
        if (dxMax < minDistanceToFace) {
            minDistanceToFace = dxMax;
            normal = new Vector3(1.0, 0.0, 0.0);
        }
        if (dyMin < minDistanceToFace) {
            minDistanceToFace = dyMin;
            normal = new Vector3(0.0, -1.0, 0.0);
        }
        if (dyMax < minDistanceToFace) {
            minDistanceToFace = dyMax;
            normal = new Vector3(0.0, 1.0, 0.0);
        }
        if (dzMin < minDistanceToFace) {
            minDistanceToFace = dzMin;
            normal = new Vector3(0.0, 0.0, -1.0);
        }
        if (dzMax < minDistanceToFace) {
            normal = new Vector3(0.0, 0.0, 1.0);
        }
        return new Hit(distance, hitPoint, normal, columnIndex);
    }

    private double[] intersectAxis(double origin, double direction, double min, double max, double tMin, double tMax) {
        if (Math.abs(direction) < EPSILON) {
            if (origin < min || origin > max) {
                return null;
            }
            return new double[] {tMin, tMax};
        }
        double inv = 1.0 / direction;
        double t1 = (min - origin) * inv;
        double t2 = (max - origin) * inv;
        double axisMin = Math.min(t1, t2);
        double axisMax = Math.max(t1, t2);
        double nextMin = Math.max(tMin, axisMin);
        double nextMax = Math.min(tMax, axisMax);
        if (nextMax < nextMin) {
            return null;
        }
        return new double[] {nextMin, nextMax};
    }
}
