package ch.so.agi.terrainvis.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.geotools.referencing.CRS;

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
import ch.so.agi.terrainvis.util.NoData;

public final class RenderGridAligner {
    private static final double EPSILON = 1.0e-9;

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
        float[] inputValues = inputSource.readWindow(inputWindow);
        float[] outputValues = new float[referenceWindow.width() * referenceWindow.height()];
        int validPixelCount = 0;

        int outputIndex = 0;
        for (int row = 0; row < referenceWindow.height(); row++) {
            int inputRowStart = row * alignment.factorY();
            for (int column = 0; column < referenceWindow.width(); column++) {
                int inputColumnStart = column * alignment.factorX();
                float maxValue = 0.0f;
                boolean hasValue = false;
                for (int offsetY = 0; offsetY < alignment.factorY(); offsetY++) {
                    int rowOffset = (inputRowStart + offsetY) * inputWindow.width();
                    for (int offsetX = 0; offsetX < alignment.factorX(); offsetX++) {
                        float candidate = inputValues[rowOffset + inputColumnStart + offsetX];
                        if (NoData.isNoData(candidate, noDataValue)) {
                            continue;
                        }
                        if (!hasValue || candidate > maxValue) {
                            maxValue = candidate;
                            hasValue = true;
                        }
                    }
                }
                if (hasValue) {
                    outputValues[outputIndex] = maxValue;
                    validPixelCount++;
                } else {
                    outputValues[outputIndex] = (float) noDataValue;
                }
                outputIndex++;
            }
        }

        return new RasterTileResult(
                tileRequest,
                RasterBlock.singleBand(referenceWindow.width(), referenceWindow.height(), noDataValue, outputValues),
                validPixelCount == 0,
                validPixelCount);
    }

    private GridAlignment validateAlignment(RasterMetadata inputMetadata, RasterMetadata referenceMetadata) {
        if (!CRS.equalsIgnoreMetadata(inputMetadata.crs(), referenceMetadata.crs())) {
            throw new IllegalArgumentException("Input CRS does not match the reference raster.");
        }
        if (!same(inputMetadata.envelope().getMinX(), referenceMetadata.envelope().getMinX())
                || !same(inputMetadata.envelope().getMinY(), referenceMetadata.envelope().getMinY())
                || !same(inputMetadata.envelope().getMaxX(), referenceMetadata.envelope().getMaxX())
                || !same(inputMetadata.envelope().getMaxY(), referenceMetadata.envelope().getMaxY())) {
            throw new IllegalArgumentException("Input extent does not match the reference raster.");
        }
        if (inputMetadata.resolutionX() - referenceMetadata.resolutionX() > EPSILON
                || inputMetadata.resolutionY() - referenceMetadata.resolutionY() > EPSILON) {
            throw new IllegalArgumentException("Input resolution must be finer than or equal to the reference raster.");
        }

        int factorX = integerFactor(referenceMetadata.resolutionX() / inputMetadata.resolutionX());
        int factorY = integerFactor(referenceMetadata.resolutionY() / inputMetadata.resolutionY());
        if (inputMetadata.width() != referenceMetadata.width() * factorX
                || inputMetadata.height() != referenceMetadata.height() * factorY) {
            throw new IllegalArgumentException("Input dimensions are not aligned to the reference raster grid.");
        }
        return new GridAlignment(factorX, factorY);
    }

    private int integerFactor(double factor) {
        long rounded = Math.round(factor);
        if (rounded <= 0 || Math.abs(factor - rounded) > EPSILON || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Reference-to-input resolution ratio must be an integer in both axes.");
        }
        return (int) rounded;
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

    private boolean same(double a, double b) {
        return Math.abs(a - b) <= EPSILON;
    }

    private record GridAlignment(int factorX, int factorY) {
        private PixelWindow inputWindow(PixelWindow referenceWindow) {
            return new PixelWindow(
                    referenceWindow.x() * factorX,
                    referenceWindow.y() * factorY,
                    referenceWindow.width() * factorX,
                    referenceWindow.height() * factorY);
        }
    }
}
