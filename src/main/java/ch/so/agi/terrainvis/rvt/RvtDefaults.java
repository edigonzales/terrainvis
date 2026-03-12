package ch.so.agi.terrainvis.rvt;

import java.util.EnumMap;
import java.util.Map;

import ch.so.agi.terrainvis.output.RasterBlock;
import ch.so.agi.terrainvis.raster.RasterMetadata;

public final class RvtDefaults {
    private static final Map<RvtProduct, Stretch> DEFAULT_STRETCHES = new EnumMap<>(RvtProduct.class);

    static {
        DEFAULT_STRETCHES.put(RvtProduct.SLOPE, new Stretch(StretchMode.VALUE, 0.0, 51.0));
        DEFAULT_STRETCHES.put(RvtProduct.HILLSHADE, new Stretch(StretchMode.VALUE, 0.0, 1.0));
        DEFAULT_STRETCHES.put(RvtProduct.MULTI_HILLSHADE, new Stretch(StretchMode.VALUE, 0.0, 1.0));
        DEFAULT_STRETCHES.put(RvtProduct.SLRM, new Stretch(StretchMode.VALUE, -2.0, 2.0));
        DEFAULT_STRETCHES.put(RvtProduct.SVF, new Stretch(StretchMode.VALUE, 0.6375, 1.0));
        DEFAULT_STRETCHES.put(RvtProduct.ASVF, new Stretch(StretchMode.VALUE, 0.70, 0.90));
        DEFAULT_STRETCHES.put(RvtProduct.POSITIVE_OPENNESS, new Stretch(StretchMode.VALUE, 60.0, 95.0));
        DEFAULT_STRETCHES.put(RvtProduct.NEGATIVE_OPENNESS, new Stretch(StretchMode.VALUE, 60.0, 95.0));
        DEFAULT_STRETCHES.put(RvtProduct.SKY_ILLUMINATION, new Stretch(StretchMode.PERCENT, 0.25, 0.0));
        DEFAULT_STRETCHES.put(RvtProduct.LOCAL_DOMINANCE, new Stretch(StretchMode.VALUE, 0.5, 1.8));
        DEFAULT_STRETCHES.put(RvtProduct.MSRM, new Stretch(StretchMode.VALUE, -2.5, 2.5));
        DEFAULT_STRETCHES.put(RvtProduct.MSTP, new Stretch(StretchMode.VALUE, 0.0, 1.0));
        DEFAULT_STRETCHES.put(RvtProduct.VAT, new Stretch(StretchMode.VALUE, 0.0, 1.0));
    }

    private RvtDefaults() {
    }

    public static Stretch defaultStretch(RvtProduct product) {
        return DEFAULT_STRETCHES.get(product);
    }

    public static int bandCount(RvtProduct product, RvtParameters parameters) {
        if (product == RvtProduct.MULTI_HILLSHADE) {
            return ((RvtParameters.MultiHillshadeParameters) parameters).directions();
        }
        if (product == RvtProduct.MSTP) {
            return 3;
        }
        return 1;
    }

    public static int requiredBufferPixels(RvtRunConfig runConfig, RasterMetadata metadata) {
        int required = switch (runConfig.product()) {
            case SLOPE, HILLSHADE, MULTI_HILLSHADE -> 1;
            case SLRM -> ((RvtParameters.SlrmParameters) runConfig.parameters()).radiusCells();
            case SVF, ASVF, POSITIVE_OPENNESS, NEGATIVE_OPENNESS ->
                    ((RvtParameters.SkyViewParameters) runConfig.parameters()).radiusPixels();
            case SKY_ILLUMINATION -> ((RvtParameters.SkyIlluminationParameters) runConfig.parameters()).shadowDistancePixels();
            case LOCAL_DOMINANCE -> ((RvtParameters.LocalDominanceParameters) runConfig.parameters()).maximumRadiusPixels();
            case MSRM -> Math.max(
                    metadata.bufferPixelsX(((RvtParameters.MsrmParameters) runConfig.parameters()).featureMaximumMeters()),
                    metadata.bufferPixelsY(((RvtParameters.MsrmParameters) runConfig.parameters()).featureMaximumMeters()));
            case MSTP -> ((RvtParameters.MstpParameters) runConfig.parameters()).broadScale().maximumRadius();
            case VAT -> requiredBufferPixelsForVat((RvtParameters.VatParameters) runConfig.parameters());
        };
        if (runConfig.commonConfig().tilingConfig().bufferPixelsOverride() != null) {
            required = Math.max(required, runConfig.commonConfig().tilingConfig().bufferPixelsOverride());
        }
        if (runConfig.commonConfig().tilingConfig().bufferMetersOverride() != null) {
            required = Math.max(required, Math.max(
                    metadata.bufferPixelsX(runConfig.commonConfig().tilingConfig().bufferMetersOverride()),
                    metadata.bufferPixelsY(runConfig.commonConfig().tilingConfig().bufferMetersOverride())));
        }
        return required;
    }

    public static RasterBlock normalizeForByte(RvtProduct product, RasterBlock block) {
        Stretch stretch = defaultStretch(product);
        float[][] normalized = new float[block.bandCount()][block.width() * block.height()];
        for (int band = 0; band < block.bandCount(); band++) {
            normalizeBand(block.bands()[band], normalized[band], block.noDataValue(), stretch);
        }
        return new RasterBlock(block.width(), block.height(), block.bandCount(), 0.0, normalized);
    }

    public static VatTerrainPreset vatPreset(RvtParameters.Terrain terrain) {
        return switch (terrain) {
            case GENERAL -> new VatTerrainPreset(
                    315.0,
                    35.0,
                    new Stretch(StretchMode.VALUE, 0.0, 50.0),
                    new Stretch(StretchMode.VALUE, 0.0, 1.0),
                    new Stretch(StretchMode.VALUE, 0.7, 1.0),
                    10,
                    0,
                    new Stretch(StretchMode.VALUE, 68.0, 93.0));
            case FLAT -> new VatTerrainPreset(
                    315.0,
                    15.0,
                    new Stretch(StretchMode.VALUE, 0.0, 15.0),
                    new Stretch(StretchMode.VALUE, 0.0, 1.0),
                    new Stretch(StretchMode.VALUE, 0.9, 1.0),
                    20,
                    3,
                    new Stretch(StretchMode.VALUE, 85.0, 93.0));
            case COMBINED -> throw new IllegalArgumentException("Combined VAT does not have a direct terrain preset");
        };
    }

    private static int requiredBufferPixelsForVat(RvtParameters.VatParameters parameters) {
        if (parameters.terrain() == RvtParameters.Terrain.COMBINED) {
            return Math.max(vatPreset(RvtParameters.Terrain.GENERAL).svfRadiusPixels(), vatPreset(RvtParameters.Terrain.FLAT).svfRadiusPixels());
        }
        return vatPreset(parameters.terrain()).svfRadiusPixels();
    }

    private static void normalizeBand(float[] source, float[] target, double noDataValue, Stretch stretch) {
        if (stretch.mode() == StretchMode.PERCENT) {
            double[] cutoffs = percentileCutoffs(source, noDataValue, stretch.minimum(), stretch.maximum());
            normalizeBandValue(source, target, noDataValue, cutoffs[0], cutoffs[1]);
            return;
        }
        normalizeBandValue(source, target, noDataValue, stretch.minimum(), stretch.maximum());
    }

    private static void normalizeBandValue(float[] source, float[] target, double noDataValue, double minimum, double maximum) {
        double range = maximum - minimum;
        if (range == 0.0) {
            range = 1.0;
        }
        for (int i = 0; i < source.length; i++) {
            float value = source[i];
            if (Float.isNaN(value) || nearlyEquals(value, noDataValue)) {
                target[i] = 0.0f;
                continue;
            }
            double clamped = Math.max(minimum, Math.min(maximum, value));
            target[i] = (float) ((clamped - minimum) / range);
        }
    }

    private static double[] percentileCutoffs(float[] source, double noDataValue, double minimumPercent, double maximumPercent) {
        int validCount = 0;
        for (float value : source) {
            if (!Float.isNaN(value) && !nearlyEquals(value, noDataValue)) {
                validCount++;
            }
        }
        if (validCount == 0) {
            return new double[] {0.0, 1.0};
        }
        double[] values = new double[validCount];
        int index = 0;
        for (float value : source) {
            if (!Float.isNaN(value) && !nearlyEquals(value, noDataValue)) {
                values[index++] = value;
            }
        }
        java.util.Arrays.sort(values);
        double lower = percentile(values, minimumPercent);
        double upper = percentile(values, 100.0 - maximumPercent);
        if (lower == upper) {
            lower = values[0];
            upper = values[values.length - 1];
        }
        return new double[] {lower, upper};
    }

    private static double percentile(double[] values, double percentile) {
        if (values.length == 1) {
            return values[0];
        }
        double position = percentile / 100.0 * (values.length - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        if (lowerIndex == upperIndex) {
            return values[lowerIndex];
        }
        double fraction = position - lowerIndex;
        return values[lowerIndex] + ((values[upperIndex] - values[lowerIndex]) * fraction);
    }

    private static boolean nearlyEquals(double value, double noDataValue) {
        if (Double.isNaN(noDataValue)) {
            return Double.isNaN(value);
        }
        return Math.abs(value - noDataValue) <= 1.0e-6;
    }

    public enum StretchMode {
        VALUE,
        PERCENT
    }

    public record Stretch(StretchMode mode, double minimum, double maximum) {
    }

    public record VatTerrainPreset(
            double hillshadeSunAzimuth,
            double hillshadeSunElevation,
            Stretch slopeStretch,
            Stretch hillshadeStretch,
            Stretch svfStretch,
            int svfRadiusPixels,
            int svfNoise,
            Stretch positiveOpennessStretch) {
    }
}
