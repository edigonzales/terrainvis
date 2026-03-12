package ch.so.agi.terrainvis.core;

import java.util.Arrays;

import ch.so.agi.terrainvis.util.NoData;

public final class CpuBvh {
    private static final double TRACE_EPSILON = 1.0e-6;
    private static final double AXIS_EPSILON = 1.0e-9;
    private static final int MIN_STACK_SIZE = 64;
    private static final Vector3 NORMAL_NEGATIVE_X = new Vector3(-1.0, 0.0, 0.0);
    private static final Vector3 NORMAL_POSITIVE_X = new Vector3(1.0, 0.0, 0.0);
    private static final Vector3 NORMAL_NEGATIVE_Y = new Vector3(0.0, -1.0, 0.0);
    private static final Vector3 NORMAL_POSITIVE_Y = new Vector3(0.0, 1.0, 0.0);
    private static final Vector3 NORMAL_NEGATIVE_Z = new Vector3(0.0, 0.0, -1.0);
    private static final Vector3 NORMAL_POSITIVE_Z = new Vector3(0.0, 0.0, 1.0);

    private final int width;
    private final int height;
    private final double pixelSizeX;
    private final double pixelSizeY;
    private final double halfPixelSizeX;
    private final double halfPixelSizeY;
    private final int traversalStackSize;
    private final int[] pixelToColumn;

    private final double[] centerX;
    private final double[] centerY;
    private final double[] columnHeight;

    private int[] order;

    private double[] minX;
    private double[] maxX;
    private double[] minY;
    private double[] maxY;
    private double[] maxZ;
    private int[] leftChild;
    private int[] rightChild;
    private int[] leafColumn;
    private boolean[] leafNode;

    private int nodeCount;
    private int root = -1;

    public CpuBvh(
            float[] values,
            int width,
            int height,
            double pixelSizeX,
            double pixelSizeY,
            double exaggeration,
            double noDataValue) {
        this.width = width;
        this.height = height;
        this.pixelSizeX = pixelSizeX;
        this.pixelSizeY = pixelSizeY;
        this.halfPixelSizeX = pixelSizeX / 2.0;
        this.halfPixelSizeY = pixelSizeY / 2.0;
        this.pixelToColumn = new int[values.length];
        Arrays.fill(pixelToColumn, -1);

        int validCount = 0;
        for (float value : values) {
            if (NoData.isValid(value, noDataValue)) {
                validCount++;
            }
        }
        this.centerX = new double[validCount];
        this.centerY = new double[validCount];
        this.columnHeight = new double[validCount];

        int cursor = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = index(x, y);
                float value = values[pixelIndex];
                if (NoData.isValid(value, noDataValue)) {
                    centerX[cursor] = x * pixelSizeX;
                    centerY[cursor] = y * pixelSizeY;
                    columnHeight[cursor] = value * exaggeration;
                    pixelToColumn[pixelIndex] = cursor;
                    cursor++;
                }
            }
        }

        this.order = new int[validCount];
        for (int i = 0; i < validCount; i++) {
            order[i] = i;
        }
        this.traversalStackSize = Math.max(MIN_STACK_SIZE, validCount <= 1 ? MIN_STACK_SIZE : (32 - Integer.numberOfLeadingZeros(validCount - 1)) * 2 + 8);

        int capacity = Math.max(1, validCount * 2);
        this.minX = new double[capacity];
        this.maxX = new double[capacity];
        this.minY = new double[capacity];
        this.maxY = new double[capacity];
        this.maxZ = new double[capacity];
        this.leftChild = new int[capacity];
        this.rightChild = new int[capacity];
        this.leafColumn = new int[capacity];
        this.leafNode = new boolean[capacity];
        Arrays.fill(leftChild, -1);
        Arrays.fill(rightChild, -1);
        Arrays.fill(leafColumn, -1);

        if (validCount > 0) {
            root = build(0, validCount);
        }
        order = null;
    }

    public boolean isEmpty() {
        return root < 0;
    }

    public int columnIndexForPixel(int x, int y) {
        return pixelToColumn[index(x, y)];
    }

    public Vector3 originForPixel(int x, int y, double elevation) {
        return new Vector3(x * pixelSizeX, y * pixelSizeY, elevation);
    }

    public Hit findNearestHit(Vector3 origin, Vector3 direction, int originColumnIndex) {
        return findNearestHit(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(), originColumnIndex, newTraversalContext());
    }

    public boolean hasAnyHit(Vector3 origin, Vector3 direction, int originColumnIndex) {
        return hasAnyHit(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z(), originColumnIndex, newTraversalContext());
    }

    public Hit findNearestHit(
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            int originColumnIndex,
            TraversalContext traversalContext) {
        if (root < 0) {
            return null;
        }
        int[] stack = traversalContext.stack();
        int size = 0;
        stack[size++] = root;
        Hit closestHit = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        while (size > 0) {
            int nodeIndex = stack[--size];
            double nodeDistance = intersectNodeDistance(nodeIndex, originX, originY, originZ, directionX, directionY, directionZ, closestDistance);
            if (!Double.isFinite(nodeDistance)) {
                continue;
            }

            if (leafNode[nodeIndex]) {
                int columnIndex = leafColumn[nodeIndex];
                if (columnIndex == originColumnIndex) {
                    continue;
                }
                Hit hit = createLeafHit(nodeIndex, originX, originY, originZ, directionX, directionY, directionZ, nodeDistance, columnIndex);
                if (hit != null && hit.distance() > TRACE_EPSILON && hit.distance() < closestDistance) {
                    closestDistance = hit.distance();
                    closestHit = hit;
                }
                continue;
            }

            int left = leftChild[nodeIndex];
            int right = rightChild[nodeIndex];
            double leftDistance = left >= 0
                    ? intersectNodeDistance(left, originX, originY, originZ, directionX, directionY, directionZ, closestDistance)
                    : Double.POSITIVE_INFINITY;
            double rightDistance = right >= 0
                    ? intersectNodeDistance(right, originX, originY, originZ, directionX, directionY, directionZ, closestDistance)
                    : Double.POSITIVE_INFINITY;
            if (leftDistance < rightDistance) {
                if (right >= 0 && Double.isFinite(rightDistance)) {
                    stack[size++] = right;
                }
                if (left >= 0 && Double.isFinite(leftDistance)) {
                    stack[size++] = left;
                }
            } else {
                if (left >= 0 && Double.isFinite(leftDistance)) {
                    stack[size++] = left;
                }
                if (right >= 0 && Double.isFinite(rightDistance)) {
                    stack[size++] = right;
                }
            }
        }
        return closestHit;
    }

    public boolean hasAnyHit(
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            int originColumnIndex,
            TraversalContext traversalContext) {
        if (root < 0) {
            return false;
        }
        int[] stack = traversalContext.stack();
        int size = 0;
        stack[size++] = root;

        while (size > 0) {
            int nodeIndex = stack[--size];
            double nodeDistance = intersectNodeDistance(nodeIndex, originX, originY, originZ, directionX, directionY, directionZ, Double.POSITIVE_INFINITY);
            if (!Double.isFinite(nodeDistance)) {
                continue;
            }

            if (leafNode[nodeIndex]) {
                if (leafColumn[nodeIndex] != originColumnIndex && nodeDistance > TRACE_EPSILON) {
                    return true;
                }
                continue;
            }

            int left = leftChild[nodeIndex];
            int right = rightChild[nodeIndex];
            double leftDistance = left >= 0
                    ? intersectNodeDistance(left, originX, originY, originZ, directionX, directionY, directionZ, Double.POSITIVE_INFINITY)
                    : Double.POSITIVE_INFINITY;
            double rightDistance = right >= 0
                    ? intersectNodeDistance(right, originX, originY, originZ, directionX, directionY, directionZ, Double.POSITIVE_INFINITY)
                    : Double.POSITIVE_INFINITY;
            if (leftDistance < rightDistance) {
                if (right >= 0 && Double.isFinite(rightDistance)) {
                    stack[size++] = right;
                }
                if (left >= 0 && Double.isFinite(leftDistance)) {
                    stack[size++] = left;
                }
            } else {
                if (left >= 0 && Double.isFinite(leftDistance)) {
                    stack[size++] = left;
                }
                if (right >= 0 && Double.isFinite(rightDistance)) {
                    stack[size++] = right;
                }
            }
        }
        return false;
    }

    public TraversalContext newTraversalContext() {
        return new TraversalContext(traversalStackSize);
    }

    private int build(int start, int end) {
        int nodeIndex = nodeCount++;
        ensureCapacity(nodeCount);

        double localMinX = Double.POSITIVE_INFINITY;
        double localMaxX = Double.NEGATIVE_INFINITY;
        double localMinY = Double.POSITIVE_INFINITY;
        double localMaxY = Double.NEGATIVE_INFINITY;
        double localMaxZ = Double.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            int column = order[i];
            double centerXValue = centerX[column];
            double centerYValue = centerY[column];
            double heightValue = columnHeight[column];
            localMinX = Math.min(localMinX, centerXValue - halfPixelSizeX);
            localMaxX = Math.max(localMaxX, centerXValue + halfPixelSizeX);
            localMinY = Math.min(localMinY, centerYValue - halfPixelSizeY);
            localMaxY = Math.max(localMaxY, centerYValue + halfPixelSizeY);
            localMaxZ = Math.max(localMaxZ, heightValue);
        }
        minX[nodeIndex] = localMinX;
        maxX[nodeIndex] = localMaxX;
        minY[nodeIndex] = localMinY;
        maxY[nodeIndex] = localMaxY;
        maxZ[nodeIndex] = localMaxZ;

        int size = end - start;
        if (size == 1) {
            leafNode[nodeIndex] = true;
            leafColumn[nodeIndex] = order[start];
            return nodeIndex;
        }

        boolean splitOnX = (localMaxX - localMinX) >= (localMaxY - localMinY);
        double pivot = splitOnX ? (localMinX + localMaxX) / 2.0 : (localMinY + localMaxY) / 2.0;
        int middle = partition(start, end, splitOnX, pivot);
        if (middle <= start || middle >= end) {
            middle = start + size / 2;
        }

        leftChild[nodeIndex] = build(start, middle);
        rightChild[nodeIndex] = build(middle, end);
        return nodeIndex;
    }

    private int partition(int start, int end, boolean splitOnX, double pivot) {
        int left = start;
        int right = end - 1;
        while (left <= right) {
            double leftValue = splitOnX ? centerX[order[left]] : centerY[order[left]];
            if (leftValue < pivot) {
                left++;
                continue;
            }
            double rightValue = splitOnX ? centerX[order[right]] : centerY[order[right]];
            if (rightValue >= pivot) {
                right--;
                continue;
            }
            int tmp = order[left];
            order[left] = order[right];
            order[right] = tmp;
            left++;
            right--;
        }
        return left;
    }

    private void ensureCapacity(int requested) {
        if (requested <= minX.length) {
            return;
        }
        int newCapacity = minX.length * 2;
        minX = Arrays.copyOf(minX, newCapacity);
        maxX = Arrays.copyOf(maxX, newCapacity);
        minY = Arrays.copyOf(minY, newCapacity);
        maxY = Arrays.copyOf(maxY, newCapacity);
        maxZ = Arrays.copyOf(maxZ, newCapacity);
        leftChild = Arrays.copyOf(leftChild, newCapacity);
        rightChild = Arrays.copyOf(rightChild, newCapacity);
        leafColumn = Arrays.copyOf(leafColumn, newCapacity);
        leafNode = Arrays.copyOf(leafNode, newCapacity);
        Arrays.fill(leftChild, requested - 1, newCapacity, -1);
        Arrays.fill(rightChild, requested - 1, newCapacity, -1);
        Arrays.fill(leafColumn, requested - 1, newCapacity, -1);
    }

    private double intersectNodeDistance(
            int nodeIndex,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            double maxDistance) {
        return intersectAabbDistance(
                minX[nodeIndex],
                maxX[nodeIndex],
                minY[nodeIndex],
                maxY[nodeIndex],
                0.0,
                maxZ[nodeIndex],
                originX,
                originY,
                originZ,
                directionX,
                directionY,
                directionZ,
                maxDistance);
    }

    private double intersectAabbDistance(
            double minX,
            double maxX,
            double minY,
            double maxY,
            double minZ,
            double maxZ,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            double maxDistance) {
        double tMin = 0.0;
        double tMax = maxDistance;

        if (Math.abs(directionX) < AXIS_EPSILON) {
            if (originX < minX || originX > maxX) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double invDirection = 1.0 / directionX;
            double t1 = (minX - originX) * invDirection;
            double t2 = (maxX - originX) * invDirection;
            double axisMin = Math.min(t1, t2);
            double axisMax = Math.max(t1, t2);
            tMin = Math.max(tMin, axisMin);
            tMax = Math.min(tMax, axisMax);
            if (tMax < tMin) {
                return Double.POSITIVE_INFINITY;
            }
        }

        if (Math.abs(directionY) < AXIS_EPSILON) {
            if (originY < minY || originY > maxY) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double invDirection = 1.0 / directionY;
            double t1 = (minY - originY) * invDirection;
            double t2 = (maxY - originY) * invDirection;
            double axisMin = Math.min(t1, t2);
            double axisMax = Math.max(t1, t2);
            tMin = Math.max(tMin, axisMin);
            tMax = Math.min(tMax, axisMax);
            if (tMax < tMin) {
                return Double.POSITIVE_INFINITY;
            }
        }

        if (Math.abs(directionZ) < AXIS_EPSILON) {
            if (originZ < minZ || originZ > maxZ) {
                return Double.POSITIVE_INFINITY;
            }
        } else {
            double invDirection = 1.0 / directionZ;
            double t1 = (minZ - originZ) * invDirection;
            double t2 = (maxZ - originZ) * invDirection;
            double axisMin = Math.min(t1, t2);
            double axisMax = Math.max(t1, t2);
            tMin = Math.max(tMin, axisMin);
            tMax = Math.min(tMax, axisMax);
            if (tMax < tMin) {
                return Double.POSITIVE_INFINITY;
            }
        }

        return tMin > AXIS_EPSILON ? tMin : (tMax > AXIS_EPSILON ? tMax : Double.POSITIVE_INFINITY);
    }

    private Hit createLeafHit(
            int nodeIndex,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            double distance,
            int columnIndex) {
        if (!Double.isFinite(distance)) {
            return null;
        }
        double hitX = originX + directionX * distance;
        double hitY = originY + directionY * distance;
        double hitZ = originZ + directionZ * distance;

        double dxMin = Math.abs(hitX - minX[nodeIndex]);
        double dxMax = Math.abs(hitX - maxX[nodeIndex]);
        double dyMin = Math.abs(hitY - minY[nodeIndex]);
        double dyMax = Math.abs(hitY - maxY[nodeIndex]);
        double dzMin = Math.abs(hitZ - 0.0);
        double dzMax = Math.abs(hitZ - maxZ[nodeIndex]);

        double minDistanceToFace = dxMin;
        Vector3 normal = NORMAL_NEGATIVE_X;
        if (dxMax < minDistanceToFace) {
            minDistanceToFace = dxMax;
            normal = NORMAL_POSITIVE_X;
        }
        if (dyMin < minDistanceToFace) {
            minDistanceToFace = dyMin;
            normal = NORMAL_NEGATIVE_Y;
        }
        if (dyMax < minDistanceToFace) {
            minDistanceToFace = dyMax;
            normal = NORMAL_POSITIVE_Y;
        }
        if (dzMin < minDistanceToFace) {
            minDistanceToFace = dzMin;
            normal = NORMAL_NEGATIVE_Z;
        }
        if (dzMax < minDistanceToFace) {
            normal = NORMAL_POSITIVE_Z;
        }
        return new Hit(distance, new Vector3(hitX, hitY, hitZ), normal, columnIndex);
    }

    private int index(int x, int y) {
        return y * width + x;
    }

    public static final class TraversalContext {
        private final int[] stack;

        private TraversalContext(int stackSize) {
            this.stack = new int[stackSize];
        }

        private int[] stack() {
            return stack;
        }
    }
}
