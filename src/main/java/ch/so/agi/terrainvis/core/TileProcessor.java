package ch.so.agi.terrainvis.core;

import ch.so.agi.terrainvis.config.AlgorithmMode;
import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.HorizonParameters;
import ch.so.agi.terrainvis.config.LightingParameters;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.TileRequest;

public final class TileProcessor {
    private final TileComputationStrategy exactProcessor;
    private final TileComputationStrategy horizonProcessor;

    public TileProcessor() {
        this(new ExactTileProcessor(), new HorizonApproxProcessor());
    }

    TileProcessor(TileComputationStrategy exactProcessor, TileComputationStrategy horizonProcessor) {
        this.exactProcessor = exactProcessor;
        this.horizonProcessor = horizonProcessor;
    }

    public TileComputationResult process(
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            RunConfig runConfig,
            int threads) {
        TileComputationStrategy processor = runConfig.algorithmMode() == AlgorithmMode.HORIZON ? horizonProcessor : exactProcessor;
        return processor.process(request, metadata, bufferedValues, runConfig, threads);
    }

    TileComputationResult process(
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            LightingParameters lightingParameters,
            double exaggeration,
            int threads) {
        RunConfig runConfig = new RunConfig(
                metadata.sourceDescription(),
                new BBox(0.0, 0.0, metadata.width() * metadata.resolutionX(), metadata.height() * metadata.resolutionY()),
                AlgorithmMode.EXACT,
                OutputMode.TILE_FILES,
                java.nio.file.Path.of("output.tif"),
                java.nio.file.Path.of("output_tiles"),
                request.window().coreWindow().width(),
                null,
                null,
                0,
                threads,
                false,
                false,
                false,
                exaggeration,
                lightingParameters,
                HorizonParameters.defaults());
        return process(request, metadata, bufferedValues, runConfig, threads);
    }
}
