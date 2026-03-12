package ch.so.agi.terrainvis.output;

import java.io.Closeable;
import java.io.IOException;

public interface RasterTileWriter extends Closeable {
    void write(RasterTileResult result) throws IOException;

    default void finish() throws IOException {
    }
}
