package ch.so.agi.terrainvis.core;

import java.util.ArrayList;
import java.util.List;

import ch.so.agi.terrainvis.config.HorizonParameters;
import ch.so.agi.terrainvis.config.LightingParameters;
import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.tiling.TileWindow;
import ch.so.agi.terrainvis.util.NoData;

final class HorizonApproxProcessor implements TileComputationStrategy {
    private static final int SAMPLES_PER_LEVEL = 8;

    @Override
    public TileComputationResult process(
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            RunConfig runConfig,
            int threads) {
        TileWindow window = request.window();
        PixelWindow core = window.coreWindow();
        PixelWindow buffered = window.bufferedWindow();
        float[] result = new float[core.width() * core.height()];

        boolean hasCoreData = false;
        for (int y = 0; y < core.height(); y++) {
            for (int x = 0; x < core.width(); x++) {
                int bufferedIndex = index(window.coreOffsetX() + x, window.coreOffsetY() + y, buffered.width());
                if (NoData.isValid(bufferedValues[bufferedIndex], metadata.noDataValue())) {
                    hasCoreData = true;
                    break;
                }
            }
            if (hasCoreData) {
                break;
            }
        }

        if (!hasCoreData) {
            fill(result, metadata.noDataValue());
            return new TileComputationResult(request, result, true, 0);
        }

        HorizonParameters horizonParameters = runConfig.horizonParameters();
        LightingParameters lightingParameters = runConfig.lightingParameters();
        int radiusPixels = resolveRadiusPixels(runConfig, metadata, buffered);
        HorizonDirection[] directions = buildDirections(horizonParameters.directions(), metadata);
        HorizonPyramid pyramid = HorizonPyramid.build(bufferedValues, buffered.width(), buffered.height(), runConfig.exaggeration(), metadata.noDataValue(), radiusPixels);
        SunInterpolation sunInterpolation = SunInterpolation.enabled(lightingParameters, directions.length);

        int validPixels = 0;
        for (int localY = 0; localY < core.height(); localY++) {
            for (int localX = 0; localX < core.width(); localX++) {
                int sourceX = window.coreOffsetX() + localX;
                int sourceY = window.coreOffsetY() + localY;
                int bufferedIndex = index(sourceX, sourceY, buffered.width());
                float value = bufferedValues[bufferedIndex];
                int coreIndex = index(localX, localY, core.width());
                if (NoData.isNoData(value, metadata.noDataValue())) {
                    result[coreIndex] = (float) metadata.noDataValue();
                    continue;
                }

                validPixels++;
                double originHeight = value * runConfig.exaggeration();
                double diffuseSum = 0.0;
                double lowerSunAngle = Double.NaN;
                double upperSunAngle = Double.NaN;

                for (int directionIndex = 0; directionIndex < directions.length; directionIndex++) {
                    double horizonAngle = maxHorizonAngle(sourceX, sourceY, originHeight, radiusPixels, directions[directionIndex], pyramid);
                    diffuseSum += diffuseVisibility(horizonAngle, lightingParameters.bias());
                    if (sunInterpolation.enabled()) {
                        if (directionIndex == sunInterpolation.lowerIndex()) {
                            lowerSunAngle = horizonAngle;
                        }
                        if (directionIndex == sunInterpolation.upperIndex()) {
                            upperSunAngle = horizonAngle;
                        }
                    }
                }

                double diffuse = diffuseSum / directions.length;
                double sunVisibility = sunVisibility(lowerSunAngle, upperSunAngle, sunInterpolation, lightingParameters);
                double output = lightingParameters.ambientPower()
                        + (lightingParameters.skyPower() * diffuse)
                        + (lightingParameters.sunPower() * sunVisibility);
                result[coreIndex] = (float) output;
            }
        }

        return new TileComputationResult(request, result, false, validPixels);
    }

    private int resolveRadiusPixels(RunConfig runConfig, RasterMetadata metadata, PixelWindow bufferedWindow) {
        if (runConfig.horizonParameters().radiusMeters() != null) {
            int radiusX = metadata.bufferPixelsX(runConfig.horizonParameters().radiusMeters());
            int radiusY = metadata.bufferPixelsY(runConfig.horizonParameters().radiusMeters());
            return Math.max(radiusX, radiusY);
        }
        return Math.max(0, Math.max(bufferedWindow.width(), bufferedWindow.height()));
    }

    private double maxHorizonAngle(
            int originX,
            int originY,
            double originHeight,
            int radiusPixels,
            HorizonDirection direction,
            HorizonPyramid pyramid) {
        if (radiusPixels <= 0) {
            return -Math.PI / 2.0;
        }

        double maxSlope = Double.NEGATIVE_INFINITY;
        double coveredDistance = 0.0;
        for (HorizonPyramid.Level level : pyramid.levels()) {
            double levelMaxDistance = Math.min(radiusPixels, (double) level.scale() * SAMPLES_PER_LEVEL);
            for (int sampleStep = 1; sampleStep <= SAMPLES_PER_LEVEL; sampleStep++) {
                double distancePixels = (double) sampleStep * level.scale();
                if (distancePixels <= coveredDistance || distancePixels > radiusPixels) {
                    continue;
                }
                double sampleX = originX + direction.dx() * distancePixels;
                double sampleY = originY + direction.dy() * distancePixels;
                int pyramidX = (int) Math.round(sampleX / level.scale());
                int pyramidY = (int) Math.round(sampleY / level.scale());
                if (pyramidX < 0 || pyramidX >= level.width() || pyramidY < 0 || pyramidY >= level.height()) {
                    continue;
                }
                float sampleHeight = level.value(index(pyramidX, pyramidY, level.width()));
                if (NoData.isNoData(sampleHeight, pyramid.noDataValue())) {
                    continue;
                }
                double slope = (sampleHeight - originHeight) / (distancePixels * direction.metersPerPixel());
                if (slope > maxSlope) {
                    maxSlope = slope;
                }
            }
            coveredDistance = levelMaxDistance;
            if (coveredDistance >= radiusPixels) {
                break;
            }
        }
        if (!Double.isFinite(maxSlope)) {
            return -Math.PI / 2.0;
        }
        return Math.atan(maxSlope);
    }

    private double diffuseVisibility(double horizonAngle, double bias) {
        double clampedAngle = Math.max(horizonAngle, 0.0);
        double sin = Math.sin(clampedAngle);
        double visibility = 1.0 - Math.pow(sin, 2.0 / bias);
        return Math.max(0.0, Math.min(1.0, visibility));
    }

    private double sunVisibility(
            double lowerSunAngle,
            double upperSunAngle,
            SunInterpolation sunInterpolation,
            LightingParameters lightingParameters) {
        if (!sunInterpolation.enabled() || lightingParameters.sunPower() <= 0.0) {
            return 0.0;
        }

        double interpolated = sunInterpolation.interpolate(lowerSunAngle, upperSunAngle);
        double horizonDegrees = Math.toDegrees(interpolated);
        double lowerEdge = lightingParameters.sunElevationDegrees() - (lightingParameters.sunAngularDiameterDegrees() / 2.0);
        double upperEdge = lightingParameters.sunElevationDegrees() + (lightingParameters.sunAngularDiameterDegrees() / 2.0);
        if (lightingParameters.sunAngularDiameterDegrees() <= 0.0) {
            return horizonDegrees <= lightingParameters.sunElevationDegrees() ? 1.0 : 0.0;
        }
        if (horizonDegrees <= lowerEdge) {
            return 1.0;
        }
        if (horizonDegrees >= upperEdge) {
            return 0.0;
        }
        return (upperEdge - horizonDegrees) / (upperEdge - lowerEdge);
    }

    private HorizonDirection[] buildDirections(int directionCount, RasterMetadata metadata) {
        HorizonDirection[] directions = new HorizonDirection[directionCount];
        for (int i = 0; i < directionCount; i++) {
            double azimuth = (Math.PI * 2.0 * i) / directionCount;
            double dx = Math.sin(azimuth);
            double dy = -Math.cos(azimuth);
            double metersPerPixel = Math.hypot(dx * metadata.resolutionX(), dy * metadata.resolutionY());
            directions[i] = new HorizonDirection(dx, dy, metersPerPixel);
        }
        return directions;
    }

    private void fill(float[] values, double fillValue) {
        float value = (float) fillValue;
        for (int i = 0; i < values.length; i++) {
            values[i] = value;
        }
    }

    private int index(int x, int y, int width) {
        return y * width + x;
    }

    private record HorizonDirection(double dx, double dy, double metersPerPixel) {
    }

    private record SunInterpolation(boolean enabled, int lowerIndex, int upperIndex, double fraction) {
        private static SunInterpolation enabled(LightingParameters lightingParameters, int directionCount) {
            if (lightingParameters.sunPower() <= 0.0 || directionCount <= 0) {
                return new SunInterpolation(false, 0, 0, 0.0);
            }
            double step = 360.0 / directionCount;
            double normalized = lightingParameters.sunAzimuthDegrees() % 360.0;
            if (normalized < 0.0) {
                normalized += 360.0;
            }
            double scaled = normalized / step;
            int lowerIndex = (int) Math.floor(scaled) % directionCount;
            int upperIndex = (lowerIndex + 1) % directionCount;
            double fraction = scaled - Math.floor(scaled);
            if (fraction == 0.0) {
                upperIndex = lowerIndex;
            }
            return new SunInterpolation(true, lowerIndex, upperIndex, fraction);
        }

        private double interpolate(double lowerAngle, double upperAngle) {
            if (upperIndex == lowerIndex || !Double.isFinite(upperAngle)) {
                return lowerAngle;
            }
            if (!Double.isFinite(lowerAngle)) {
                return upperAngle;
            }
            return lowerAngle + ((upperAngle - lowerAngle) * fraction);
        }
    }

    private record HorizonPyramid(List<Level> levels, double noDataValue) {
        private static HorizonPyramid build(
                float[] bufferedValues,
                int width,
                int height,
                double exaggeration,
                double noDataValue,
                int radiusPixels) {
            List<Level> levels = new ArrayList<>();
            float[] exaggerated = new float[bufferedValues.length];
            for (int i = 0; i < bufferedValues.length; i++) {
                float value = bufferedValues[i];
                exaggerated[i] = NoData.isValid(value, noDataValue)
                        ? (float) (value * exaggeration)
                        : (float) noDataValue;
            }
            Level current = new Level(exaggerated, width, height, 1);
            levels.add(current);
            while (current.scale() * SAMPLES_PER_LEVEL < radiusPixels && (current.width() > 1 || current.height() > 1)) {
                current = downsample(current, noDataValue);
                levels.add(current);
            }
            return new HorizonPyramid(levels, noDataValue);
        }

        private static Level downsample(Level source, double noDataValue) {
            int width = Math.max(1, (source.width() + 1) / 2);
            int height = Math.max(1, (source.height() + 1) / 2);
            float[] values = new float[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    boolean hasValid = false;
                    double max = Double.NEGATIVE_INFINITY;
                    for (int offsetY = 0; offsetY < 2; offsetY++) {
                        int sourceY = y * 2 + offsetY;
                        if (sourceY >= source.height()) {
                            continue;
                        }
                        for (int offsetX = 0; offsetX < 2; offsetX++) {
                            int sourceX = x * 2 + offsetX;
                            if (sourceX >= source.width()) {
                                continue;
                            }
                            float value = source.value((sourceY * source.width()) + sourceX);
                            if (NoData.isValid(value, noDataValue)) {
                                hasValid = true;
                                max = Math.max(max, value);
                            }
                        }
                    }
                    values[(y * width) + x] = hasValid ? (float) max : (float) noDataValue;
                }
            }
            return new Level(values, width, height, source.scale() * 2);
        }

        private record Level(float[] values, int width, int height, int scale) {
            private float value(int index) {
                return values[index];
            }
        }
    }
}
