package ch.so.agi.terrainvis.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.terrainvis.testsupport.CapturedCommandOutput;
import ch.so.agi.terrainvis.testsupport.TestRasterFactory;
import picocli.CommandLine;

class OcclusionCommandLoggingTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultRunPrintsHighLevelProgressToStdout() throws Exception {
        Path raster = createLocalRaster("default-logging.tif");
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new OcclusionCommand(fixedClock()));
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute(
                "-i",
                raster.toString(),
                "--bbox",
                "0,0,2,2",
                "--outputDir",
                tempDir.resolve("tiles").toString(),
                "-t",
                "2",
                "-r",
                "8");

        assertThat(exitCode).isZero();
        assertThat(output.stdout())
                .contains("Run start:")
                .contains("Resolved config:")
                .contains("Tile plan:")
                .contains("Submitting 1 tile(s) to")
                .contains("Completed tile 1/1:")
                .contains("Finished run:");
        assertThat(output.stderr()).isBlank();
    }

    @Test
    void verboseRunAddsDetailedLogsAndKeepsInfoOutput() throws Exception {
        Path raster = createLocalRaster("verbose-logging.tif");
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new OcclusionCommand(fixedClock()));
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute(
                "-i",
                raster.toString(),
                "--bbox",
                "0,0,2,2",
                "--outputDir",
                tempDir.resolve("verbose-tiles").toString(),
                "-o",
                tempDir.resolve("ignored.tif").toString(),
                "--verbose",
                "--info",
                "-t",
                "2",
                "-r",
                "8");

        assertThat(exitCode).isZero();
        assertThat(output.stdout())
                .contains("Source: " + raster)
                .contains("Threads:")
                .contains("[VERBOSE] Initializing local raster source:")
                .contains("[VERBOSE] Queued tile:")
                .contains("[VERBOSE] Reading buffered window:")
                .contains("[VERBOSE] Writing tiled output for tileId=0");
        assertThat(output.stderr()).contains("Ignoring -o because tiled mode is the default.");
    }

    @Test
    void helpIncludesVerboseFlag() {
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new OcclusionCommand(fixedClock()));
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute("--help");

        assertThat(exitCode).isZero();
        assertThat(output.stdout()).contains("--verbose");
    }

    @Test
    void horizonModeWarnsWhenRayCountIsExplicitlySet() throws Exception {
        Path raster = createLocalRaster("horizon-warning.tif");
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new OcclusionCommand(fixedClock()));
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute(
                "-i",
                raster.toString(),
                "--bbox",
                "0,0,2,2",
                "--outputDir",
                tempDir.resolve("horizon-tiles").toString(),
                "--algorithm",
                "horizon",
                "-r",
                "8",
                "--horizonDirections",
                "8");

        assertThat(exitCode).isZero();
        assertThat(output.stderr()).contains("Ignoring -r because --algorithm=horizon does not use rays.");
    }

    @Test
    void horizonModeRejectsBounceConfiguration() throws Exception {
        Path raster = createLocalRaster("horizon-bounce-error.tif");
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new OcclusionCommand(fixedClock()));
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute(
                "-i",
                raster.toString(),
                "--bbox",
                "0,0,2,2",
                "--algorithm",
                "horizon",
                "-B",
                "1");

        assertThat(exitCode).isEqualTo(1);
        assertThat(output.stderr()).contains("--algorithm=horizon supports only maxBounces=0");
    }

    private Path createLocalRaster(String filename) throws Exception {
        return TestRasterFactory.createRaster(
                tempDir.resolve(filename),
                new float[] {
                        1.0f, 2.0f,
                        3.0f, 4.0f
                },
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-03-09T12:00:00Z"), ZoneId.of("Europe/Zurich"));
    }
}
