package ch.so.agi.terrainvis.pipeline;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import ch.so.agi.terrainvis.config.AlgorithmMode;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.core.TileComputationResult;
import ch.so.agi.terrainvis.core.TileProcessor;
import ch.so.agi.terrainvis.output.SingleFileAccumulator;
import ch.so.agi.terrainvis.output.TileWriter;
import ch.so.agi.terrainvis.output.TiledTileWriter;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.raster.RasterSource;
import ch.so.agi.terrainvis.tiling.TilePlan;
import ch.so.agi.terrainvis.tiling.TilePlanner;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.util.ConsoleLogger;
import ch.so.agi.terrainvis.util.HeartbeatScheduler;
import ch.so.agi.terrainvis.util.ScheduledHeartbeatScheduler;

public final class OcclusionPipeline {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final TilePlanner tilePlanner;
    private final TileProcessor tileProcessor;
    private final ConsoleLogger logger;
    private final Supplier<HeartbeatScheduler> heartbeatSchedulerSupplier;

    public OcclusionPipeline(ConsoleLogger logger, Clock clock) {
        this(new TilePlanner(), new TileProcessor(), logger, clock, () -> new ScheduledHeartbeatScheduler("terrainvis-heartbeat"));
    }

    OcclusionPipeline(
            TilePlanner tilePlanner,
            TileProcessor tileProcessor,
            ConsoleLogger logger,
            Clock clock,
            Supplier<HeartbeatScheduler> heartbeatSchedulerSupplier) {
        this.tilePlanner = tilePlanner;
        this.tileProcessor = tileProcessor;
        this.logger = logger;
        this.heartbeatSchedulerSupplier = heartbeatSchedulerSupplier;
    }

    public void run(RasterSource rasterSource, RunConfig runConfig) throws IOException {
        long runStartNanos = System.nanoTime();
        RasterMetadata metadata = rasterSource.metadata();
        int bufferPixelsX = resolveEffectiveBufferPixelsX(runConfig, metadata);
        int bufferPixelsY = resolveEffectiveBufferPixelsY(runConfig, metadata);
        TilePlan tilePlan = tilePlanner.plan(metadata, runConfig.bbox(), runConfig.tileSizePixels(), bufferPixelsX, bufferPixelsY);
        int submitted = countSubmittedTiles(tilePlan, runConfig.startTile());
        ExecutionLayout executionLayout = resolveExecutionLayout(runConfig, submitted);

        logger.info(
                "Resolved config: algorithm=%s, threads=%d, tileWorkers=%d, tileThreads=%d, tileSize=%d px, buffer=%d x %d px (~%.3f x %.3f m), rays=%d, horizonDirections=%d, horizonRadius=%s, exaggeration=%.3f, startTile=%d",
                runConfig.algorithmMode(),
                runConfig.threads(),
                executionLayout.tileWorkers(),
                executionLayout.tileComputeThreads(),
                runConfig.tileSizePixels(),
                bufferPixelsX,
                bufferPixelsY,
                bufferPixelsX * metadata.resolutionX(),
                bufferPixelsY * metadata.resolutionY(),
                runConfig.lightingParameters().raysPerPixel(),
                runConfig.horizonParameters().directions(),
                formatHorizonRadius(runConfig),
                runConfig.exaggeration(),
                runConfig.startTile());
        logger.info(
                "Tile plan: requestedWindow=%dx%d at (%d,%d), totalTiles=%d, tilesToExecute=%d",
                tilePlan.requestedWindow().width(),
                tilePlan.requestedWindow().height(),
                tilePlan.requestedWindow().x(),
                tilePlan.requestedWindow().y(),
                tilePlan.tiles().size(),
                submitted);

        AtomicInteger completedCount = new AtomicInteger();
        AtomicInteger skippedCount = new AtomicInteger();
        AtomicInteger inFlightCount = new AtomicInteger();

        try (TileWriter tileWriter = createWriter(runConfig, metadata, tilePlan);
                HeartbeatScheduler heartbeatScheduler = heartbeatSchedulerSupplier.get()) {
            HeartbeatScheduler.Cancellable heartbeat = submitted > 0
                    ? heartbeatScheduler.scheduleAtFixedRate(
                            HEARTBEAT_INTERVAL,
                            HEARTBEAT_INTERVAL,
                            () -> logger.info(
                                    "Heartbeat: elapsed=%s, completed=%d/%d, skipped=%d, inFlight=%d",
                                    formatDuration(System.nanoTime() - runStartNanos),
                                    completedCount.get(),
                                    submitted,
                                    skippedCount.get(),
                                    inFlightCount.get()))
                    : () -> {
                    };

            ExecutorService executor = Executors.newFixedThreadPool(executionLayout.tileWorkers());
            try {
                ExecutorCompletionService<WorkerTileResult> completionService = new ExecutorCompletionService<>(executor);
                logger.info(
                        "Submitting %d tile(s) to %d worker(s) with %d compute thread(s) per tile",
                        submitted,
                        executionLayout.tileWorkers(),
                        executionLayout.tileComputeThreads());
                for (TileRequest tileRequest : tilePlan.tiles()) {
                    if (tileRequest.id() < runConfig.startTile()) {
                        continue;
                    }
                    logger.verbose(
                            "Queued tile: tileId=%d row=%d col=%d core=%dx%d buffered=%dx%d",
                            tileRequest.id(),
                            tileRequest.tileRow(),
                            tileRequest.tileColumn(),
                            tileRequest.window().coreWindow().width(),
                            tileRequest.window().coreWindow().height(),
                            tileRequest.window().bufferedWindow().width(),
                            tileRequest.window().bufferedWindow().height());
                    completionService.submit(() -> executeTile(
                            rasterSource,
                            runConfig,
                            metadata,
                            tileRequest,
                            executionLayout.tileComputeThreads(),
                            inFlightCount));
                }

                for (int completed = 0; completed < submitted; completed++) {
                    Future<WorkerTileResult> future = completionService.take();
                    WorkerTileResult workerResult = future.get();
                    if (!workerResult.result().skipped()) {
                        logger.verbose("Writing tile output: tileId=%d", workerResult.result().tileRequest().id());
                    }
                    long writeStartNanos = System.nanoTime();
                    tileWriter.write(workerResult.result());
                    long writeDurationNanos = System.nanoTime() - writeStartNanos;

                    int completedValue = completedCount.incrementAndGet();
                    if (workerResult.result().skipped()) {
                        skippedCount.incrementAndGet();
                    }
                    long totalTileDurationNanos = workerResult.workerDurationNanos() + writeDurationNanos;
                    logger.info(
                            "Completed tile %d/%d: tileId=%d row=%d col=%d status=%s validPixels=%d elapsed=%s",
                            completedValue,
                            submitted,
                            workerResult.result().tileRequest().id(),
                            workerResult.result().tileRequest().tileRow(),
                            workerResult.result().tileRequest().tileColumn(),
                            workerResult.result().skipped() ? "skipped" : "processed",
                            workerResult.result().validPixelCount(),
                            formatDuration(totalTileDurationNanos));
                }

                tileWriter.finish();
                heartbeat.cancel();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Processing interrupted");
                throw new IOException("Processing interrupted", e);
            } catch (ExecutionException e) {
                heartbeat.cancel();
                IOException exception = unwrap(e);
                logger.error(exception.getMessage());
                throw exception;
            } finally {
                heartbeat.cancel();
                executor.shutdownNow();
            }
        }

        logger.info(
                "Finished run: completed=%d, skipped=%d, output=%s, elapsed=%s",
                completedCount.get(),
                skippedCount.get(),
                runConfig.outputMode() == OutputMode.SINGLE_FILE ? runConfig.outputFile() : runConfig.outputDirectory(),
                formatDuration(System.nanoTime() - runStartNanos));
    }

    private WorkerTileResult executeTile(
            RasterSource rasterSource,
            RunConfig runConfig,
            RasterMetadata metadata,
            TileRequest tileRequest,
            int tileComputeThreads,
            AtomicInteger inFlightCount) throws IOException {
        inFlightCount.incrementAndGet();
        long workerStartNanos = System.nanoTime();
        try {
            logger.verbose("Reading buffered window: tileId=%d", tileRequest.id());
            long readStartNanos = System.nanoTime();
            float[] bufferedValues = rasterSource.readWindow(tileRequest.window().bufferedWindow());
            long readDurationNanos = System.nanoTime() - readStartNanos;

            logger.verbose("Tracing tile: tileId=%d", tileRequest.id());
            long computeStartNanos = System.nanoTime();
            TileComputationResult result = tileProcessor.process(
                    tileRequest,
                    metadata,
                    bufferedValues,
                    runConfig,
                    tileComputeThreads);
            long computeDurationNanos = System.nanoTime() - computeStartNanos;
            if (result.skipped()) {
                logger.verbose("Skipping tile because core window has no data: tileId=%d", tileRequest.id());
            }
            return new WorkerTileResult(
                    result,
                    readDurationNanos,
                    computeDurationNanos,
                    System.nanoTime() - workerStartNanos);
        } catch (IOException | RuntimeException e) {
            throw new TileExecutionException(tileRequest, e);
        } finally {
            inFlightCount.decrementAndGet();
        }
    }

    private TileWriter createWriter(RunConfig runConfig, RasterMetadata metadata, TilePlan tilePlan) throws IOException {
        if (runConfig.outputMode() == OutputMode.SINGLE_FILE) {
            return new SingleFileAccumulator(runConfig, metadata, tilePlan, logger);
        }
        return new TiledTileWriter(runConfig, metadata, logger);
    }

    private int countSubmittedTiles(TilePlan tilePlan, int startTile) {
        int submitted = 0;
        for (TileRequest tileRequest : tilePlan.tiles()) {
            if (tileRequest.id() >= startTile) {
                submitted++;
            }
        }
        return submitted;
    }

    private int resolveEffectiveBufferPixelsX(RunConfig runConfig, RasterMetadata metadata) {
        int resolved = resolveBaseBufferPixelsX(runConfig, metadata);
        if (runConfig.algorithmMode() == AlgorithmMode.HORIZON && runConfig.horizonParameters().radiusMeters() != null) {
            resolved = Math.max(resolved, metadata.bufferPixelsX(runConfig.horizonParameters().radiusMeters()));
        }
        return resolved;
    }

    private int resolveEffectiveBufferPixelsY(RunConfig runConfig, RasterMetadata metadata) {
        int resolved = resolveBaseBufferPixelsY(runConfig, metadata);
        if (runConfig.algorithmMode() == AlgorithmMode.HORIZON && runConfig.horizonParameters().radiusMeters() != null) {
            resolved = Math.max(resolved, metadata.bufferPixelsY(runConfig.horizonParameters().radiusMeters()));
        }
        return resolved;
    }

    private int resolveBaseBufferPixelsX(RunConfig runConfig, RasterMetadata metadata) {
        if (runConfig.bufferPixelsOverride() != null) {
            return runConfig.bufferPixelsOverride();
        }
        if (runConfig.bufferMetersOverride() != null) {
            return metadata.bufferPixelsX(runConfig.bufferMetersOverride());
        }
        return runConfig.defaultBufferPixels();
    }

    private int resolveBaseBufferPixelsY(RunConfig runConfig, RasterMetadata metadata) {
        if (runConfig.bufferPixelsOverride() != null) {
            return runConfig.bufferPixelsOverride();
        }
        if (runConfig.bufferMetersOverride() != null) {
            return metadata.bufferPixelsY(runConfig.bufferMetersOverride());
        }
        return runConfig.defaultBufferPixels();
    }

    private String formatHorizonRadius(RunConfig runConfig) {
        if (runConfig.algorithmMode() != AlgorithmMode.HORIZON) {
            return "-";
        }
        if (runConfig.horizonParameters().radiusMeters() == null) {
            return "buffer";
        }
        return String.format(Locale.ROOT, "%.3f m", runConfig.horizonParameters().radiusMeters());
    }

    private IOException unwrap(ExecutionException executionException) {
        Throwable cause = executionException.getCause();
        if (cause instanceof TileExecutionException tileExecutionException) {
            TileRequest tileRequest = tileExecutionException.tileRequest();
            Throwable tileCause = tileExecutionException.getCause();
            String message = String.format(
                    Locale.ROOT,
                    "Tile processing failed for tileId=%d row=%d col=%d",
                    tileRequest.id(),
                    tileRequest.tileRow(),
                    tileRequest.tileColumn());
            if (tileCause instanceof IOException ioException) {
                return new IOException(message, ioException);
            }
            if (tileCause instanceof RuntimeException runtimeException) {
                throw new RuntimeException(message, runtimeException);
            }
            return new IOException(message, tileCause);
        }
        if (cause instanceof IOException ioException) {
            return ioException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return new IOException("Tile processing failed", cause);
    }

    private String formatDuration(long elapsedNanos) {
        Duration duration = Duration.ofNanos(elapsedNanos);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (hours > 0) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private ExecutionLayout resolveExecutionLayout(RunConfig runConfig, int submittedTiles) {
        int totalThreads = runConfig.threads();
        if (runConfig.algorithmMode() == AlgorithmMode.HORIZON) {
            return new ExecutionLayout(Math.max(1, Math.min(submittedTiles, totalThreads)), 1);
        }
        if (submittedTiles <= 1 || totalThreads <= 4) {
            return new ExecutionLayout(1, totalThreads);
        }
        // Each tile holds a large BVH, so prefer a few concurrent tiles and parallelize inside the tile.
        int tileWorkers = Math.min(submittedTiles, Math.max(1, totalThreads / 8));
        int tileComputeThreads = Math.max(1, totalThreads / tileWorkers);
        return new ExecutionLayout(tileWorkers, tileComputeThreads);
    }

    record WorkerTileResult(
            TileComputationResult result,
            long readDurationNanos,
            long computeDurationNanos,
            long workerDurationNanos) {
    }

    record ExecutionLayout(int tileWorkers, int tileComputeThreads) {
    }

    private static final class TileExecutionException extends IOException {
        private static final long serialVersionUID = 1L;
        private final TileRequest tileRequest;

        private TileExecutionException(TileRequest tileRequest, Throwable cause) {
            super(cause);
            this.tileRequest = tileRequest;
        }

        private TileRequest tileRequest() {
            return tileRequest;
        }
    }
}
