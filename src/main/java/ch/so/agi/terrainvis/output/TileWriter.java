package ch.so.agi.terrainvis.output;

import java.io.Closeable;
import java.io.IOException;

import ch.so.agi.terrainvis.core.TileComputationResult;

public interface TileWriter extends Closeable {
    void write(TileComputationResult result) throws IOException;

    default void finish() throws IOException {
    }
}
