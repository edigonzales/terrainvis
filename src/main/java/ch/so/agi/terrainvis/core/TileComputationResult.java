package ch.so.agi.terrainvis.core;

import ch.so.agi.terrainvis.tiling.TileRequest;

public record TileComputationResult(TileRequest tileRequest, float[] data, boolean skipped, int validPixelCount) {
    public TileComputationResult {
        if (tileRequest == null || data == null) {
            throw new IllegalArgumentException("TileComputationResult requires tile metadata and data");
        }
    }
}
