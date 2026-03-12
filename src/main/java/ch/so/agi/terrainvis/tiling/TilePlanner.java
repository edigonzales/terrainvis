package ch.so.agi.terrainvis.tiling;

import java.util.ArrayList;
import java.util.List;

import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.raster.RasterMetadata;

public final class TilePlanner {
    public TilePlan plan(
            RasterMetadata metadata,
            BBox bbox,
            int tileSizePixels,
            int bufferPixelsX,
            int bufferPixelsY) {
        PixelWindow requestedWindow = metadata.snapAndClip(bbox);
        List<TileRequest> tiles = new ArrayList<>();
        int id = 0;
        int tileRow = 0;
        for (int y = requestedWindow.y(); y < requestedWindow.endY(); y += tileSizePixels) {
            int tileHeight = Math.min(tileSizePixels, requestedWindow.endY() - y);
            int tileColumn = 0;
            for (int x = requestedWindow.x(); x < requestedWindow.endX(); x += tileSizePixels) {
                int tileWidth = Math.min(tileSizePixels, requestedWindow.endX() - x);
                PixelWindow core = new PixelWindow(x, y, tileWidth, tileHeight);
                int bufferedX = Math.max(0, x - bufferPixelsX);
                int bufferedY = Math.max(0, y - bufferPixelsY);
                int bufferedEndX = Math.min(metadata.width(), core.endX() + bufferPixelsX);
                int bufferedEndY = Math.min(metadata.height(), core.endY() + bufferPixelsY);
                PixelWindow buffered = new PixelWindow(
                        bufferedX,
                        bufferedY,
                        bufferedEndX - bufferedX,
                        bufferedEndY - bufferedY);
                TileWindow window = new TileWindow(core, buffered, x - bufferedX, y - bufferedY);
                tiles.add(new TileRequest(id++, tileColumn, tileRow, window));
                tileColumn++;
            }
            tileRow++;
        }
        return new TilePlan(requestedWindow, metadata.toEnvelope(requestedWindow), tiles);
    }
}
