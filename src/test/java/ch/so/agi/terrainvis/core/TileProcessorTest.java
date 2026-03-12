package ch.so.agi.terrainvis.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;

import ch.so.agi.terrainvis.config.LightingParameters;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.tiling.TileWindow;

class TileProcessorTest {
    @Test
    void skipsTileWhenCoreContainsOnlyNoData() throws Exception {
        RasterMetadata metadata = new RasterMetadata(
                4,
                2,
                new ReferencedEnvelope(0.0, 4.0, 0.0, 2.0, CRS.decode("EPSG:2056", true)),
                CRS.decode("EPSG:2056", true),
                1.0,
                1.0,
                -9999.0,
                1,
                "test");
        TileRequest request = new TileRequest(
                0,
                0,
                0,
                new TileWindow(new PixelWindow(0, 0, 2, 2), new PixelWindow(0, 0, 4, 2), 0, 0));
        float[] bufferedValues = new float[] {
                -9999.0f, -9999.0f, 5.0f, 5.0f,
                -9999.0f, -9999.0f, 5.0f, 5.0f
        };

        TileComputationResult result = new TileProcessor().process(
                request,
                metadata,
                bufferedValues,
                LightingParameters.defaults(),
                1.0,
                1);

        assertThat(result.skipped()).isTrue();
        assertThat(result.validPixelCount()).isZero();
    }

    @Test
    void producesSameResultWithSingleAndMultiThreadTileTracing() throws Exception {
        RasterMetadata metadata = new RasterMetadata(
                4,
                4,
                new ReferencedEnvelope(0.0, 4.0, 0.0, 4.0, CRS.decode("EPSG:2056", true)),
                CRS.decode("EPSG:2056", true),
                1.0,
                1.0,
                -9999.0,
                1,
                "test");
        TileRequest request = new TileRequest(
                0,
                0,
                0,
                new TileWindow(new PixelWindow(0, 0, 4, 4), new PixelWindow(0, 0, 4, 4), 0, 0));
        float[] bufferedValues = new float[] {
                1.0f, 2.0f, 3.0f, 4.0f,
                1.0f, 2.0f, 3.0f, 4.0f,
                2.0f, 3.0f, 4.0f, 5.0f,
                2.0f, 3.0f, 4.0f, 5.0f
        };
        LightingParameters parameters = new LightingParameters(0, 16, 1.0, 0.0, 1.0, 0.2, 180.0, 45.0, 5.0, 1.0);
        TileProcessor processor = new TileProcessor();

        TileComputationResult singleThread = processor.process(request, metadata, bufferedValues, parameters, 1.0, 1);
        TileComputationResult multiThread = processor.process(request, metadata, bufferedValues, parameters, 1.0, 4);

        assertThat(multiThread.skipped()).isFalse();
        assertThat(multiThread.validPixelCount()).isEqualTo(singleThread.validPixelCount());
        assertThat(multiThread.data()).containsExactly(singleThread.data());
    }
}
