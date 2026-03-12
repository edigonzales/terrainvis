package ch.so.agi.terrainvis.output;

import ch.so.agi.terrainvis.tiling.TileRequest;

public record RasterTileResult(TileRequest tileRequest, RasterBlock block, boolean skipped, int validPixelCount) {
    public RasterTileResult {
        if (tileRequest == null || block == null) {
            throw new IllegalArgumentException("tileRequest and block are required");
        }
    }
}
