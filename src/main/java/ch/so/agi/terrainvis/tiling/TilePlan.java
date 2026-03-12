package ch.so.agi.terrainvis.tiling;

import java.util.List;

import org.geotools.geometry.jts.ReferencedEnvelope;

public record TilePlan(PixelWindow requestedWindow, ReferencedEnvelope requestedEnvelope, List<TileRequest> tiles) {
    public TilePlan {
        if (requestedWindow == null || requestedEnvelope == null || tiles == null) {
            throw new IllegalArgumentException("TilePlan fields must not be null");
        }
        tiles = List.copyOf(tiles);
    }
}
