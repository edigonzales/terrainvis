package ch.so.agi.terrainvis.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.CommonRunConfig;
import ch.so.agi.terrainvis.config.OutputConfig;
import ch.so.agi.terrainvis.config.OutputDataType;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.TilingConfig;
import ch.so.agi.terrainvis.output.RasterBlock;
import ch.so.agi.terrainvis.output.RasterTileResult;
import ch.so.agi.terrainvis.output.RasterTileWriter;
import ch.so.agi.terrainvis.output.SingleFileRasterAccumulator;
import ch.so.agi.terrainvis.raster.GeoToolsCogRasterSource;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TilePlan;
import ch.so.agi.terrainvis.tiling.TilePlanner;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.util.ConsoleLogger;

public final class RenderGridAligner {
    private final TilePlanner tilePlanner;
    private final ConsoleLogger logger;

    public RenderGridAligner(ConsoleLogger logger) {
        this(new TilePlanner(), logger);
    }

    RenderGridAligner(TilePlanner tilePlanner, ConsoleLogger logger) {
        this.tilePlanner = tilePlanner;
        this.logger = logger;
    }

    public void run(AlignGridRunConfig runConfig) throws IOException {
        try (GeoToolsCogRasterSource inputSource = new GeoToolsCogRasterSource(runConfig.inputPath(), logger);
                GeoToolsCogRasterSource referenceSource = new GeoToolsCogRasterSource(runConfig.referencePath(), logger)) {
            RasterMetadata inputMetadata = inputSource.metadata();
            RasterMetadata referenceMetadata = referenceSource.metadata();
            GridAlignment alignment = validateAlignment(inputMetadata, referenceMetadata);
            BBox fullBbox = fullExtent(referenceMetadata);

            if (runConfig.printInfo()) {
                logger.info("Reference raster:%n%s", referenceMetadata.describe());
                logger.info("Input raster:%n%s", inputMetadata.describe());
                logger.info("Alignment factors: x=%d, y=%d", alignment.factorX(), alignment.factorY());
                logger.info("Threads: %d", runConfig.threads());
            }

            TilePlan tilePlan = tilePlanner.plan(referenceMetadata, fullBbox, runConfig.tileSizePixels(), 0, 0);
            logger.info(
                    "Align-grid run start: tiles=%d, threads=%d, tileSize=%d, input=%s, reference=%s, output=%s",
                    tilePlan.tiles().size(),
                    runConfig.threads(),
                    runConfig.tileSizePixels(),
                    runConfig.inputPath(),
                    runConfig.referencePath(),
                    runConfig.outputPath());

            ensureParentDirectory(runConfig.outputPath());
            CommonRunConfig writerConfig = new CommonRunConfig(
                    runConfig.inputPath(),
                    fullBbox,
                    new OutputConfig(OutputMode.SINGLE_FILE, runConfig.outputPath(), OutputDataType.FLOAT32),
                    new TilingConfig(runConfig.tileSizePixels(), null, null, 0, runConfig.threads()),
                    runConfig.printInfo(),
                    runConfig.verbose());

            try (RasterTileWriter writer = new SingleFileRasterAccumulator(writerConfig, tilePlan, 1, inputMetadata.noDataValue(), logger)) {
                ExecutorService executor = Executors.newFixedThreadPool(runConfig.threads());
                try {
                    ExecutorCompletionService<RasterTileResult> completionService = new ExecutorCompletionService<>(executor);
                    for (TileRequest tileRequest : tilePlan.tiles()) {
                        completionService.submit(() -> executeTile(tileRequest, inputSource, alignment, inputMetadata.noDataValue()));
                    }
                    for (int completed = 0; completed < tilePlan.tiles().size(); completed++) {
                        Future<RasterTileResult> future = completionService.take();
                        RasterTileResult result = future.get();
                        writer.write(result);
                        logger.info(
                                "Completed align tile %d/%d: tileId=%d status=%s validPixels=%d",
                                completed + 1,
                                tilePlan.tiles().size(),
                                result.tileRequest().id(),
                                result.skipped() ? "skipped" : "processed",
                                result.validPixelCount());
                    }
                    writer.finish();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Align-grid processing interrupted", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException ioException) {
                        throw ioException;
                    }
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IOException("Align-grid processing failed", cause);
                } finally {
                    executor.shutdownNow();
                }
            }
        }
    }

    private RasterTileResult executeTile(
            TileRequest tileRequest,
            GeoToolsCogRasterSource inputSource,
            GridAlignment alignment,
            double noDataValue) throws IOException {
        PixelWindow referenceWindow = tileRequest.window().coreWindow();
        PixelWindow inputWindow = alignment.inputWindow(referenceWindow);
        float[] inputValues = inputWindow.isEmpty() ? new float[0] : inputSource.readWindow(inputWindow);
        float[] outputValues = alignment.aggregateMax(referenceWindow, inputValues, noDataValue);
        int validPixelCount = 0;
        for (float outputValue : outputValues) {
            if (!Double.isNaN(noDataValue) ? outputValue != (float) noDataValue : !Float.isNaN(outputValue)) {
                validPixelCount++;
            }
        }

        return new RasterTileResult(
                tileRequest,
                RasterBlock.singleBand(referenceWindow.width(), referenceWindow.height(), noDataValue, outputValues),
                validPixelCount == 0,
                validPixelCount);
    }

    private GridAlignment validateAlignment(RasterMetadata inputMetadata, RasterMetadata referenceMetadata) {
        GridAlignment alignment = GridAlignment.between(inputMetadata, referenceMetadata, "Input");
        alignment.requireSameExtent(referenceMetadata, "Input");
        return alignment;
    }

    private BBox fullExtent(RasterMetadata metadata) {
        return new BBox(
                metadata.envelope().getMinX(),
                metadata.envelope().getMinY(),
                metadata.envelope().getMaxX(),
                metadata.envelope().getMaxY());
    }

    private void ensureParentDirectory(Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

}
