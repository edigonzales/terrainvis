package ch.so.agi.terrainvis.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;

import ch.so.agi.terrainvis.config.AlgorithmMode;
import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.HorizonParameters;
import ch.so.agi.terrainvis.config.LightingParameters;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.tiling.TileWindow;

class HorizonApproxProcessorTest {
    @Test
    void flatSurfaceKeepsFullSkyAndSunVisibility() throws Exception {
        RasterMetadata metadata = metadata(3, 3);
        TileRequest request = centeredSinglePixelRequest(3, 3);
        float[] bufferedValues = new float[] {
                5.0f, 5.0f, 5.0f,
                5.0f, 5.0f, 5.0f,
                5.0f, 5.0f, 5.0f
        };
        RunConfig runConfig = horizonConfig(
                metadata,
                request,
                new LightingParameters(0, 128, 1.0, 0.0, 1.0, 1.0, 0.0, 45.0, 10.0, 1.0),
                new HorizonParameters(8, null));

        TileComputationResult result = new TileProcessor().process(request, metadata, bufferedValues, runConfig, 1);

        assertThat(result.skipped()).isFalse();
        assertThat(result.validPixelCount()).isEqualTo(1);
        assertThat(result.data()[0]).isCloseTo(2.0f, within(1.0e-4f));
    }

    @Test
    void sunVisibilitySoftensAcrossAngularDiameter() throws Exception {
        RasterMetadata metadata = metadata(3, 3);
        TileRequest request = centeredSinglePixelRequest(3, 3);
        float horizonHeight = (float) Math.tan(Math.toRadians(30.0));
        float[] bufferedValues = new float[] {
                0.0f, horizonHeight, 0.0f,
                0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f
        };
        RunConfig runConfig = horizonConfig(
                metadata,
                request,
                new LightingParameters(0, 128, 1.0, 0.0, 0.0, 1.0, 0.0, 30.0, 10.0, 1.0),
                new HorizonParameters(4, null));

        TileComputationResult result = new TileProcessor().process(request, metadata, bufferedValues, runConfig, 1);

        assertThat(result.data()[0]).isCloseTo(0.5f, within(0.05f));
    }

    @Test
    void horizonApproxStaysCloseToExactForSingleObstacle() throws Exception {
        RasterMetadata metadata = metadata(5, 5);
        TileRequest request = centeredSinglePixelRequest(5, 5);
        float[] bufferedValues = new float[] {
                0.0f, 0.0f, 4.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 4.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f
        };
        LightingParameters parameters = new LightingParameters(0, 256, 1.0, 0.0, 1.0, 0.0, 0.0, 45.0, 11.4, 1.0);
        TileProcessor processor = new TileProcessor();

        TileComputationResult exact = processor.process(request, metadata, bufferedValues, parameters, 1.0, 1);
        TileComputationResult horizon = processor.process(
                request,
                metadata,
                bufferedValues,
                horizonConfig(metadata, request, parameters, new HorizonParameters(32, null)),
                1);

        assertThat(Math.abs(horizon.data()[0] - exact.data()[0])).isLessThan(0.2f);
    }

    private RasterMetadata metadata(int width, int height) throws Exception {
        return new RasterMetadata(
                width,
                height,
                new ReferencedEnvelope(0.0, width, 0.0, height, CRS.decode("EPSG:2056", true)),
                CRS.decode("EPSG:2056", true),
                1.0,
                1.0,
                -9999.0,
                1,
                "test");
    }

    private TileRequest centeredSinglePixelRequest(int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;
        return new TileRequest(
                0,
                0,
                0,
                new TileWindow(new PixelWindow(centerX, centerY, 1, 1), new PixelWindow(0, 0, width, height), centerX, centerY));
    }

    private RunConfig horizonConfig(
            RasterMetadata metadata,
            TileRequest request,
            LightingParameters lightingParameters,
            HorizonParameters horizonParameters) {
        return new RunConfig(
                metadata.sourceDescription(),
                new BBox(0.0, 0.0, metadata.width(), metadata.height()),
                AlgorithmMode.HORIZON,
                OutputMode.TILE_FILES,
                Path.of("output.tif"),
                Path.of("output_tiles"),
                request.window().coreWindow().width(),
                null,
                null,
                0,
                1,
                false,
                false,
                false,
                1.0,
                lightingParameters,
                horizonParameters);
    }
}
