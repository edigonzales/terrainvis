package ch.so.agi.terrainvis.tiling;

public record PixelWindow(int x, int y, int width, int height) {
    public PixelWindow {
        if (x < 0 || y < 0 || width < 0 || height < 0) {
            throw new IllegalArgumentException("PixelWindow values must be >= 0");
        }
    }

    public int endX() {
        return x + width;
    }

    public int endY() {
        return y + height;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }
}
