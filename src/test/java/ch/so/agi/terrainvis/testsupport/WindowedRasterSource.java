package ch.so.agi.terrainvis.testsupport;

import java.io.IOException;

import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.raster.RasterSource;
import ch.so.agi.terrainvis.tiling.PixelWindow;

public class WindowedRasterSource implements RasterSource {
    private final RasterMetadata metadata;
    private final float[] values;

    public WindowedRasterSource(RasterMetadata metadata, float[] values) {
        this.metadata = metadata;
        this.values = values.clone();
        int expectedLength = metadata.width() * metadata.height();
        if (values.length != expectedLength) {
            throw new IllegalArgumentException("values length must match raster dimensions");
        }
    }

    @Override
    public RasterMetadata metadata() {
        return metadata;
    }

    @Override
    public float[] readWindow(PixelWindow window) throws IOException {
        if (window.endX() > metadata.width() || window.endY() > metadata.height()) {
            throw new IOException("Requested window exceeds raster bounds.");
        }
        float[] subset = new float[window.width() * window.height()];
        for (int y = 0; y < window.height(); y++) {
            int sourceIndex = index(window.x(), window.y() + y, metadata.width());
            int destinationIndex = y * window.width();
            System.arraycopy(values, sourceIndex, subset, destinationIndex, window.width());
        }
        return subset;
    }

    @Override
    public void close() {
    }

    private int index(int x, int y, int width) {
        return y * width + x;
    }
}
