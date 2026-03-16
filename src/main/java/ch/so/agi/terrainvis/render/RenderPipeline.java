package ch.so.agi.terrainvis.render;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.geotools.referencing.CRS;
import org.geotools.geometry.jts.ReferencedEnvelope;

import ch.so.agi.terrainvis.config.CommonRunConfig;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.output.RasterTileResult;
import ch.so.agi.terrainvis.output.RasterTileWriter;
import ch.so.agi.terrainvis.output.SingleFileRasterAccumulator;
import ch.so.agi.terrainvis.output.TiledRasterWriter;
import ch.so.agi.terrainvis.raster.GeoToolsCogRasterSource;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TilePlan;
import ch.so.agi.terrainvis.tiling.TilePlanner;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.util.ConsoleLogger;

public final class RenderPipeline {
    private static final double EPSILON = 1.0e-9;

    private final TilePlanner tilePlanner;
    private final RenderComposer composer;
    private final ConsoleLogger logger;

    public RenderPipeline(ConsoleLogger logger, Clock clock) {
        this(new TilePlanner(), new RenderComposer(), logger);
    }

    RenderPipeline(TilePlanner tilePlanner, RenderComposer composer, ConsoleLogger logger) {
        this.tilePlanner = tilePlanner;
        this.composer = composer;
        this.logger = logger;
    }

    public void run(RenderRunConfig runConfig) throws IOException {
        try (OpenedSources openedSources = openSources(runConfig.style())) {
            PreparedRender preparedRender = prepare(runConfig.style(), openedSources);
            if (runConfig.printInfo()) {
                logger.info("%s", preparedRender.referenceMetadata().describe());
                logger.info("Render layers: %d", preparedRender.layers().size());
                logger.info("Threads: %d", runConfig.tilingConfig().threads());
            }
            TilePlan tilePlan = tilePlanner.plan(
                    preparedRender.referenceMetadata(),
                    runConfig.bbox(),
                    runConfig.tilingConfig().tileSizePixels(),
                    0,
                    0);
            logger.info(
                    "Render run start: layers=%d, tiles=%d, threads=%d, tileSize=%d, outputMode=%s, output=%s, withAlpha=%s",
                    preparedRender.layers().size(),
                    tilePlan.tiles().size(),
                    runConfig.tilingConfig().threads(),
                    runConfig.tilingConfig().tileSizePixels(),
                    runConfig.outputConfig().mode(),
                    runConfig.outputConfig().output(),
                    runConfig.withAlpha());

            try (RasterTileWriter writer = createWriter(runConfig, preparedRender.referenceMetadata(), tilePlan)) {
                ExecutorService executor = Executors.newFixedThreadPool(runConfig.tilingConfig().threads());
                try {
                    ExecutorCompletionService<RasterTileResult> completionService = new ExecutorCompletionService<>(executor);
                    int submitted = 0;
                    for (TileRequest tileRequest : tilePlan.tiles()) {
                        if (tileRequest.id() < runConfig.tilingConfig().startTile()) {
                            continue;
                        }
                        submitted++;
                        completionService.submit(() -> executeTile(tileRequest, preparedRender.layers(), runConfig.withAlpha()));
                    }
                    for (int completed = 0; completed < submitted; completed++) {
                        Future<RasterTileResult> future = completionService.take();
                        RasterTileResult result = future.get();
                        writer.write(result);
                        logger.info(
                                "Completed render tile %d/%d: tileId=%d status=%s validPixels=%d",
                                completed + 1,
                                submitted,
                                result.tileRequest().id(),
                                result.skipped() ? "skipped" : "processed",
                                result.validPixelCount());
                    }
                    writer.finish();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Render processing interrupted", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException ioException) {
                        throw ioException;
                    }
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IOException("Render processing failed", cause);
                } finally {
                    executor.shutdownNow();
                }
            }
        }
    }

    private RasterTileResult executeTile(TileRequest tileRequest, List<PreparedLayer> layers, boolean withAlpha) throws IOException {
        PixelWindow window = tileRequest.window().coreWindow();
        ReferencedEnvelope targetEnvelope = layers.get(0).referenceMetadata().toEnvelope(window);
        Map<LayerReadKey, float[]> valueCache = new HashMap<>();
        List<RenderComposer.LayerTile> layerTiles = new ArrayList<>(layers.size());
        for (PreparedLayer layer : layers) {
            float[] values = readLayerValues(
                    valueCache,
                    layer.valueSource(),
                    layer.valueMetadata(),
                    layer.valueMatchesReferenceGrid(),
                    layer.valueAlignment(),
                    layer.spec().resampling(),
                    window,
                    targetEnvelope);
            float[] alphaValues = layer.alphaSource() == null
                    ? null
                    : readLayerValues(
                            valueCache,
                            layer.alphaSource(),
                            layer.alphaMetadata(),
                            layer.alphaMatchesReferenceGrid(),
                            layer.alphaAlignment(),
                            layer.spec().resampling(),
                            window,
                            targetEnvelope);
            layerTiles.add(new RenderComposer.LayerTile(
                    layer.spec(),
                    layer.ramp(),
                    values,
                    layer.valueMetadata().noDataValue(),
                    alphaValues,
                    layer.alphaMetadata() == null ? Double.NaN : layer.alphaMetadata().noDataValue()));
        }
        return composer.composeTile(tileRequest, layerTiles, withAlpha);
    }

    private float[] readLayerValues(
            Map<LayerReadKey, float[]> cache,
            GeoToolsCogRasterSource source,
            RasterMetadata sourceMetadata,
            boolean matchesReferenceGrid,
            GridAlignment alignment,
            LayerResampling resampling,
            PixelWindow window,
            ReferencedEnvelope targetEnvelope) throws IOException {
        LayerReadKey cacheKey = new LayerReadKey(source, resampling, matchesReferenceGrid);
        float[] cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        float[] values;
        if (matchesReferenceGrid) {
            values = source.readWindow(window);
        } else if (resampling == LayerResampling.MAX) {
            if (alignment == null) {
                throw new IllegalStateException("Missing grid alignment for max-resampled layer.");
            }
            PixelWindow inputWindow = alignment.inputWindow(window);
            float[] inputValues = inputWindow.isEmpty() ? new float[0] : source.readWindow(inputWindow);
            values = alignment.aggregateMax(window, inputValues, sourceMetadata.noDataValue());
        } else if (resampling == LayerResampling.NEAREST) {
            values = source.readAlignedNearestWindow(targetEnvelope, window.width(), window.height());
        } else {
            values = source.readAlignedWindow(targetEnvelope, window.width(), window.height());
        }
        cache.put(cacheKey, values);
        return values;
    }

    private RasterTileWriter createWriter(RenderRunConfig runConfig, RasterMetadata metadata, TilePlan tilePlan) throws IOException {
        CommonRunConfig writerConfig = new CommonRunConfig(
                metadata.sourceDescription(),
                runConfig.bbox(),
                runConfig.outputConfig(),
                runConfig.tilingConfig(),
                runConfig.printInfo(),
                runConfig.verbose());
        if (runConfig.outputConfig().mode() == OutputMode.SINGLE_FILE) {
            return new SingleFileRasterAccumulator(writerConfig, tilePlan, runConfig.outputBandCount(), Double.NaN, logger);
        }
        return new TiledRasterWriter(writerConfig, metadata, logger);
    }

    private PreparedRender prepare(RenderStyle style, OpenedSources openedSources) {
        List<PreparedLayer> preparedLayers = new ArrayList<>(style.layers().size());
        RenderLayerSpec firstLayer = style.layers().get(0);
        RasterMetadata referenceMetadata = openedSources.get(firstLayer.input()).metadata();
        for (RenderLayerSpec layer : style.layers()) {
            GeoToolsCogRasterSource valueSource = openedSources.get(layer.input());
            RasterMetadata valueMetadata = valueSource.metadata();
            validateCompatible(referenceMetadata, valueMetadata, "Layer input " + layer.input());
            boolean valueMatchesReferenceGrid = sameGrid(referenceMetadata, valueMetadata);
            GridAlignment valueAlignment = prepareAlignment(
                    layer.resampling(),
                    referenceMetadata,
                    valueMetadata,
                    valueMatchesReferenceGrid,
                    "Layer input " + layer.input());
            GeoToolsCogRasterSource alphaSource = null;
            RasterMetadata alphaMetadata = null;
            boolean alphaMatchesReferenceGrid = false;
            GridAlignment alphaAlignment = null;
            if (layer.alphaInput() != null) {
                alphaSource = openedSources.get(layer.alphaInput());
                alphaMetadata = alphaSource.metadata();
                validateCompatible(referenceMetadata, alphaMetadata, "Alpha input " + layer.alphaInput());
                alphaMatchesReferenceGrid = sameGrid(referenceMetadata, alphaMetadata);
                alphaAlignment = prepareAlignment(
                        layer.resampling(),
                        referenceMetadata,
                        alphaMetadata,
                        alphaMatchesReferenceGrid,
                        "Alpha input " + layer.alphaInput());
            }
            preparedLayers.add(new PreparedLayer(
                    layer,
                    RenderRamp.fromSpec(layer),
                    referenceMetadata,
                    valueSource,
                    valueMetadata,
                    valueMatchesReferenceGrid,
                    valueAlignment,
                    alphaSource,
                    alphaMetadata,
                    alphaMatchesReferenceGrid,
                    alphaAlignment));
        }
        return new PreparedRender(referenceMetadata, List.copyOf(preparedLayers));
    }

    private GridAlignment prepareAlignment(
            LayerResampling resampling,
            RasterMetadata referenceMetadata,
            RasterMetadata candidateMetadata,
            boolean matchesReferenceGrid,
            String label) {
        if (resampling != LayerResampling.MAX || matchesReferenceGrid) {
            return null;
        }
        return GridAlignment.between(candidateMetadata, referenceMetadata, label);
    }

    private OpenedSources openSources(RenderStyle style) throws IOException {
        Map<String, GeoToolsCogRasterSource> sources = new LinkedHashMap<>();
        try {
            for (RenderLayerSpec layer : style.layers()) {
                openIfAbsent(sources, layer.input());
                if (layer.alphaInput() != null) {
                    openIfAbsent(sources, layer.alphaInput());
                }
            }
            return new OpenedSources(sources);
        } catch (IOException | RuntimeException e) {
            IOException closeFailure = null;
            for (GeoToolsCogRasterSource source : sources.values()) {
                try {
                    source.close();
                } catch (IOException closeException) {
                    if (closeFailure == null) {
                        closeFailure = closeException;
                    } else {
                        closeFailure.addSuppressed(closeException);
                    }
                }
            }
            if (closeFailure != null) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private void openIfAbsent(Map<String, GeoToolsCogRasterSource> sources, String input) throws IOException {
        if (!sources.containsKey(input)) {
            sources.put(input, new GeoToolsCogRasterSource(input, logger));
        }
    }

    private void validateCompatible(RasterMetadata reference, RasterMetadata candidate, String label) {
        if (!CRS.equalsIgnoreMetadata(reference.crs(), candidate.crs())) {
            throw new IllegalArgumentException(label + " CRS does not match the reference raster.");
        }
    }

    private boolean sameGrid(RasterMetadata reference, RasterMetadata candidate) {
        return reference.width() == candidate.width()
                && reference.height() == candidate.height()
                && same(reference.resolutionX(), candidate.resolutionX())
                && same(reference.resolutionY(), candidate.resolutionY())
                && same(reference.envelope().getMinX(), candidate.envelope().getMinX())
                && same(reference.envelope().getMinY(), candidate.envelope().getMinY())
                && same(reference.envelope().getMaxX(), candidate.envelope().getMaxX())
                && same(reference.envelope().getMaxY(), candidate.envelope().getMaxY());
    }

    private boolean same(double a, double b) {
        return Math.abs(a - b) <= EPSILON;
    }

    private record PreparedRender(RasterMetadata referenceMetadata, List<PreparedLayer> layers) {
    }

    private record PreparedLayer(
            RenderLayerSpec spec,
            RenderRamp ramp,
            RasterMetadata referenceMetadata,
            GeoToolsCogRasterSource valueSource,
            RasterMetadata valueMetadata,
            boolean valueMatchesReferenceGrid,
            GridAlignment valueAlignment,
            GeoToolsCogRasterSource alphaSource,
            RasterMetadata alphaMetadata,
            boolean alphaMatchesReferenceGrid,
            GridAlignment alphaAlignment) {
    }

    private record LayerReadKey(
            GeoToolsCogRasterSource source,
            LayerResampling resampling,
            boolean matchesReferenceGrid) {
    }

    private static final class OpenedSources implements AutoCloseable {
        private final Map<String, GeoToolsCogRasterSource> sources;

        private OpenedSources(Map<String, GeoToolsCogRasterSource> sources) {
            this.sources = Map.copyOf(sources);
        }

        private GeoToolsCogRasterSource get(String input) {
            GeoToolsCogRasterSource source = sources.get(input);
            if (source == null) {
                throw new IllegalStateException("Missing opened source for input: " + input);
            }
            return source;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (GeoToolsCogRasterSource source : sources.values()) {
                try {
                    source.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
