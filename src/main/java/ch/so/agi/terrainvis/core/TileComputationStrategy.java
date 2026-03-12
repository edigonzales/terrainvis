package ch.so.agi.terrainvis.core;

import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.TileRequest;

interface TileComputationStrategy {
    TileComputationResult process(
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            RunConfig runConfig,
            int threads);
}
