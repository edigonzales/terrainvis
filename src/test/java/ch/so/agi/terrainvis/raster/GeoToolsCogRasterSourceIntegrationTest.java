package ch.so.agi.terrainvis.raster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.terrainvis.testsupport.RangeAwareHttpServer;
import ch.so.agi.terrainvis.testsupport.TestRasterFactory;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.util.ConsoleLogger;

class GeoToolsCogRasterSourceIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void readsRemoteSubsetViaRangeRequests() throws Exception {
        Path raster = TestRasterFactory.createRaster(
                tempDir.resolve("remote.tif"),
                new float[] {
                        1, 2, 3, 4,
                        5, 6, 7, 8,
                        9, 10, 11, 12,
                        13, 14, 15, 16
                },
                4,
                4,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        assertThat(Files.exists(raster)).isTrue();

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/remote.tif");
                GeoToolsCogRasterSource source = new GeoToolsCogRasterSource(server.uri("/remote.tif").toString())) {
            float[] subset = source.readWindow(new PixelWindow(1, 1, 2, 2));

            assertThat(subset).containsExactly(6.0f, 7.0f, 10.0f, 11.0f);
            assertThat(server.rangeRequests()).isGreaterThan(0);
            assertThat(server.seenRanges()).allMatch(range -> range.startsWith("bytes="));
        }
    }

    @Test
    void retriesRemoteSubsetReadAfterCorruptRangeResponse() throws Exception {
        Path raster = createRemoteTestRaster("retry-once.tif");
        List<Long> retryDelays = new ArrayList<>();

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/retry-once.tif");
                GeoToolsCogRasterSource source = new GeoToolsCogRasterSource(
                        server.uri("/retry-once.tif").toString(),
                        silentLogger(),
                        retryDelays::add)) {
            server.corruptNextRangeResponses(1);

            float[] subset = source.readWindow(new PixelWindow(1, 1, 2, 2));

            assertThat(subset).containsExactly(6.0f, 7.0f, 10.0f, 11.0f);
            assertThat(server.corruptedRangeResponses()).isEqualTo(1);
            assertThat(server.rangeRequests()).isGreaterThan(1);
            assertThat(retryDelays).hasSize(1);
            assertThat(retryDelays.get(0)).isBetween(750L, 1_250L);
        }
    }

    @Test
    void failsRemoteSubsetReadAfterExhaustingRetriesOnCorruptRangeResponses() throws Exception {
        Path raster = createRemoteTestRaster("retry-exhausted.tif");
        List<Long> retryDelays = new ArrayList<>();

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/retry-exhausted.tif");
                GeoToolsCogRasterSource source = new GeoToolsCogRasterSource(
                        server.uri("/retry-exhausted.tif").toString(),
                        silentLogger(),
                        retryDelays::add)) {
            server.alwaysCorruptRangeResponses();

            assertThatThrownBy(() -> source.readWindow(new PixelWindow(1, 1, 2, 2)))
                    .isInstanceOf(IOException.class);
            assertThat(server.corruptedRangeResponses()).isGreaterThanOrEqualTo(3);
            assertThat(retryDelays).hasSize(2);
            assertThat(retryDelays.get(0)).isBetween(750L, 1_250L);
            assertThat(retryDelays.get(1)).isBetween(1_500L, 2_500L);
        }
    }

    @Test
    void failsWhenRetrySleepIsInterrupted() throws Exception {
        Path raster = createRemoteTestRaster("retry-interrupted.tif");

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/retry-interrupted.tif");
                GeoToolsCogRasterSource source = new GeoToolsCogRasterSource(
                        server.uri("/retry-interrupted.tif").toString(),
                        silentLogger(),
                        delayMillis -> {
                            throw new InterruptedException("simulated interrupt");
                        })) {
            server.corruptNextRangeResponses(1);

            assertThatThrownBy(() -> source.readWindow(new PixelWindow(1, 1, 2, 2)))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Interrupted while waiting to retry remote raster read")
                    .cause()
                    .isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private Path createRemoteTestRaster(String filename) throws Exception {
        return TestRasterFactory.createRaster(
                tempDir.resolve(filename),
                new float[] {
                        1, 2, 3, 4,
                        5, 6, 7, 8,
                        9, 10, 11, 12,
                        13, 14, 15, 16
                },
                4,
                4,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
    }

    private ConsoleLogger silentLogger() {
        return new ConsoleLogger(
                new PrintWriter(new StringWriter()),
                new PrintWriter(new StringWriter()),
                Clock.systemUTC(),
                false);
    }
}
