package ch.so.agi.terrainvis.testsupport;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;

public final class ConcurrentReadProbeRasterSource extends WindowedRasterSource {
    private final CountDownLatch concurrentReadStarted = new CountDownLatch(1);
    private final AtomicInteger activeReads = new AtomicInteger();
    private final AtomicInteger maxConcurrentReads = new AtomicInteger();
    private final long concurrentReadTimeoutMillis;

    public ConcurrentReadProbeRasterSource(RasterMetadata metadata, float[] values, long concurrentReadTimeoutMillis) {
        super(metadata, values);
        this.concurrentReadTimeoutMillis = concurrentReadTimeoutMillis;
    }

    @Override
    public float[] readWindow(PixelWindow window) throws IOException {
        int active = activeReads.incrementAndGet();
        maxConcurrentReads.accumulateAndGet(active, Math::max);
        try {
            if (active == 1) {
                awaitConcurrentRead();
            } else {
                concurrentReadStarted.countDown();
            }
            return super.readWindow(window);
        } finally {
            activeReads.decrementAndGet();
        }
    }

    public int maxConcurrentReads() {
        return maxConcurrentReads.get();
    }

    private void awaitConcurrentRead() throws IOException {
        try {
            if (!concurrentReadStarted.await(concurrentReadTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IOException("Timed out waiting for a concurrent raster read.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for a concurrent raster read.", e);
        }
    }
}
