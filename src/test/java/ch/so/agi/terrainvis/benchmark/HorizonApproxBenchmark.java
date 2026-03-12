package ch.so.agi.terrainvis.benchmark;

import java.nio.file.Path;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;

import ch.so.agi.terrainvis.config.AlgorithmMode;
import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.HorizonParameters;
import ch.so.agi.terrainvis.config.LightingParameters;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.core.TileComputationResult;
import ch.so.agi.terrainvis.core.TileProcessor;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.tiling.TileWindow;

public final class HorizonApproxBenchmark {
    private HorizonApproxBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        int size = 256;
        float[] values = syntheticSurface(size);
        RasterMetadata metadata = new RasterMetadata(
                size,
                size,
                new ReferencedEnvelope(0.0, size, 0.0, size, CRS.decode("EPSG:2056", true)),
                CRS.decode("EPSG:2056", true),
                1.0,
                1.0,
                -9999.0,
                1,
                "benchmark");
        TileRequest request = new TileRequest(
                0,
                0,
                0,
                new TileWindow(new PixelWindow(0, 0, size, size), new PixelWindow(0, 0, size, size), 0, 0));
        TileProcessor processor = new TileProcessor();
        LightingParameters lighting = new LightingParameters(0, 1024, 1.0, 0.0, 1.0, 0.2, 180.0, 35.0, 5.0, 1.0);

        RunConfig exact = new RunConfig(
                "benchmark",
                new BBox(0.0, 0.0, size, size),
                AlgorithmMode.EXACT,
                OutputMode.TILE_FILES,
                Path.of("output.tif"),
                Path.of("output_tiles"),
                size,
                null,
                null,
                0,
                Runtime.getRuntime().availableProcessors(),
                false,
                false,
                false,
                1.0,
                lighting,
                HorizonParameters.defaults());
        RunConfig horizon = new RunConfig(
                "benchmark",
                new BBox(0.0, 0.0, size, size),
                AlgorithmMode.HORIZON,
                OutputMode.TILE_FILES,
                Path.of("output.tif"),
                Path.of("output_tiles"),
                size,
                null,
                null,
                0,
                Runtime.getRuntime().availableProcessors(),
                false,
                false,
                false,
                1.0,
                lighting,
                new HorizonParameters(32, 64.0));

        warmup(processor, request, metadata, values, exact);
        warmup(processor, request, metadata, values, horizon);

        long exactNanos = timed(processor, request, metadata, values, exact);
        long horizonNanos = timed(processor, request, metadata, values, horizon);

        System.out.printf("Exact:   %.3f ms%n", exactNanos / 1_000_000.0);
        System.out.printf("Horizon: %.3f ms%n", horizonNanos / 1_000_000.0);
        System.out.printf("Speedup: %.2fx%n", (double) exactNanos / horizonNanos);
    }

    private static void warmup(TileProcessor processor, TileRequest request, RasterMetadata metadata, float[] values, RunConfig config) {
        processor.process(request, metadata, values, config, Math.max(1, config.threads()));
    }

    private static long timed(TileProcessor processor, TileRequest request, RasterMetadata metadata, float[] values, RunConfig config) {
        long start = System.nanoTime();
        TileComputationResult result = processor.process(request, metadata, values, config, Math.max(1, config.threads()));
        if (result.validPixelCount() == 0) {
            throw new IllegalStateException("Benchmark produced no valid pixels");
        }
        return System.nanoTime() - start;
    }

    private static float[] syntheticSurface(int size) {
        float[] values = new float[size * size];
        int center = size / 2;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - center;
                double dy = y - center;
                double mound = Math.max(0.0, 32.0 - Math.hypot(dx, dy));
                double ridge = x > center ? 8.0 : 0.0;
                values[(y * size) + x] = (float) (mound + ridge);
            }
        }
        return values;
    }
}
