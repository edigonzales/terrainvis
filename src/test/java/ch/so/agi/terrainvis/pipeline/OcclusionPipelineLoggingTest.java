package ch.so.agi.terrainvis.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.terrainvis.config.AlgorithmMode;
import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.HorizonParameters;
import ch.so.agi.terrainvis.config.LightingParameters;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.testsupport.BlockingRasterSource;
import ch.so.agi.terrainvis.testsupport.CapturedCommandOutput;
import ch.so.agi.terrainvis.testsupport.ManualHeartbeatScheduler;
import ch.so.agi.terrainvis.testsupport.WindowedRasterSource;
import ch.so.agi.terrainvis.util.ConsoleLogger;

class OcclusionPipelineLoggingTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsHeartbeatBeforeFirstTileCompletes() throws Exception {
        CapturedCommandOutput output = new CapturedCommandOutput();
        ConsoleLogger logger = new ConsoleLogger(output.stdoutWriter(), output.stderrWriter(), fixedClock(), false);
        ManualHeartbeatScheduler heartbeatScheduler = new ManualHeartbeatScheduler();
        OcclusionPipeline pipeline = newPipeline(logger, heartbeatScheduler);

        RasterMetadata metadata = metadata(2, 2, "blocking-test");
        BlockingRasterSource rasterSource = new BlockingRasterSource(metadata, new float[] {
                1.0f, 2.0f,
                3.0f, 4.0f
        });
        RunConfig runConfig = runConfig("blocking-test", 2, 2, AlgorithmMode.EXACT, 2, 1, HorizonParameters.defaults());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    pipeline.run(rasterSource, runConfig);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertThat(rasterSource.awaitReadStarted(5, TimeUnit.SECONDS)).isTrue();
            heartbeatScheduler.trigger();
            assertThat(output.stdout()).contains("Heartbeat:");

            rasterSource.releaseRead();
            future.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(output.stdout()).contains("Completed tile 1/1:").contains("Finished run:");
        assertThat(output.stderr()).isBlank();
    }

    @Test
    void logsHorizonExecutionLayoutAsPureTileParallelism() throws Exception {
        CapturedCommandOutput output = new CapturedCommandOutput();
        ConsoleLogger logger = new ConsoleLogger(output.stdoutWriter(), output.stderrWriter(), fixedClock(), false);
        OcclusionPipeline pipeline = newPipeline(logger, new ManualHeartbeatScheduler());
        RasterMetadata metadata = metadata(6, 2, "horizon-layout");
        WindowedRasterSource rasterSource = new WindowedRasterSource(metadata, new float[] {
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f,
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f
        });

        pipeline.run(
                rasterSource,
                runConfig("horizon-layout", 6, 2, AlgorithmMode.HORIZON, 2, 4, new HorizonParameters(8, null)));

        assertThat(output.stdout())
                .contains("Resolved config: algorithm=horizon, threads=4, tileWorkers=3, tileThreads=1")
                .contains("Submitting 3 tile(s) to 3 worker(s) with 1 compute thread(s) per tile");
        assertThat(output.stderr()).isBlank();
    }

    @Test
    void keepsExactExecutionLayoutHeuristic() throws Exception {
        CapturedCommandOutput output = new CapturedCommandOutput();
        ConsoleLogger logger = new ConsoleLogger(output.stdoutWriter(), output.stderrWriter(), fixedClock(), false);
        OcclusionPipeline pipeline = newPipeline(logger, new ManualHeartbeatScheduler());
        RasterMetadata metadata = metadata(6, 4, "exact-layout");
        WindowedRasterSource rasterSource = new WindowedRasterSource(metadata, new float[] {
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f,
                1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f,
                2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f,
                2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f
        });

        pipeline.run(
                rasterSource,
                runConfig("exact-layout", 6, 4, AlgorithmMode.EXACT, 2, 16, HorizonParameters.defaults()));

        assertThat(output.stdout())
                .contains("Resolved config: algorithm=exact, threads=16, tileWorkers=2, tileThreads=8")
                .contains("Submitting 6 tile(s) to 2 worker(s) with 8 compute thread(s) per tile");
        assertThat(output.stderr()).isBlank();
    }

    private OcclusionPipeline newPipeline(ConsoleLogger logger, ManualHeartbeatScheduler heartbeatScheduler) {
        return new OcclusionPipeline(
                new ch.so.agi.terrainvis.tiling.TilePlanner(),
                new ch.so.agi.terrainvis.core.TileProcessor(),
                logger,
                fixedClock(),
                () -> heartbeatScheduler);
    }

    private RasterMetadata metadata(int width, int height, String description) throws Exception {
        return new RasterMetadata(
                width,
                height,
                new ReferencedEnvelope(0.0, width, 0.0, height, CRS.decode("EPSG:2056", true)),
                CRS.decode("EPSG:2056", true),
                1.0,
                1.0,
                -9999.0,
                1,
                description);
    }

    private RunConfig runConfig(
            String inputLocation,
            int width,
            int height,
            AlgorithmMode algorithmMode,
            int tileSizePixels,
            int threads,
            HorizonParameters horizonParameters) {
        return new RunConfig(
                inputLocation,
                new BBox(0.0, 0.0, width, height),
                algorithmMode,
                OutputMode.TILE_FILES,
                tempDir.resolve(inputLocation + ".tif"),
                tempDir.resolve(inputLocation + "-tiles"),
                tileSizePixels,
                0,
                null,
                0,
                threads,
                false,
                false,
                false,
                1.0,
                new LightingParameters(0, 4, 1.0, 0.0, 1.0, 0.0, 0.0, 45.0, 11.4, 1.0),
                horizonParameters);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-03-09T12:00:00Z"), ZoneId.of("Europe/Zurich"));
    }
}
