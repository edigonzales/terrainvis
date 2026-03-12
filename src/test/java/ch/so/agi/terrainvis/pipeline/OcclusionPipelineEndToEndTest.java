package ch.so.agi.terrainvis.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.terrainvis.cli.OcclusionCommand;
import ch.so.agi.terrainvis.testsupport.RangeAwareHttpServer;
import ch.so.agi.terrainvis.testsupport.TestRasterFactory;
import picocli.CommandLine;

class OcclusionPipelineEndToEndTest {
    @TempDir
    Path tempDir;

    @Test
    void tiledAndSingleFileOutputsMatchForDeterministicRun() throws Exception {
        Path raster = TestRasterFactory.createRaster(
                tempDir.resolve("source.tif"),
                new float[] {
                        -9999.0f, -9999.0f, 5.0f, 5.0f,
                        -9999.0f, -9999.0f, 5.0f, 5.0f,
                        4.0f, 4.0f, 6.0f, 6.0f,
                        4.0f, 4.0f, 6.0f, 6.0f
                },
                4,
                4,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);

        Path tiledDir = tempDir.resolve("tiles");
        Path singleFile = tempDir.resolve("single.tif");

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/source.tif")) {
            int tiledExit = new CommandLine(new OcclusionCommand()).execute(
                    "-i",
                    server.uri("/source.tif").toString(),
                    "--bbox",
                    "0,0,4,4",
                    "--outputDir",
                    tiledDir.toString(),
                    "-t",
                    "2",
                    "--bufferMeters",
                    "1",
                    "-r",
                    "16",
                    "--threads",
                    "2");
            int singleExit = new CommandLine(new OcclusionCommand()).execute(
                    "-i",
                    server.uri("/source.tif").toString(),
                    "--bbox",
                    "0,0,4,4",
                    "--singleFile",
                    "-o",
                    singleFile.toString(),
                    "-t",
                    "2",
                    "--bufferMeters",
                    "1",
                    "-r",
                    "16",
                    "--threads",
                    "2");

            assertThat(tiledExit).isZero();
            assertThat(singleExit).isZero();
            assertThat(server.rangeRequests()).isGreaterThan(0);
        }

        List<Path> tiles = Files.list(tiledDir).sorted().toList();
        assertThat(tiles)
                .extracting(path -> path.getFileName().toString())
                .containsExactly(
                        "00000001_tile_00001_00000.tif",
                        "00000002_tile_00000_00001.tif",
                        "00000003_tile_00001_00001.tif");

        float[] singleValues = TestRasterFactory.readRaster(singleFile);
        float[] tiledMosaic = new float[] {
                -9999.0f, -9999.0f, TestRasterFactory.readRaster(tiles.get(0))[0], TestRasterFactory.readRaster(tiles.get(0))[1],
                -9999.0f, -9999.0f, TestRasterFactory.readRaster(tiles.get(0))[2], TestRasterFactory.readRaster(tiles.get(0))[3],
                TestRasterFactory.readRaster(tiles.get(1))[0], TestRasterFactory.readRaster(tiles.get(1))[1], TestRasterFactory.readRaster(tiles.get(2))[0], TestRasterFactory.readRaster(tiles.get(2))[1],
                TestRasterFactory.readRaster(tiles.get(1))[2], TestRasterFactory.readRaster(tiles.get(1))[3], TestRasterFactory.readRaster(tiles.get(2))[2], TestRasterFactory.readRaster(tiles.get(2))[3]
        };
        assertThat(singleValues).containsExactly(tiledMosaic);
    }

    @Test
    void startTileSkipsEarlierTilesEvenWhenTheyContainData() throws Exception {
        Path raster = TestRasterFactory.createRaster(
                tempDir.resolve("resume.tif"),
                new float[] {
                        1.0f, 1.0f, 2.0f, 2.0f,
                        1.0f, 1.0f, 2.0f, 2.0f,
                        3.0f, 3.0f, 4.0f, 4.0f,
                        3.0f, 3.0f, 4.0f, 4.0f
                },
                4,
                4,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);

        Path tiledDir = tempDir.resolve("resume-tiles");

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/resume.tif")) {
            int exit = new CommandLine(new OcclusionCommand()).execute(
                    "-i",
                    server.uri("/resume.tif").toString(),
                    "--bbox",
                    "0,0,4,4",
                    "--outputDir",
                    tiledDir.toString(),
                    "-t",
                    "2",
                    "--bufferMeters",
                    "1",
                    "--startTile",
                    "1",
                    "-r",
                    "8");

            assertThat(exit).isZero();
        }

        assertThat(Files.list(tiledDir).map(path -> path.getFileName().toString()).toList())
                .doesNotContain("00000000_tile_00000_00000.tif")
                .contains("00000001_tile_00001_00000.tif", "00000002_tile_00000_00001.tif", "00000003_tile_00001_00001.tif");
    }

    @Test
    void horizonSingleFileOutputIsStableAcrossTileSizes() throws Exception {
        Path raster = TestRasterFactory.createRaster(
                tempDir.resolve("horizon-source.tif"),
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

        Path smallTile = tempDir.resolve("horizon-small.tif");
        Path largeTile = tempDir.resolve("horizon-large.tif");

        int smallExit = new CommandLine(new OcclusionCommand()).execute(
                "-i",
                raster.toString(),
                "--bbox",
                "0,0,6,6",
                "--singleFile",
                "-o",
                smallTile.toString(),
                "--algorithm",
                "horizon",
                "--horizonDirections",
                "8",
                "--horizonRadiusMeters",
                "2",
                "-t",
                "2");
        int largeExit = new CommandLine(new OcclusionCommand()).execute(
                "-i",
                raster.toString(),
                "--bbox",
                "0,0,6,6",
                "--singleFile",
                "-o",
                largeTile.toString(),
                "--algorithm",
                "horizon",
                "--horizonDirections",
                "8",
                "--horizonRadiusMeters",
                "2",
                "-t",
                "6");

        assertThat(smallExit).isZero();
        assertThat(largeExit).isZero();
        assertThat(TestRasterFactory.readRaster(smallTile)).containsExactly(TestRasterFactory.readRaster(largeTile));
    }

    @Test
    void horizonSingleFileOutputIsStableAcrossThreadCounts() throws Exception {
        Path raster = TestRasterFactory.createRaster(
                tempDir.resolve("horizon-threads-source.tif"),
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

        Path singleThread = tempDir.resolve("horizon-single-thread.tif");
        Path multiThread = tempDir.resolve("horizon-multi-thread.tif");

        int singleExit = new CommandLine(new OcclusionCommand()).execute(
                "-i",
                raster.toString(),
                "--bbox",
                "0,0,6,6",
                "--singleFile",
                "-o",
                singleThread.toString(),
                "--algorithm",
                "horizon",
                "--horizonDirections",
                "8",
                "--horizonRadiusMeters",
                "2",
                "-t",
                "2",
                "--threads",
                "1");
        int multiExit = new CommandLine(new OcclusionCommand()).execute(
                "-i",
                raster.toString(),
                "--bbox",
                "0,0,6,6",
                "--singleFile",
                "-o",
                multiThread.toString(),
                "--algorithm",
                "horizon",
                "--horizonDirections",
                "8",
                "--horizonRadiusMeters",
                "2",
                "-t",
                "2",
                "--threads",
                "4");

        assertThat(singleExit).isZero();
        assertThat(multiExit).isZero();
        assertThat(TestRasterFactory.readRaster(singleThread)).containsExactly(TestRasterFactory.readRaster(multiThread));
    }
}
