package ch.so.agi.terrainvis.pipeline;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ch.so.agi.terrainvis.config.CommonRunConfig;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.output.RasterBlock;
import ch.so.agi.terrainvis.output.RasterTileResult;
import ch.so.agi.terrainvis.output.RasterTileWriter;
import ch.so.agi.terrainvis.output.SingleFileRasterAccumulator;
import ch.so.agi.terrainvis.output.TiledRasterWriter;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.raster.RasterSource;
import ch.so.agi.terrainvis.rvt.RvtDefaults;
import ch.so.agi.terrainvis.rvt.RvtRenderer;
import ch.so.agi.terrainvis.rvt.RvtRunConfig;
import ch.so.agi.terrainvis.tiling.TilePlan;
import ch.so.agi.terrainvis.tiling.TilePlanner;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.util.ConsoleLogger;

public final class RvtPipeline {
    private final TilePlanner tilePlanner;
    private final RvtRenderer renderer;
    private final ConsoleLogger logger;

    public RvtPipeline(ConsoleLogger logger, Clock clock) {
        this(new TilePlanner(), new RvtRenderer(), logger);
    }

    RvtPipeline(TilePlanner tilePlanner, RvtRenderer renderer, ConsoleLogger logger) {
        this.tilePlanner = tilePlanner;
        this.renderer = renderer;
        this.logger = logger;
    }

    public void run(RasterSource rasterSource, RvtRunConfig runConfig) throws IOException {
        RasterMetadata metadata = rasterSource.metadata();
        CommonRunConfig commonConfig = runConfig.commonConfig();
        int bufferPixels = RvtDefaults.requiredBufferPixels(runConfig, metadata);
        TilePlan tilePlan = tilePlanner.plan(
                metadata,
                commonConfig.bbox(),
                commonConfig.tilingConfig().tileSizePixels(),
                bufferPixels,
                bufferPixels);
        int bandCount = RvtDefaults.bandCount(runConfig.product(), runConfig.parameters());
        int threads = commonConfig.tilingConfig().threads();

        logger.info(
                "RVT run start: product=%s, tiles=%d, threads=%d, tileSize=%d, buffer=%d px, outputMode=%s, output=%s",
                runConfig.product(),
                tilePlan.tiles().size(),
                threads,
                commonConfig.tilingConfig().tileSizePixels(),
                bufferPixels,
                commonConfig.outputConfig().mode(),
                commonConfig.outputConfig().output());

        try (RasterTileWriter writer = createWriter(commonConfig, metadata, tilePlan, bandCount)) {
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                ExecutorCompletionService<RasterTileResult> completionService = new ExecutorCompletionService<>(executor);
                int submitted = 0;
                for (TileRequest tileRequest : tilePlan.tiles()) {
                    if (tileRequest.id() < commonConfig.tilingConfig().startTile()) {
                        continue;
                    }
                    submitted++;
                    completionService.submit(() -> executeTile(rasterSource, metadata, tileRequest, runConfig));
                }
                for (int completed = 0; completed < submitted; completed++) {
                    Future<RasterTileResult> future = completionService.take();
                    RasterTileResult result = future.get();
                    writer.write(result);
                    logger.info(
                            "Completed RVT tile %d/%d: tileId=%d status=%s validPixels=%d",
                            completed + 1,
                            submitted,
                            result.tileRequest().id(),
                            result.skipped() ? "skipped" : "processed",
                            result.validPixelCount());
                }
                writer.finish();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("RVT processing interrupted", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IOException("RVT processing failed", cause);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private RasterTileResult executeTile(
            RasterSource rasterSource,
            RasterMetadata metadata,
            TileRequest tileRequest,
            RvtRunConfig runConfig) throws IOException {
        float[] bufferedValues = rasterSource.readWindow(tileRequest.window().bufferedWindow());
        RasterTileResult rawResult = renderer.renderTile(tileRequest, metadata, bufferedValues, runConfig);
        if (rawResult.skipped() || runConfig.commonConfig().outputConfig().dataType() == ch.so.agi.terrainvis.config.OutputDataType.FLOAT32) {
            return rawResult;
        }
        RasterBlock normalized = RvtDefaults.normalizeForByte(runConfig.product(), rawResult.block());
        return new RasterTileResult(rawResult.tileRequest(), normalized, rawResult.skipped(), rawResult.validPixelCount());
    }

    private RasterTileWriter createWriter(
            CommonRunConfig runConfig,
            RasterMetadata metadata,
            TilePlan tilePlan,
            int bandCount) throws IOException {
        if (runConfig.outputConfig().mode() == OutputMode.SINGLE_FILE) {
            return new SingleFileRasterAccumulator(runConfig, tilePlan, bandCount, metadata.noDataValue(), logger);
        }
        return new TiledRasterWriter(runConfig, metadata, logger);
    }
}
