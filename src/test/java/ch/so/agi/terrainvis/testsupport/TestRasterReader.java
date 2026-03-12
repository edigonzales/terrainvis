package ch.so.agi.terrainvis.testsupport;

import java.io.IOException;

import ch.so.agi.terrainvis.raster.GeoToolsCogRasterSource;
import ch.so.agi.terrainvis.tiling.PixelWindow;

public final class TestRasterReader implements AutoCloseable {
    private final GeoToolsCogRasterSource delegate;

    public TestRasterReader(String inputLocation) throws IOException {
        this.delegate = new GeoToolsCogRasterSource(inputLocation);
    }

    public float[] readAll() throws IOException {
        return delegate.readWindow(new PixelWindow(0, 0, delegate.metadata().width(), delegate.metadata().height()));
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
