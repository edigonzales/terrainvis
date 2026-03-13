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
}
