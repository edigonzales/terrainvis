package ch.so.agi.terrainvis.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.terrainvis.cli.MainCommand;
import ch.so.agi.terrainvis.testsupport.RangeAwareHttpServer;
import ch.so.agi.terrainvis.testsupport.TestRasterFactory;
import picocli.CommandLine;

class RvtPipelineEndToEndTest {
    @TempDir
    Path tempDir;

    @Test
    void vatCombinedSingleFileProducesOutput() throws Exception {
        Path raster = createRaster("vat-source.tif");
        Path output = tempDir.resolve("vat-combined.tif");

        int exitCode = new CommandLine(new MainCommand()).execute(
                "rvt",
                "vat",
                "--input",
                raster.toString(),
                "--bbox",
                "0,0,6,6",
                "--output-mode",
                "single-file",
                "--output",
                output.toString(),
                "--tile-size",
                "3");

        assertThat(exitCode).isZero();
        assertThat(output).exists();
        float[] values = TestRasterFactory.readRaster(output);
        assertThat(values).hasSize(36);
        boolean hasData = false;
        for (float value : values) {
            if (value != -9999.0f) {
                hasData = true;
                break;
            }
        }
        assertThat(hasData).isTrue();
    }

    @Test
    void multiHillshadeWritesRequestedBandCount() throws Exception {
        Path raster = createRaster("mhs-source.tif");
        Path output = tempDir.resolve("mhs.tif");

        int exitCode = new CommandLine(new MainCommand()).execute(
                "rvt",
                "multi-hillshade",
                "--input",
                raster.toString(),
                "--bbox",
                "0,0,6,6",
                "--output-mode",
                "single-file",
                "--output",
                output.toString(),
                "--output-data-type",
                "uint8",
                "--tile-size",
                "3",
                "--directions",
                "4");

        assertThat(exitCode).isZero();
        assertThat(TestRasterFactory.readBandCount(output)).isEqualTo(4);
    }

    @Test
    void mstpWritesRgbOutput() throws Exception {
        Path raster = createRaster("mstp-source.tif");
        Path output = tempDir.resolve("mstp.tif");

        int exitCode = new CommandLine(new MainCommand()).execute(
                "rvt",
                "mstp",
                "--input",
                raster.toString(),
                "--bbox",
                "0,0,6,6",
                "--output-mode",
                "single-file",
                "--output",
                output.toString(),
                "--tile-size",
                "3");

        assertThat(exitCode).isZero();
        assertThat(TestRasterFactory.readBandCount(output)).isEqualTo(3);
    }

    @Test
    void svfWorksOnRemoteCogInput() throws Exception {
        Path raster = createRaster("remote-svf.tif");
        Path output = tempDir.resolve("remote-svf.tif.out");

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/remote-svf.tif")) {
            int exitCode = new CommandLine(new MainCommand()).execute(
                    "rvt",
                    "svf",
                    "--input",
                    server.uri("/remote-svf.tif").toString(),
                    "--bbox",
                    "0,0,6,6",
                    "--output-mode",
                    "tile-files",
                    "--output",
                    output.toString(),
                    "--tile-size",
                    "3");

            assertThat(exitCode).isZero();
            assertThat(server.rangeRequests()).isGreaterThan(0);
        }

        List<Path> tiles = Files.list(output).toList();
        assertThat(tiles).isNotEmpty();
    }

    private Path createRaster(String filename) throws Exception {
        return TestRasterFactory.createRaster(
                tempDir.resolve(filename),
                new float[] {
                        0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f,
                        0.0f, 2.0f, 3.0f, 2.0f, 0.0f, 0.0f,
                        1.0f, 3.0f, 6.0f, 3.0f, 1.0f, 0.0f,
                        0.0f, 2.0f, 3.0f, 2.0f, 0.0f, 0.0f,
                        0.0f, 1.0f, 2.0f, 1.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
                },
                6,
                6,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
    }
}
