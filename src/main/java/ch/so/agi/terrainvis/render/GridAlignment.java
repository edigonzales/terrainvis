package ch.so.agi.terrainvis.render;

import java.util.Arrays;

import org.geotools.referencing.CRS;

import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.util.NoData;

final class GridAlignment {
    private static final double EPSILON = 1.0e-9;

    private final int factorX;
    private final int factorY;
    private final int referenceStartX;
    private final int referenceStartY;
    private final int referenceEndX;
    private final int referenceEndY;

    private GridAlignment(
            int factorX,
            int factorY,
            int referenceStartX,
            int referenceStartY,
            int referenceEndX,
            int referenceEndY) {
        this.factorX = factorX;
        this.factorY = factorY;
        this.referenceStartX = referenceStartX;
        this.referenceStartY = referenceStartY;
        this.referenceEndX = referenceEndX;
        this.referenceEndY = referenceEndY;
    }

    static GridAlignment between(RasterMetadata inputMetadata, RasterMetadata referenceMetadata, String label) {
        if (!CRS.equalsIgnoreMetadata(inputMetadata.crs(), referenceMetadata.crs())) {
            throw new IllegalArgumentException(label + " CRS does not match the reference raster.");
        }
        if (inputMetadata.resolutionX() - referenceMetadata.resolutionX() > EPSILON
                || inputMetadata.resolutionY() - referenceMetadata.resolutionY() > EPSILON) {
            throw new IllegalArgumentException(label + " resolution must be finer than or equal to the reference raster.");
        }

        int factorX = integerFactor(referenceMetadata.resolutionX() / inputMetadata.resolutionX());
        int factorY = integerFactor(referenceMetadata.resolutionY() / inputMetadata.resolutionY());

        if (inputMetadata.width() % factorX != 0 || inputMetadata.height() % factorY != 0) {
            throw new IllegalArgumentException(label + " dimensions are not aligned to the reference raster grid.");
        }

        int referenceStartX = alignedOffset(
                (inputMetadata.envelope().getMinX() - referenceMetadata.envelope().getMinX()) / referenceMetadata.resolutionX(),
                label + " minX is not aligned to the reference raster grid.");
        int referenceStartY = alignedOffset(
                (referenceMetadata.envelope().getMaxY() - inputMetadata.envelope().getMaxY()) / referenceMetadata.resolutionY(),
                label + " maxY is not aligned to the reference raster grid.");

        int referenceWidth = inputMetadata.width() / factorX;
        int referenceHeight = inputMetadata.height() / factorY;
        return new GridAlignment(
                factorX,
                factorY,
                referenceStartX,
                referenceStartY,
                referenceStartX + referenceWidth,
                referenceStartY + referenceHeight);
    }

    int factorX() {
        return factorX;
    }

    int factorY() {
        return factorY;
    }

    void requireSameExtent(RasterMetadata referenceMetadata, String label) {
        if (referenceStartX != 0
                || referenceStartY != 0
                || referenceEndX != referenceMetadata.width()
                || referenceEndY != referenceMetadata.height()) {
            throw new IllegalArgumentException(label + " extent does not match the reference raster.");
        }
    }

    PixelWindow inputWindow(PixelWindow referenceWindow) {
        WindowMapping mapping = mapping(referenceWindow);
        return mapping.inputWindow();
    }

    float[] aggregateMax(PixelWindow referenceWindow, float[] inputValues, double noDataValue) {
        float noData = Double.isNaN(noDataValue) ? Float.NaN : (float) noDataValue;
        float[] outputValues = new float[referenceWindow.width() * referenceWindow.height()];
        Arrays.fill(outputValues, noData);

        WindowMapping mapping = mapping(referenceWindow);
        if (mapping.inputWindow().isEmpty()) {
            return outputValues;
        }
        if (inputValues.length != mapping.inputWindow().width() * mapping.inputWindow().height()) {
            throw new IllegalArgumentException("inputValues length does not match the aligned input window.");
        }

        for (int row = 0; row < mapping.targetHeight(); row++) {
            int inputRowStart = row * factorY;
            int targetRow = row + mapping.targetOffsetY();
            for (int column = 0; column < mapping.targetWidth(); column++) {
                int inputColumnStart = column * factorX;
                float maxValue = 0.0f;
                boolean hasValue = false;
                for (int offsetY = 0; offsetY < factorY; offsetY++) {
                    int rowOffset = (inputRowStart + offsetY) * mapping.inputWindow().width();
                    for (int offsetX = 0; offsetX < factorX; offsetX++) {
                        float candidate = inputValues[rowOffset + inputColumnStart + offsetX];
                        if (NoData.isNoData(candidate, noDataValue)) {
                            continue;
                        }
                        if (!hasValue || candidate > maxValue) {
                            maxValue = candidate;
                            hasValue = true;
                        }
                    }
                }
                if (hasValue) {
                    int targetColumn = column + mapping.targetOffsetX();
                    outputValues[(targetRow * referenceWindow.width()) + targetColumn] = maxValue;
                }
            }
        }
        return outputValues;
    }

    private WindowMapping mapping(PixelWindow referenceWindow) {
        int overlapStartX = Math.max(referenceWindow.x(), referenceStartX);
        int overlapEndX = Math.min(referenceWindow.endX(), referenceEndX);
        int overlapStartY = Math.max(referenceWindow.y(), referenceStartY);
        int overlapEndY = Math.min(referenceWindow.endY(), referenceEndY);
        if (overlapStartX >= overlapEndX || overlapStartY >= overlapEndY) {
            return WindowMapping.empty();
        }

        int targetOffsetX = overlapStartX - referenceWindow.x();
        int targetOffsetY = overlapStartY - referenceWindow.y();
        int targetWidth = overlapEndX - overlapStartX;
        int targetHeight = overlapEndY - overlapStartY;
        PixelWindow inputWindow = new PixelWindow(
                (overlapStartX - referenceStartX) * factorX,
                (overlapStartY - referenceStartY) * factorY,
                targetWidth * factorX,
                targetHeight * factorY);
        return new WindowMapping(inputWindow, targetOffsetX, targetOffsetY, targetWidth, targetHeight);
    }

    private static int integerFactor(double factor) {
        long rounded = Math.round(factor);
        if (rounded <= 0 || Math.abs(factor - rounded) > EPSILON || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Reference-to-input resolution ratio must be an integer in both axes.");
        }
        return (int) rounded;
    }

    private static int alignedOffset(double value, String message) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) > EPSILON || rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(message);
        }
        return (int) rounded;
    }

    private record WindowMapping(
            PixelWindow inputWindow,
            int targetOffsetX,
            int targetOffsetY,
            int targetWidth,
            int targetHeight) {
        private static WindowMapping empty() {
            return new WindowMapping(new PixelWindow(0, 0, 0, 0), 0, 0, 0, 0);
        }
    }
}
