package ch.so.agi.terrainvis.testsupport;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.raster.RasterSource;
import ch.so.agi.terrainvis.tiling.PixelWindow;

public final class BlockingRasterSource implements RasterSource {
    private final RasterMetadata metadata;
    private final float[] values;
    private final CountDownLatch readStarted = new CountDownLatch(1);
    private final CountDownLatch releaseRead = new CountDownLatch(1);

    public BlockingRasterSource(RasterMetadata metadata, float[] values) {
        this.metadata = metadata;
        this.values = values;
    }

    @Override
    public RasterMetadata metadata() {
        return metadata;
    }

    @Override
    public float[] readWindow(PixelWindow window) throws IOException {
        readStarted.countDown();
        try {
            if (!releaseRead.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting to release test raster read.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting in test raster source.", e);
        }
        return values.clone();
    }

    public boolean awaitReadStarted(long timeout, TimeUnit unit) throws InterruptedException {
        return readStarted.await(timeout, unit);
    }

    public void releaseRead() {
        releaseRead.countDown();
    }

    @Override
    public void close() {
    }
}
