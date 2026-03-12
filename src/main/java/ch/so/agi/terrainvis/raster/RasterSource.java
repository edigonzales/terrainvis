package ch.so.agi.terrainvis.raster;

import java.io.Closeable;
import java.io.IOException;

import ch.so.agi.terrainvis.tiling.PixelWindow;

public interface RasterSource extends Closeable {
    RasterMetadata metadata();

    float[] readWindow(PixelWindow window) throws IOException;
}
