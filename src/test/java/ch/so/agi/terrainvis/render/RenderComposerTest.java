package ch.so.agi.terrainvis.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.so.agi.terrainvis.output.RasterTileResult;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.tiling.TileWindow;

class RenderComposerTest {
    private final RenderComposer composer = new RenderComposer();

    @Test
    void grayscaleMapsZeroHalfAndOneToBlackGrayAndWhite() {
        RasterTileResult result = composer.composeTile(
                tileRequest(3, 1),
                List.of(layer(
                        new RenderLayerSpec("input.tif", null, 0.0, 1.0, color(0, 0, 0), color(255, 255, 255), BlendMode.NORMAL, 1.0),
                        new float[] {0.0f, 0.5f, 1.0f},
                        null,
                        Double.NaN,
                        Double.NaN)),
                false);

        assertThat(result.skipped()).isFalse();
        assertThat(result.validPixelCount()).isEqualTo(3);
        assertThat(result.block().bands()[0]).containsExactly(0.0f, 0.5f, 1.0f);
        assertThat(result.block().bands()[1]).containsExactly(0.0f, 0.5f, 1.0f);
        assertThat(result.block().bands()[2]).containsExactly(0.0f, 0.5f, 1.0f);
    }

    @Test
    void colorRampUsesConfiguredEndColors() {
        RasterTileResult result = composer.composeTile(
                tileRequest(2, 1),
                List.of(layer(
                        new RenderLayerSpec("input.tif", null, 0.0, 1.0, color(170, 255, 170), color(0, 85, 0), BlendMode.NORMAL, 1.0),
                        new float[] {0.0f, 1.0f},
                        null,
                        Double.NaN,
                        Double.NaN)),
                false);

        assertThat(result.block().bands()[0]).containsExactly(170.0f / 255.0f, 0.0f);
        assertThat(result.block().bands()[1]).containsExactly(1.0f, 85.0f / 255.0f);
        assertThat(result.block().bands()[2]).containsExactly(170.0f / 255.0f, 0.0f);
    }

    @Test
    void nodataAndSeparateAlphaProduceTransparentPixels() {
        RasterTileResult result = composer.composeTile(
                tileRequest(3, 1),
                List.of(layer(
                        new RenderLayerSpec("input.tif", "alpha.tif", 0.0, 1.0, color(0, 0, 0), color(255, 255, 255), BlendMode.NORMAL, 1.0),
                        new float[] {0.25f, Float.NaN, 1.0f},
                        new float[] {0.0f, 1.0f, 0.5f},
                        Double.NaN,
                        Double.NaN)),
                true);

        assertThat(result.validPixelCount()).isEqualTo(1);
        assertThat(result.block().bands()[3]).containsExactly(0.0f, 0.0f, 0.5f);
        assertThat(result.block().bands()[0]).containsExactly(0.0f, 0.0f, 1.0f);
        assertThat(result.block().bands()[1]).containsExactly(0.0f, 0.0f, 1.0f);
        assertThat(result.block().bands()[2]).containsExactly(0.0f, 0.0f, 1.0f);
    }

    @Test
    void normalBlendRespectsLayerOrderAndOpacity() {
        RasterTileResult result = composer.composeTile(
                tileRequest(1, 1),
                List.of(
                        layer(new RenderLayerSpec("base.tif", null, 0.0, 1.0, color(255, 0, 0), color(255, 0, 0), BlendMode.NORMAL, 1.0),
                                new float[] {1.0f},
                                null,
                                Double.NaN,
                                Double.NaN),
                        layer(new RenderLayerSpec("overlay.tif", null, 0.0, 1.0, color(0, 0, 255), color(0, 0, 255), BlendMode.NORMAL, 0.5),
                                new float[] {1.0f},
                                null,
                                Double.NaN,
                                Double.NaN)),
                false);

        assertThat(result.block().bands()[0][0]).isCloseTo(0.5f, offset(1.0e-6f));
        assertThat(result.block().bands()[1][0]).isZero();
        assertThat(result.block().bands()[2][0]).isCloseTo(0.5f, offset(1.0e-6f));
    }

    @Test
    void multiplyBlendUsesDestinationColorBeforeCompositing() {
        RasterTileResult result = composer.composeTile(
                tileRequest(1, 1),
                List.of(
                        layer(new RenderLayerSpec("base.tif", null, 0.0, 1.0, color(255, 0, 0), color(255, 0, 0), BlendMode.NORMAL, 1.0),
                                new float[] {1.0f},
                                null,
                                Double.NaN,
                                Double.NaN),
                        layer(new RenderLayerSpec("overlay.tif", null, 0.0, 1.0, color(0, 0, 255), color(0, 0, 255), BlendMode.MULTIPLY, 1.0),
                                new float[] {1.0f},
                                null,
                                Double.NaN,
                                Double.NaN)),
                false);

        assertThat(result.block().bands()[0][0]).isZero();
        assertThat(result.block().bands()[1][0]).isZero();
        assertThat(result.block().bands()[2][0]).isZero();
    }

    @Test
    void screenBlendUsesSrgbCorrectedFormula() {
        float dst = 51.0f / 255.0f;
        float src = 179.0f / 255.0f;
        float expected = blendScreen(src, dst);

        RasterTileResult result = composer.composeTile(
                tileRequest(1, 1),
                List.of(
                        layer(new RenderLayerSpec("base.tif", null, 0.0, 1.0, color(51, 51, 51), color(51, 51, 51), BlendMode.NORMAL, 1.0),
                                new float[] {1.0f},
                                null,
                                Double.NaN,
                                Double.NaN),
                        layer(new RenderLayerSpec("overlay.tif", null, 0.0, 1.0, color(179, 179, 179), color(179, 179, 179), BlendMode.SCREEN, 1.0),
                                new float[] {1.0f},
                                null,
                                Double.NaN,
                                Double.NaN)),
                false);

        assertThat(result.block().bands()[0][0]).isCloseTo(expected, offset(1.0e-6f));
        assertThat(result.block().bands()[1][0]).isCloseTo(expected, offset(1.0e-6f));
        assertThat(result.block().bands()[2][0]).isCloseTo(expected, offset(1.0e-6f));
    }

    @Test
    void softLightBlendUsesSrgbCorrectedFormula() {
        float dst = 51.0f / 255.0f;
        float src = 179.0f / 255.0f;
        float expected = blendSoftLight(src, dst);

        RasterTileResult result = composer.composeTile(
                tileRequest(1, 1),
                List.of(
                        layer(new RenderLayerSpec("base.tif", null, 0.0, 1.0, color(51, 51, 51), color(51, 51, 51), BlendMode.NORMAL, 1.0),
                                new float[] {1.0f},
                                null,
                                Double.NaN,
                                Double.NaN),
                        layer(new RenderLayerSpec("overlay.tif", null, 0.0, 1.0, color(179, 179, 179), color(179, 179, 179), BlendMode.SOFTLIGHT, 1.0),
                                new float[] {1.0f},
                                null,
                                Double.NaN,
                                Double.NaN)),
                false);

        assertThat(result.block().bands()[0][0]).isCloseTo(expected, offset(1.0e-6f));
        assertThat(result.block().bands()[1][0]).isCloseTo(expected, offset(1.0e-6f));
        assertThat(result.block().bands()[2][0]).isCloseTo(expected, offset(1.0e-6f));
    }

    @Test
    void multiStopRampInterpolatesPerSegmentAndClampsToEndStops() {
        RasterTileResult result = composer.composeTile(
                tileRequest(5, 1),
                List.of(layer(
                        new RenderLayerSpec(
                                "input.tif",
                                null,
                                List.of(
                                        new RenderRampStop(0.0, color(0, 0, 255), 1.0),
                                        new RenderRampStop(10.0, color(255, 0, 0), 1.0),
                                        new RenderRampStop(20.0, color(255, 255, 0), 1.0)),
                                BlendMode.NORMAL,
                                1.0),
                        new float[] {-5.0f, 5.0f, 10.0f, 15.0f, 25.0f},
                        null,
                        Double.NaN,
                        Double.NaN)),
                false);

        assertThat(result.block().bands()[0]).containsExactly(0.0f, 0.5f, 1.0f, 1.0f, 1.0f);
        assertThat(result.block().bands()[1]).containsExactly(0.0f, 0.0f, 0.0f, 0.5f, 1.0f);
        assertThat(result.block().bands()[2]).containsExactly(1.0f, 0.5f, 0.0f, 0.0f, 0.0f);
    }

    @Test
    void stopAlphaCombinesWithOpacityAndSeparateAlphaRaster() {
        RasterTileResult result = composer.composeTile(
                tileRequest(2, 1),
                List.of(layer(
                        new RenderLayerSpec(
                                "input.tif",
                                "alpha.tif",
                                List.of(
                                        new RenderRampStop(0.0, color(255, 255, 255), 0.0),
                                        new RenderRampStop(1.0, color(0, 68, 27), 1.0)),
                                BlendMode.NORMAL,
                                0.5),
                        new float[] {0.0f, 1.0f},
                        new float[] {1.0f, 0.5f},
                        Double.NaN,
                        Double.NaN)),
                true);

        assertThat(result.validPixelCount()).isEqualTo(1);
        assertThat(result.block().bands()[0]).containsExactly(0.0f, 0.0f);
        assertThat(result.block().bands()[1]).containsExactly(0.0f, 68.0f / 255.0f);
        assertThat(result.block().bands()[2]).containsExactly(0.0f, 27.0f / 255.0f);
        assertThat(result.block().bands()[3]).containsExactly(0.0f, 0.25f);
    }

    private RenderComposer.LayerTile layer(
            RenderLayerSpec spec,
            float[] values,
            float[] alpha,
            double valueNoData,
            double alphaNoData) {
        return new RenderComposer.LayerTile(spec, RenderRamp.fromSpec(spec), values, valueNoData, alpha, alphaNoData);
    }

    private TileRequest tileRequest(int width, int height) {
        PixelWindow window = new PixelWindow(0, 0, width, height);
        return new TileRequest(0, 0, 0, new TileWindow(window, window, 0, 0));
    }

    private RenderColor color(int red, int green, int blue) {
        return new RenderColor(red, green, blue);
    }

    private float blendScreen(float src, float dst) {
        float s = srgbToLinear(src);
        float b = srgbToLinear(dst);
        float out = 1.0f - ((1.0f - s) * (1.0f - b));
        return linearToSrgb(out);
    }

    private float blendSoftLight(float src, float dst) {
        float s = srgbToLinear(src);
        float b = srgbToLinear(dst);
        float out;
        if (s <= 0.5f) {
            out = b - ((1.0f - (2.0f * s)) * b * (1.0f - b));
        } else {
            float d = b <= 0.25f
                    ? (((16.0f * b - 12.0f) * b + 4.0f) * b)
                    : (float) Math.sqrt(b);
            out = b + ((2.0f * s - 1.0f) * (d - b));
        }
        return linearToSrgb(out);
    }

    private float srgbToLinear(float value) {
        float c = clamp01(value);
        if (c <= 0.04045f) {
            return c / 12.92f;
        }
        return (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    private float linearToSrgb(float value) {
        float c = clamp01(value);
        if (c <= 0.0031308f) {
            return 12.92f * c;
        }
        return (float) (1.055f * Math.pow(c, 1.0f / 2.4f) - 0.055f);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
