package ch.so.agi.terrainvis.render;

import java.util.List;

import ch.so.agi.terrainvis.output.RasterBlock;
import ch.so.agi.terrainvis.output.RasterTileResult;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.util.NoData;

public final class RenderComposer {
    public RasterTileResult composeTile(TileRequest tileRequest, List<LayerTile> layers, boolean withAlpha) {
        int width = tileRequest.window().coreWindow().width();
        int height = tileRequest.window().coreWindow().height();
        int pixelCount = width * height;
        float[] red = new float[pixelCount];
        float[] green = new float[pixelCount];
        float[] blue = new float[pixelCount];
        float[] alpha = withAlpha ? new float[pixelCount] : null;
        int validPixelCount = 0;
        RenderRamp.Sample sample = new RenderRamp.Sample();

        for (int i = 0; i < pixelCount; i++) {
            float dstRed = 0.0f;
            float dstGreen = 0.0f;
            float dstBlue = 0.0f;
            float dstAlpha = 0.0f;
            for (LayerTile layer : layers) {
                float value = layer.values()[i];
                if (NoData.isNoData(value, layer.valueNoData())) {
                    continue;
                }
                layer.ramp().sample(value, sample);
                float srcRed = sample.red();
                float srcGreen = sample.green();
                float srcBlue = sample.blue();
                float srcAlpha = sample.alpha() * (float) layer.spec().opacity();
                if (layer.alphaValues() != null) {
                    float alphaValue = layer.alphaValues()[i];
                    if (NoData.isNoData(alphaValue, layer.alphaNoData())) {
                        srcAlpha = 0.0f;
                    } else {
                        srcAlpha *= clamp01(alphaValue);
                    }
                }
                if (srcAlpha <= 0.0f) {
                    continue;
                }

                float blendedRed = srcRed;
                float blendedGreen = srcGreen;
                float blendedBlue = srcBlue;
                if (layer.spec().blendMode() == BlendMode.MULTIPLY) {
                    blendedRed *= dstRed;
                    blendedGreen *= dstGreen;
                    blendedBlue *= dstBlue;
                }

                float outAlpha = srcAlpha + (dstAlpha * (1.0f - srcAlpha));
                if (outAlpha <= 0.0f) {
                    dstRed = 0.0f;
                    dstGreen = 0.0f;
                    dstBlue = 0.0f;
                    dstAlpha = 0.0f;
                    continue;
                }
                dstRed = (((blendedRed * srcAlpha) + (dstRed * dstAlpha * (1.0f - srcAlpha))) / outAlpha);
                dstGreen = (((blendedGreen * srcAlpha) + (dstGreen * dstAlpha * (1.0f - srcAlpha))) / outAlpha);
                dstBlue = (((blendedBlue * srcAlpha) + (dstBlue * dstAlpha * (1.0f - srcAlpha))) / outAlpha);
                dstAlpha = outAlpha;
            }
            red[i] = clamp01(dstRed);
            green[i] = clamp01(dstGreen);
            blue[i] = clamp01(dstBlue);
            if (withAlpha) {
                alpha[i] = clamp01(dstAlpha);
            }
            if (dstAlpha > 0.0f) {
                validPixelCount++;
            }
        }

        float[][] bands = withAlpha
                ? new float[][] {red, green, blue, alpha}
                : new float[][] {red, green, blue};
        return new RasterTileResult(
                tileRequest,
                new RasterBlock(width, height, bands.length, Double.NaN, bands),
                validPixelCount == 0,
                validPixelCount);
    }

    public record LayerTile(
            RenderLayerSpec spec,
            RenderRamp ramp,
            float[] values,
            double valueNoData,
            float[] alphaValues,
            double alphaNoData) {
        public LayerTile {
            if (spec == null || ramp == null || values == null) {
                throw new IllegalArgumentException("spec, ramp and values are required");
            }
            if (alphaValues != null && alphaValues.length != values.length) {
                throw new IllegalArgumentException("alphaValues length must match values length");
            }
        }
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
