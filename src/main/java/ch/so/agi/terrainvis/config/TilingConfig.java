package ch.so.agi.terrainvis.config;

public record TilingConfig(
        int tileSizePixels,
        Integer bufferPixelsOverride,
        Double bufferMetersOverride,
        int startTile,
        int threads) {

    public TilingConfig {
        if (tileSizePixels <= 0) {
            throw new IllegalArgumentException("tileSizePixels must be > 0");
        }
        if (bufferPixelsOverride != null && bufferPixelsOverride < 0) {
            throw new IllegalArgumentException("bufferPixelsOverride must be >= 0");
        }
        if (bufferMetersOverride != null && bufferMetersOverride < 0.0) {
            throw new IllegalArgumentException("bufferMetersOverride must be >= 0");
        }
        if (bufferPixelsOverride != null && bufferMetersOverride != null) {
            throw new IllegalArgumentException("Use either --buffer-px or --buffer-m, not both");
        }
        if (startTile < 0) {
            throw new IllegalArgumentException("startTile must be >= 0");
        }
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be > 0");
        }
    }

    public int defaultBufferPixels() {
        return tileSizePixels / 3;
    }
}
