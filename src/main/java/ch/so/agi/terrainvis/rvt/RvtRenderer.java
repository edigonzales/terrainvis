package ch.so.agi.terrainvis.rvt;

import static java.lang.Math.PI;

import java.util.Arrays;

import ch.so.agi.terrainvis.output.RasterBlock;
import ch.so.agi.terrainvis.output.RasterTileResult;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.tiling.TileWindow;
import ch.so.agi.terrainvis.util.NoData;

public final class RvtRenderer {
    public RasterTileResult renderTile(
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            RvtRunConfig runConfig) {
        TileWindow window = request.window();
        float[][] dem = toMatrix(bufferedValues, window.bufferedWindow().width(), window.bufferedWindow().height(), metadata.noDataValue());
        int coreWidth = window.coreWindow().width();
        int coreHeight = window.coreWindow().height();
        int validPixelCount = countValidCore(dem, window, metadata.noDataValue());
        if (validPixelCount == 0) {
            return new RasterTileResult(
                    request,
                    filledBlock(coreWidth, coreHeight, RvtDefaults.bandCount(runConfig.product(), runConfig.parameters()), metadata.noDataValue()),
                    true,
                    0);
        }
        TileContext context = new TileContext(dem, metadata, request, runConfig);
        ComputationCache cache = new ComputationCache();
        RasterBlock block = switch (runConfig.product()) {
            case SLOPE -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), slopeDegrees(context, cache));
            case HILLSHADE -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), hillshade(context, cache,
                    ((RvtParameters.HillshadeParameters) runConfig.parameters()).sunAzimuthDegrees(),
                    ((RvtParameters.HillshadeParameters) runConfig.parameters()).sunElevationDegrees()));
            case MULTI_HILLSHADE -> multiHillshadeBlock(context, cache, (RvtParameters.MultiHillshadeParameters) runConfig.parameters());
            case SLRM -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), slrm(context, (RvtParameters.SlrmParameters) runConfig.parameters()));
            case SVF -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), skyView(context, cache, (RvtParameters.SkyViewParameters) runConfig.parameters()).svf());
            case ASVF -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), skyView(context, cache, (RvtParameters.SkyViewParameters) runConfig.parameters()).asvf());
            case POSITIVE_OPENNESS -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), skyView(context, cache, (RvtParameters.SkyViewParameters) runConfig.parameters()).positiveOpenness());
            case NEGATIVE_OPENNESS -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), negativeOpenness(context, cache, (RvtParameters.SkyViewParameters) runConfig.parameters()));
            case SKY_ILLUMINATION -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), skyIllumination(context, cache, (RvtParameters.SkyIlluminationParameters) runConfig.parameters()));
            case LOCAL_DOMINANCE -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), localDominance(context, (RvtParameters.LocalDominanceParameters) runConfig.parameters()));
            case MSRM -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), msrm(context, (RvtParameters.MsrmParameters) runConfig.parameters()));
            case MSTP -> mstp(context, (RvtParameters.MstpParameters) runConfig.parameters());
            case VAT -> singleBandBlock(coreWidth, coreHeight, metadata.noDataValue(), vat(context, cache, (RvtParameters.VatParameters) runConfig.parameters()));
        };
        return new RasterTileResult(request, block, false, validPixelCount);
    }

    private RasterBlock multiHillshadeBlock(TileContext context, ComputationCache cache, RvtParameters.MultiHillshadeParameters parameters) {
        int bandCount = parameters.directions();
        float[][] bands = new float[bandCount][context.coreSize()];
        for (int i = 0; i < bandCount; i++) {
            double azimuth = (360.0 / bandCount) * i;
            bands[i] = hillshade(context, cache, azimuth, parameters.sunElevationDegrees());
        }
        return finalizeBlock(bands, context.metadata().noDataValue(), context.coreWidth(), context.coreHeight());
    }

    private RasterBlock mstp(TileContext context, RvtParameters.MstpParameters parameters) {
        float[] local = maxElevationDeviation(context, parameters.localScale());
        float[] meso = maxElevationDeviation(context, parameters.mesoScale());
        float[] broad = maxElevationDeviation(context, parameters.broadScale());
        float[] red = new float[context.coreSize()];
        float[] green = new float[context.coreSize()];
        float[] blue = new float[context.coreSize()];
        for (int i = 0; i < context.coreSize(); i++) {
            if (isNoData(local[i], context.metadata().noDataValue())
                    || isNoData(meso[i], context.metadata().noDataValue())
                    || isNoData(broad[i], context.metadata().noDataValue())) {
                red[i] = fillValue(context.metadata().noDataValue());
                green[i] = fillValue(context.metadata().noDataValue());
                blue[i] = fillValue(context.metadata().noDataValue());
                continue;
            }
            red[i] = clamp01(1.0 - Math.exp(-parameters.lightness() * Math.abs(broad[i])));
            green[i] = clamp01(1.0 - Math.exp(-parameters.lightness() * Math.abs(meso[i])));
            blue[i] = clamp01(1.0 - Math.exp(-parameters.lightness() * Math.abs(local[i])));
        }
        return finalizeBlock(new float[][] {red, green, blue}, context.metadata().noDataValue(), context.coreWidth(), context.coreHeight());
    }

    private float[] slopeDegrees(TileContext context, ComputationCache cache) {
        return slopeAspect(context, cache).slopeDegrees();
    }

    private SlopeAspect slopeAspect(TileContext context, ComputationCache cache) {
        if (cache.slopeAspect == null) {
            float[] slopeDegrees = new float[context.coreSize()];
            float[] slopeRadians = new float[context.coreSize()];
            float[] aspectRadians = new float[context.coreSize()];
            for (int localY = 0; localY < context.coreHeight(); localY++) {
                for (int localX = 0; localX < context.coreWidth(); localX++) {
                    int index = index(localX, localY, context.coreWidth());
                    int sourceX = context.coreOffsetX() + localX;
                    int sourceY = context.coreOffsetY() + localY;
                    float center = context.dem()[sourceY][sourceX];
                    if (Float.isNaN(center)) {
                        slopeDegrees[index] = fillValue(context.metadata().noDataValue());
                        slopeRadians[index] = fillValue(context.metadata().noDataValue());
                        aspectRadians[index] = fillValue(context.metadata().noDataValue());
                        continue;
                    }
                    double left = shiftedValue(context, sourceX, sourceY, 0, -1);
                    double right = shiftedValue(context, sourceX, sourceY, 0, 1);
                    double up = shiftedValue(context, sourceX, sourceY, -1, 0);
                    double down = shiftedValue(context, sourceX, sourceY, 1, 0);
                    double dzdx = ((left - right) / 2.0) / context.metadata().resolutionX();
                    double dzdy = ((down - up) / 2.0) / context.metadata().resolutionY();
                    double tanSlope = Math.sqrt((dzdx * dzdx) + (dzdy * dzdy));
                    double slopeRad = Math.atan(tanSlope);
                    if (dzdy == 0.0) {
                        dzdy = 1.0e-8;
                    }
                    double aspectRad = Math.atan2(dzdx, dzdy);
                    slopeRadians[index] = (float) slopeRad;
                    slopeDegrees[index] = (float) Math.toDegrees(slopeRad);
                    aspectRadians[index] = (float) aspectRad;
                }
            }
            cache.slopeAspect = new SlopeAspect(slopeDegrees, slopeRadians, aspectRadians);
        }
        return cache.slopeAspect;
    }

    private float[] hillshade(TileContext context, ComputationCache cache, double sunAzimuthDegrees, double sunElevationDegrees) {
        SlopeAspect slopeAspect = slopeAspect(context, cache);
        float[] output = new float[context.coreSize()];
        double sunAzimuthRad = Math.toRadians(sunAzimuthDegrees);
        double sunZenithRad = PI / 2.0 - Math.toRadians(sunElevationDegrees);
        for (int i = 0; i < output.length; i++) {
            if (isNoData(slopeAspect.slopeRadians()[i], context.metadata().noDataValue())) {
                output[i] = fillValue(context.metadata().noDataValue());
                continue;
            }
            double value = Math.cos(sunZenithRad) * Math.cos(slopeAspect.slopeRadians()[i])
                    + Math.sin(sunZenithRad) * Math.sin(slopeAspect.slopeRadians()[i]) * Math.cos(slopeAspect.aspectRadians()[i] - sunAzimuthRad);
            output[i] = (float) Math.max(0.0, value);
        }
        return output;
    }

    private float[] slrm(TileContext context, RvtParameters.SlrmParameters parameters) {
        IntegralStats stats = new IntegralStats(context.dem());
        float[] output = new float[context.coreSize()];
        for (int localY = 0; localY < context.coreHeight(); localY++) {
            for (int localX = 0; localX < context.coreWidth(); localX++) {
                int index = index(localX, localY, context.coreWidth());
                int sourceX = context.coreOffsetX() + localX;
                int sourceY = context.coreOffsetY() + localY;
                float center = context.dem()[sourceY][sourceX];
                if (Float.isNaN(center)) {
                    output[index] = fillValue(context.metadata().noDataValue());
                    continue;
                }
                double mean = stats.mean(sourceX, sourceY, parameters.radiusCells());
                output[index] = Double.isNaN(mean) ? fillValue(context.metadata().noDataValue()) : (float) (center - mean);
            }
        }
        return output;
    }

    private SkyViewResult skyView(TileContext context, ComputationCache cache, RvtParameters.SkyViewParameters parameters) {
        if (cache.skyView == null || !cache.skyViewParameters.equals(parameters)) {
            cache.skyView = computeSkyView(context, parameters, false);
            cache.skyViewParameters = parameters;
        }
        return cache.skyView;
    }

    private float[] negativeOpenness(TileContext context, ComputationCache cache, RvtParameters.SkyViewParameters parameters) {
        if (cache.negativeSkyView == null || !cache.negativeSkyViewParameters.equals(parameters)) {
            cache.negativeSkyView = computeSkyView(context, parameters, true);
            cache.negativeSkyViewParameters = parameters;
        }
        return cache.negativeSkyView.positiveOpenness();
    }

    private SkyViewResult computeSkyView(TileContext context, RvtParameters.SkyViewParameters parameters, boolean invertDem) {
        float[] svf = new float[context.coreSize()];
        float[] asvf = new float[context.coreSize()];
        float[] openness = new float[context.coreSize()];
        int minRadius = Math.max((int) Math.round(parameters.radiusPixels() * noisePercent(parameters.noiseLevel()) / 100.0), 1);
        int polyLevel = parameters.anisotropyLevel() == 1 ? 4 : 8;
        double minWeight = parameters.anisotropyLevel() == 1 ? 0.4 : 0.1;
        double mainDirectionRad = Math.toRadians(parameters.anisotropyDirectionDegrees());

        for (int localY = 0; localY < context.coreHeight(); localY++) {
            for (int localX = 0; localX < context.coreWidth(); localX++) {
                int index = index(localX, localY, context.coreWidth());
                int sourceX = context.coreOffsetX() + localX;
                int sourceY = context.coreOffsetY() + localY;
                float center = context.dem()[sourceY][sourceX];
                if (Float.isNaN(center)) {
                    svf[index] = fillValue(context.metadata().noDataValue());
                    asvf[index] = fillValue(context.metadata().noDataValue());
                    openness[index] = fillValue(context.metadata().noDataValue());
                    continue;
                }
                double workingCenter = invertDem ? -center : center;
                double svfSum = 0.0;
                double asvfSum = 0.0;
                double weightSum = 0.0;
                double opennessSum = 0.0;
                for (int directionIndex = 0; directionIndex < parameters.directions(); directionIndex++) {
                    double azimuth = (360.0 / parameters.directions()) * directionIndex;
                    double angle = Math.toRadians(azimuth);
                    double dx = Math.sin(angle);
                    double dy = -Math.cos(angle);
                    double maxSlope = -1000.0;
                    for (int radius = minRadius; radius <= parameters.radiusPixels(); radius++) {
                        int sampleX = (int) Math.round(sourceX + (dx * radius));
                        int sampleY = (int) Math.round(sourceY + (dy * radius));
                        if (sampleX < 0 || sampleX >= context.bufferedWidth() || sampleY < 0 || sampleY >= context.bufferedHeight()) {
                            continue;
                        }
                        float sample = context.dem()[sampleY][sampleX];
                        if (Float.isNaN(sample)) {
                            continue;
                        }
                        double workingSample = invertDem ? -sample : sample;
                        double distanceMeters = Math.hypot(dx * radius * context.metadata().resolutionX(), dy * radius * context.metadata().resolutionY());
                        if (distanceMeters <= 0.0) {
                            continue;
                        }
                        maxSlope = Math.max(maxSlope, (workingSample - workingCenter) / distanceMeters);
                    }
                    double maxAngle = Math.atan(maxSlope);
                    svfSum += 1.0 - Math.sin(Math.max(maxAngle, 0.0));
                    double directionAngle = (2.0 * PI / parameters.directions()) * directionIndex;
                    double weight = ((1.0 - minWeight) * Math.pow(Math.cos((directionAngle - mainDirectionRad) / 2.0), polyLevel)) + minWeight;
                    asvfSum += (1.0 - Math.sin(Math.max(maxAngle, 0.0))) * weight;
                    weightSum += weight;
                    opennessSum += maxAngle;
                }
                svf[index] = (float) (svfSum / parameters.directions());
                asvf[index] = (float) (asvfSum / weightSum);
                openness[index] = (float) Math.toDegrees((PI / 2.0) - (opennessSum / parameters.directions()));
            }
        }
        return new SkyViewResult(svf, asvf, openness);
    }

    private float[] skyIllumination(TileContext context, ComputationCache cache, RvtParameters.SkyIlluminationParameters parameters) {
        SlopeAspect slopeAspect = slopeAspect(context, cache);
        float[] uniform = new float[context.coreSize()];
        float[] overcastRaw = new float[context.coreSize()];
        float[] shadow = new float[context.coreSize()];
        double da = PI / parameters.directions();
        int shadowDirection = nearestDirection(parameters.shadowAzimuthDegrees(), parameters.directions());
        for (int localY = 0; localY < context.coreHeight(); localY++) {
            for (int localX = 0; localX < context.coreWidth(); localX++) {
                int index = index(localX, localY, context.coreWidth());
                int sourceX = context.coreOffsetX() + localX;
                int sourceY = context.coreOffsetY() + localY;
                float center = context.dem()[sourceY][sourceX];
                if (Float.isNaN(center) || isNoData(slopeAspect.slopeRadians()[index], context.metadata().noDataValue())) {
                    uniform[index] = fillValue(context.metadata().noDataValue());
                    overcastRaw[index] = fillValue(context.metadata().noDataValue());
                    shadow[index] = fillValue(context.metadata().noDataValue());
                    continue;
                }
                double uniformA = 0.0;
                double uniformB = 0.0;
                double overcastC = 0.0;
                double overcastD = 0.0;
                double horizonForShadow = 0.0;
                for (int directionIndex = 0; directionIndex < parameters.directions(); directionIndex++) {
                    double azimuth = (360.0 / parameters.directions()) * directionIndex;
                    double angle = Math.toRadians(azimuth);
                    double dx = Math.sin(angle);
                    double dy = -Math.cos(angle);
                    double maxSlope = -1000.0;
                    for (int radius = 1; radius <= parameters.shadowDistancePixels(); radius++) {
                        int sampleX = (int) Math.round(sourceX + (dx * radius));
                        int sampleY = (int) Math.round(sourceY + (dy * radius));
                        if (sampleX < 0 || sampleX >= context.bufferedWidth() || sampleY < 0 || sampleY >= context.bufferedHeight()) {
                            continue;
                        }
                        float sample = context.dem()[sampleY][sampleX];
                        if (Float.isNaN(sample)) {
                            continue;
                        }
                        double distanceMeters = Math.hypot(dx * radius * context.metadata().resolutionX(), dy * radius * context.metadata().resolutionY());
                        if (distanceMeters <= 0.0) {
                            continue;
                        }
                        maxSlope = Math.max(maxSlope, Math.max((sample - center) / distanceMeters, 0.0));
                    }
                    double horizon = Math.atan(maxSlope);
                    double dirRad = Math.toRadians(azimuth);
                    double dAspect = -2.0 * Math.sin(da) * Math.cos(dirRad - slopeAspect.aspectRadians()[index]);
                    uniformA += Math.pow(Math.cos(horizon), 2.0);
                    uniformB += Math.max(dAspect * ((PI / 4.0) - (horizon / 2.0) - (Math.sin(2.0 * horizon) / 4.0)), 0.0);
                    double cos3 = Math.pow(Math.cos(horizon), 3.0);
                    overcastC += Math.max(cos3, 0.0);
                    overcastD += Math.max(dAspect * ((2.0 / 3.0) - Math.cos(horizon) + (cos3 / 3.0)), 0.0);
                    if (directionIndex == shadowDirection) {
                        horizonForShadow = Math.toDegrees(horizon);
                    }
                }
                double uniformValue = da * Math.cos(slopeAspect.slopeRadians()[index]) * uniformA
                        + Math.sin(slopeAspect.slopeRadians()[index]) * Math.min(uniformB, PI);
                uniform[index] = (float) (uniformValue / PI);
                overcastRaw[index] = (float) (((2.0 * da / 3.0) * Math.cos(slopeAspect.slopeRadians()[index]) * overcastC)
                        + (Math.sin(slopeAspect.slopeRadians()[index]) * overcastD));
                shadow[index] = horizonForShadow < parameters.shadowElevationDegrees() ? 1.0f : 0.0f;
            }
        }
        if ("uniform".equalsIgnoreCase(parameters.skyModel())) {
            return parameters.computeShadow() ? mix(uniform, shadow, 0.8f, 0.2f, context.metadata().noDataValue()) : uniform;
        }
        float[] overcast = normalizeByMax(overcastRaw, context.metadata().noDataValue());
        float[] mixed = new float[context.coreSize()];
        for (int i = 0; i < mixed.length; i++) {
            if (isNoData(uniform[i], context.metadata().noDataValue()) || isNoData(overcast[i], context.metadata().noDataValue())) {
                mixed[i] = fillValue(context.metadata().noDataValue());
                continue;
            }
            mixed[i] = (float) ((0.33 * uniform[i]) + (0.67 * overcast[i]));
        }
        return parameters.computeShadow() ? mix(mixed, shadow, 0.8f, 0.2f, context.metadata().noDataValue()) : mixed;
    }

    private float[] localDominance(TileContext context, RvtParameters.LocalDominanceParameters parameters) {
        int distanceCount = ((parameters.maximumRadiusPixels() - parameters.minimumRadiusPixels()) / parameters.radiusIncrementPixels()) + 1;
        int angleCount = (359 / parameters.angularResolutionDegrees()) + 1;
        double[] distances = new double[distanceCount];
        for (int i = 0; i < distanceCount; i++) {
            distances[i] = parameters.minimumRadiusPixels() + ((double) i * parameters.radiusIncrementPixels());
        }
        double norma = 0.0;
        for (double distance : distances) {
            norma += (parameters.observerHeight() / distance) * ((2.0 * distance) + parameters.radiusIncrementPixels());
        }
        norma *= angleCount;
        float[] output = new float[context.coreSize()];
        for (int localY = 0; localY < context.coreHeight(); localY++) {
            for (int localX = 0; localX < context.coreWidth(); localX++) {
                int index = index(localX, localY, context.coreWidth());
                int sourceX = context.coreOffsetX() + localX;
                int sourceY = context.coreOffsetY() + localY;
                float center = context.dem()[sourceY][sourceX];
                if (Float.isNaN(center)) {
                    output[index] = fillValue(context.metadata().noDataValue());
                    continue;
                }
                double sum = 0.0;
                for (int angle = 0; angle < angleCount; angle++) {
                    double angleRad = Math.toRadians(angle * parameters.angularResolutionDegrees());
                    double dx = Math.cos(angleRad);
                    double dy = Math.sin(angleRad);
                    for (double distance : distances) {
                        int sampleX = (int) Math.round(sourceX + (dx * distance));
                        int sampleY = (int) Math.round(sourceY + (dy * distance));
                        if (sampleX < 0 || sampleX >= context.bufferedWidth() || sampleY < 0 || sampleY >= context.bufferedHeight()) {
                            continue;
                        }
                        float sample = context.dem()[sampleY][sampleX];
                        if (Float.isNaN(sample)) {
                            continue;
                        }
                        if ((center + parameters.observerHeight()) > sample) {
                            sum += (((center + parameters.observerHeight()) - sample) / distance) * ((2.0 * distance) + parameters.radiusIncrementPixels());
                        }
                    }
                }
                output[index] = (float) (sum / norma);
            }
        }
        return output;
    }

    private float[] msrm(TileContext context, RvtParameters.MsrmParameters parameters) {
        IntegralStats stats = new IntegralStats(context.dem());
        double resolution = averageResolution(context.metadata());
        double featureMin = Math.max(parameters.featureMinimumMeters(), resolution);
        int i = (int) Math.floor(Math.pow((featureMin - resolution) / (2.0 * resolution), 1.0 / parameters.scalingFactor()));
        int n = (int) Math.ceil(Math.pow((parameters.featureMaximumMeters() - resolution) / (2.0 * resolution), 1.0 / parameters.scalingFactor()));
        n = Math.max(n, i + 1);
        float[] sum = new float[context.coreSize()];
        float[] last = null;
        int count = 0;
        for (int ndx = i; ndx <= n; ndx++) {
            int radius = (int) Math.round(Math.pow(Math.max(ndx, 0), parameters.scalingFactor()));
            float[] current = meanAtCore(context, stats, radius);
            if (last != null) {
                for (int iPixel = 0; iPixel < sum.length; iPixel++) {
                    if (isNoData(last[iPixel], context.metadata().noDataValue()) || isNoData(current[iPixel], context.metadata().noDataValue())) {
                        sum[iPixel] = fillValue(context.metadata().noDataValue());
                        continue;
                    }
                    if (!isNoData(sum[iPixel], context.metadata().noDataValue())) {
                        sum[iPixel] += last[iPixel] - current[iPixel];
                    }
                }
                count++;
            }
            last = current;
        }
        if (count == 0) {
            Arrays.fill(sum, 0.0f);
            return sum;
        }
        for (int iPixel = 0; iPixel < sum.length; iPixel++) {
            if (!isNoData(sum[iPixel], context.metadata().noDataValue())) {
                sum[iPixel] /= count;
            }
        }
        return sum;
    }

    private float[] maxElevationDeviation(TileContext context, RvtParameters.MstpParameters.Scale scale) {
        IntegralStats stats = new IntegralStats(context.dem());
        float[] best = new float[context.coreSize()];
        Arrays.fill(best, fillValue(context.metadata().noDataValue()));
        for (int radius = scale.minimumRadius(); radius <= scale.maximumRadius(); radius += scale.step()) {
            for (int localY = 0; localY < context.coreHeight(); localY++) {
                for (int localX = 0; localX < context.coreWidth(); localX++) {
                    int index = index(localX, localY, context.coreWidth());
                    int sourceX = context.coreOffsetX() + localX;
                    int sourceY = context.coreOffsetY() + localY;
                    float center = context.dem()[sourceY][sourceX];
                    if (Float.isNaN(center)) {
                        best[index] = fillValue(context.metadata().noDataValue());
                        continue;
                    }
                    Stats window = stats.stats(sourceX, sourceY, radius);
                    if (window.count() == 0) {
                        best[index] = fillValue(context.metadata().noDataValue());
                        continue;
                    }
                    double std = Math.sqrt(Math.max(0.0, (window.sumSquares() / window.count()) - Math.pow(window.mean(), 2.0)));
                    double dev = (center - window.mean()) / (std + 1.0e-6);
                    if (isNoData(best[index], context.metadata().noDataValue()) || Math.abs(dev) > Math.abs(best[index])) {
                        best[index] = (float) dev;
                    }
                }
            }
        }
        return best;
    }

    private float[] vat(TileContext context, ComputationCache cache, RvtParameters.VatParameters parameters) {
        if (parameters.terrain() == RvtParameters.Terrain.COMBINED) {
            float[] general = vatSingle(context, cache, RvtDefaults.vatPreset(RvtParameters.Terrain.GENERAL));
            float[] flat = vatSingle(context, cache, RvtDefaults.vatPreset(RvtParameters.Terrain.FLAT));
            float alpha = parameters.generalOpacityPercent() / 100.0f;
            float[] combined = new float[context.coreSize()];
            for (int i = 0; i < combined.length; i++) {
                if (isNoData(general[i], context.metadata().noDataValue()) || isNoData(flat[i], context.metadata().noDataValue())) {
                    combined[i] = fillValue(context.metadata().noDataValue());
                    continue;
                }
                combined[i] = (general[i] * alpha) + (flat[i] * (1.0f - alpha));
            }
            return combined;
        }
        return vatSingle(context, cache, RvtDefaults.vatPreset(parameters.terrain()));
    }

    private float[] vatSingle(TileContext context, ComputationCache cache, RvtDefaults.VatTerrainPreset preset) {
        float[] slope = normalizeSingleBand(slopeDegrees(context, cache), context.metadata().noDataValue(), preset.slopeStretch());
        float[] hillshade = normalizeSingleBand(
                hillshade(context, cache, preset.hillshadeSunAzimuth(), preset.hillshadeSunElevation()),
                context.metadata().noDataValue(),
                preset.hillshadeStretch());
        SkyViewResult skyView = skyView(context, cache, new RvtParameters.SkyViewParameters(16, preset.svfRadiusPixels(), preset.svfNoise(), 315, 1));
        float[] svf = normalizeSingleBand(skyView.svf(), context.metadata().noDataValue(), preset.svfStretch());
        float[] positive = normalizeSingleBand(skyView.positiveOpenness(), context.metadata().noDataValue(), preset.positiveOpennessStretch());

        float[] rendered = Arrays.copyOf(hillshade, hillshade.length);
        rendered = applyOpacity(blendLuminosity(slope, rendered, context.metadata().noDataValue()), rendered, 0.50f, context.metadata().noDataValue());
        rendered = applyOpacity(blendOverlay(positive, rendered, context.metadata().noDataValue()), rendered, 0.50f, context.metadata().noDataValue());
        rendered = applyOpacity(blendMultiply(svf, rendered, context.metadata().noDataValue()), rendered, 0.25f, context.metadata().noDataValue());
        return rendered;
    }

    private float[] normalizeSingleBand(float[] values, double noDataValue, RvtDefaults.Stretch stretch) {
        RasterBlock block = RasterBlock.singleBand(1, values.length, noDataValue, Arrays.copyOf(values, values.length));
        float[] normalized = new float[values.length];
        float[] source = block.bands()[0];
        if (stretch.mode() == RvtDefaults.StretchMode.PERCENT) {
            double[] cutoffs = percentCutoffs(source, noDataValue, stretch.minimum(), stretch.maximum());
            scaleValues(source, normalized, noDataValue, cutoffs[0], cutoffs[1]);
        } else {
            scaleValues(source, normalized, noDataValue, stretch.minimum(), stretch.maximum());
        }
        return normalized;
    }

    private double[] percentCutoffs(float[] values, double noDataValue, double lowerPercent, double upperPercent) {
        int count = 0;
        for (float value : values) {
            if (!isNoData(value, noDataValue)) {
                count++;
            }
        }
        if (count == 0) {
            return new double[] {0.0, 1.0};
        }
        double[] collected = new double[count];
        int index = 0;
        for (float value : values) {
            if (!isNoData(value, noDataValue)) {
                collected[index++] = value;
            }
        }
        Arrays.sort(collected);
        return new double[] {
                percentile(collected, lowerPercent),
                percentile(collected, 100.0 - upperPercent)
        };
    }

    private double percentile(double[] values, double percentile) {
        if (values.length == 1) {
            return values[0];
        }
        double pos = (percentile / 100.0) * (values.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) {
            return values[lo];
        }
        double fraction = pos - lo;
        return values[lo] + ((values[hi] - values[lo]) * fraction);
    }

    private void scaleValues(float[] source, float[] target, double noDataValue, double minimum, double maximum) {
        double range = maximum - minimum;
        if (range == 0.0) {
            range = 1.0;
        }
        for (int i = 0; i < source.length; i++) {
            if (isNoData(source[i], noDataValue)) {
                target[i] = fillValue(noDataValue);
                continue;
            }
            double clamped = Math.max(minimum, Math.min(maximum, source[i]));
            target[i] = (float) ((clamped - minimum) / range);
        }
    }

    private float[] blendMultiply(float[] active, float[] background, double noDataValue) {
        float[] output = new float[active.length];
        for (int i = 0; i < output.length; i++) {
            if (isNoData(active[i], noDataValue) || isNoData(background[i], noDataValue)) {
                output[i] = fillValue(noDataValue);
                continue;
            }
            output[i] = active[i] * background[i];
        }
        return output;
    }

    private float[] blendOverlay(float[] active, float[] background, double noDataValue) {
        float[] output = new float[active.length];
        for (int i = 0; i < output.length; i++) {
            if (isNoData(active[i], noDataValue) || isNoData(background[i], noDataValue)) {
                output[i] = fillValue(noDataValue);
                continue;
            }
            float bg = background[i];
            output[i] = bg > 0.5f
                    ? (1.0f - ((1.0f - (2.0f * (bg - 0.5f))) * (1.0f - active[i])))
                    : ((2.0f * bg) * active[i]);
        }
        return output;
    }

    private float[] blendLuminosity(float[] active, float[] background, double noDataValue) {
        float[] output = new float[active.length];
        for (int i = 0; i < output.length; i++) {
            if (isNoData(active[i], noDataValue) || isNoData(background[i], noDataValue)) {
                output[i] = fillValue(noDataValue);
                continue;
            }
            output[i] = active[i];
        }
        return output;
    }

    private float[] applyOpacity(float[] active, float[] background, float opacity, double noDataValue) {
        float[] output = new float[active.length];
        for (int i = 0; i < output.length; i++) {
            if (isNoData(active[i], noDataValue) || isNoData(background[i], noDataValue)) {
                output[i] = fillValue(noDataValue);
                continue;
            }
            output[i] = (active[i] * opacity) + (background[i] * (1.0f - opacity));
        }
        return output;
    }

    private float[] mix(float[] a, float[] b, float aWeight, float bWeight, double noDataValue) {
        float[] output = new float[a.length];
        for (int i = 0; i < output.length; i++) {
            if (isNoData(a[i], noDataValue) || isNoData(b[i], noDataValue)) {
                output[i] = fillValue(noDataValue);
                continue;
            }
            output[i] = (a[i] * aWeight) + (b[i] * bWeight);
        }
        return output;
    }

    private float[] normalizeByMax(float[] values, double noDataValue) {
        float max = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            if (!isNoData(value, noDataValue)) {
                max = Math.max(max, value);
            }
        }
        if (!Float.isFinite(max) || max == 0.0f) {
            return Arrays.copyOf(values, values.length);
        }
        float[] output = new float[values.length];
        for (int i = 0; i < output.length; i++) {
            if (isNoData(values[i], noDataValue)) {
                output[i] = fillValue(noDataValue);
                continue;
            }
            output[i] = values[i] / max;
        }
        return output;
    }

    private float[] meanAtCore(TileContext context, IntegralStats stats, int radius) {
        float[] mean = new float[context.coreSize()];
        for (int localY = 0; localY < context.coreHeight(); localY++) {
            for (int localX = 0; localX < context.coreWidth(); localX++) {
                int index = index(localX, localY, context.coreWidth());
                int sourceX = context.coreOffsetX() + localX;
                int sourceY = context.coreOffsetY() + localY;
                double value = stats.mean(sourceX, sourceY, radius);
                mean[index] = Double.isNaN(value) ? fillValue(context.metadata().noDataValue()) : (float) value;
            }
        }
        return mean;
    }

    private RasterBlock singleBandBlock(int width, int height, double noDataValue, float[] band) {
        return finalizeBlock(new float[][] {band}, noDataValue, width, height);
    }

    private RasterBlock finalizeBlock(float[][] bands, double noDataValue, int width, int height) {
        float fillValue = fillValue(noDataValue);
        for (float[] band : bands) {
            for (int i = 0; i < band.length; i++) {
                if (Float.isNaN(band[i])) {
                    band[i] = fillValue;
                }
            }
        }
        return new RasterBlock(width, height, bands.length, noDataValue, bands);
    }

    private RasterBlock filledBlock(int width, int height, int bandCount, double noDataValue) {
        float[][] bands = new float[bandCount][width * height];
        for (float[] band : bands) {
            Arrays.fill(band, fillValue(noDataValue));
        }
        return new RasterBlock(width, height, bandCount, noDataValue, bands);
    }

    private float[][] toMatrix(float[] values, int width, int height, double noDataValue) {
        float[][] dem = new float[height][width];
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                float value = values[rowOffset + x];
                dem[y][x] = NoData.isValid(value, noDataValue) ? value * 1.0f : Float.NaN;
            }
        }
        return dem;
    }

    private int countValidCore(float[][] dem, TileWindow window, double noDataValue) {
        int count = 0;
        for (int y = 0; y < window.coreWindow().height(); y++) {
            for (int x = 0; x < window.coreWindow().width(); x++) {
                float value = dem[window.coreOffsetY() + y][window.coreOffsetX() + x];
                if (!isNoData(value, noDataValue)) {
                    count++;
                }
            }
        }
        return count;
    }

    private double shiftedValue(TileContext context, int sourceX, int sourceY, int shiftY, int shiftX) {
        int y = clamp(sourceY + shiftY, 0, context.bufferedHeight() - 1);
        int x = clamp(sourceX + shiftX, 0, context.bufferedWidth() - 1);
        float shifted = context.dem()[y][x];
        float center = context.dem()[sourceY][sourceX];
        if (Float.isNaN(shifted) && !Float.isNaN(center)) {
            return center * context.runConfig().exaggeration();
        }
        return (Float.isNaN(shifted) ? center : shifted) * context.runConfig().exaggeration();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int index(int x, int y, int width) {
        return (y * width) + x;
    }

    private static float fillValue(double noDataValue) {
        return Double.isNaN(noDataValue) ? Float.NaN : (float) noDataValue;
    }

    private static boolean isNoData(float value, double noDataValue) {
        return NoData.isNoData(value, noDataValue) || Float.isNaN(value);
    }

    private static int nearestDirection(double azimuth, int directions) {
        double step = 360.0 / directions;
        return ((int) Math.round(azimuth / step)) % directions;
    }

    private static double noisePercent(int noiseLevel) {
        return switch (noiseLevel) {
            case 0 -> 0.0;
            case 1 -> 10.0;
            case 2 -> 20.0;
            case 3 -> 40.0;
            default -> throw new IllegalArgumentException("Unsupported noise level: " + noiseLevel);
        };
    }

    private static double averageResolution(RasterMetadata metadata) {
        return (metadata.resolutionX() + metadata.resolutionY()) / 2.0;
    }

    private static float clamp01(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }

    private record TileContext(
            float[][] dem,
            RasterMetadata metadata,
            TileRequest request,
            RvtRunConfig runConfig) {
        private int coreWidth() {
            return request.window().coreWindow().width();
        }

        private int coreHeight() {
            return request.window().coreWindow().height();
        }

        private int coreOffsetX() {
            return request.window().coreOffsetX();
        }

        private int coreOffsetY() {
            return request.window().coreOffsetY();
        }

        private int bufferedWidth() {
            return request.window().bufferedWindow().width();
        }

        private int bufferedHeight() {
            return request.window().bufferedWindow().height();
        }

        private int coreSize() {
            return coreWidth() * coreHeight();
        }
    }

    private static final class ComputationCache {
        private SlopeAspect slopeAspect;
        private SkyViewResult skyView;
        private RvtParameters.SkyViewParameters skyViewParameters;
        private SkyViewResult negativeSkyView;
        private RvtParameters.SkyViewParameters negativeSkyViewParameters;
    }

    private record SlopeAspect(float[] slopeDegrees, float[] slopeRadians, float[] aspectRadians) {
    }

    private record SkyViewResult(float[] svf, float[] asvf, float[] positiveOpenness) {
    }

    private static final class IntegralStats {
        private final double[][] sum;
        private final double[][] sumSquares;
        private final int[][] count;
        private final int width;
        private final int height;

        private IntegralStats(float[][] dem) {
            this.height = dem.length;
            this.width = dem[0].length;
            this.sum = new double[height + 1][width + 1];
            this.sumSquares = new double[height + 1][width + 1];
            this.count = new int[height + 1][width + 1];
            for (int y = 0; y < height; y++) {
                double rowSum = 0.0;
                double rowSumSquares = 0.0;
                int rowCount = 0;
                for (int x = 0; x < width; x++) {
                    float value = dem[y][x];
                    if (!Float.isNaN(value)) {
                        rowSum += value;
                        rowSumSquares += value * value;
                        rowCount++;
                    }
                    sum[y + 1][x + 1] = sum[y][x + 1] + rowSum;
                    sumSquares[y + 1][x + 1] = sumSquares[y][x + 1] + rowSumSquares;
                    count[y + 1][x + 1] = count[y][x + 1] + rowCount;
                }
            }
        }

        private double mean(int centerX, int centerY, int radius) {
            Stats stats = stats(centerX, centerY, radius);
            return stats.count() == 0 ? Double.NaN : stats.mean();
        }

        private Stats stats(int centerX, int centerY, int radius) {
            int x0 = Math.max(0, centerX - radius);
            int y0 = Math.max(0, centerY - radius);
            int x1 = Math.min(width - 1, centerX + radius);
            int y1 = Math.min(height - 1, centerY + radius);
            int left = x0;
            int top = y0;
            int right = x1 + 1;
            int bottom = y1 + 1;
            int pixelCount = count[bottom][right] - count[top][right] - count[bottom][left] + count[top][left];
            double windowSum = sum[bottom][right] - sum[top][right] - sum[bottom][left] + sum[top][left];
            double windowSquares = sumSquares[bottom][right] - sumSquares[top][right] - sumSquares[bottom][left] + sumSquares[top][left];
            double mean = pixelCount == 0 ? Double.NaN : windowSum / pixelCount;
            return new Stats(pixelCount, windowSum, windowSquares, mean);
        }
    }

    private record Stats(int count, double sum, double sumSquares, double mean) {
    }
}
