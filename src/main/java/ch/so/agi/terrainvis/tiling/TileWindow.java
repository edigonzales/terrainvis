package ch.so.agi.terrainvis.tiling;

public record TileWindow(PixelWindow coreWindow, PixelWindow bufferedWindow, int coreOffsetX, int coreOffsetY) {
    public TileWindow {
        if (coreWindow == null || bufferedWindow == null) {
            throw new IllegalArgumentException("TileWindow requires both core and buffered windows");
        }
        if (coreOffsetX < 0 || coreOffsetY < 0) {
            throw new IllegalArgumentException("Core offsets must be >= 0");
        }
    }
}
