package ch.so.agi.terrainvis.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.terrainvis.cli.MainCommand;
import ch.so.agi.terrainvis.testsupport.RangeAwareHttpServer;
import ch.so.agi.terrainvis.testsupport.TestRasterFactory;
import picocli.CommandLine;

class RenderPipelineEndToEndTest {
    private static final Pattern TILE_NAME = Pattern.compile("\\d+_tile_(\\d+)_(\\d+)\\.tif");

    @TempDir
    Path tempDir;

    @Test
    void tiledAndSingleFileOutputsMatch() throws Exception {
        Path raster = createBaseRaster("render-source.tif");
        Path tiledDir = tempDir.resolve("tiles");
        Path singleFile = tempDir.resolve("single.tif");
        Path style = writeSingleLayerStyle(tempDir.resolve("style.json"), raster.toString(), null, "#000000", "#FFFFFF", "normal", 1.0);

        int tiledExit = new CommandLine(new MainCommand()).execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,4,4",
                "--output-mode",
                "tile-files",
                "--output",
                tiledDir.toString(),
                "--tile-size",
                "2");
        int singleExit = new CommandLine(new MainCommand()).execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,4,4",
                "--output-mode",
                "single-file",
                "--output",
                singleFile.toString(),
                "--tile-size",
                "2");

        assertThat(tiledExit).isZero();
        assertThat(singleExit).isZero();
        assertThat(TestRasterFactory.readBandCount(singleFile)).isEqualTo(3);
        assertThat(TestRasterFactory.readRasterBands(singleFile)).isEqualTo(mosaicTiles(tiledDir, 4, 4, 2));
    }

    @Test
    void greenRampExampleProducesExpectedRgbValues() throws Exception {
        Path raster = createBaseRaster("forest-index.tif");
        Path output = tempDir.resolve("forest-green.tif");
        Path style = writeSingleLayerStyle(
                tempDir.resolve("green-style.json"),
                raster.toString(),
                null,
                "#CBEA9A",
                "#1F6B2A",
                "normal",
                1.0);

        int exitCode = new CommandLine(new MainCommand()).execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,4,4",
                "--output-mode",
                "single-file",
                "--output",
                output.toString(),
                "--tile-size",
                "2");

        assertThat(exitCode).isZero();
        float[][] bands = TestRasterFactory.readRasterBands(output);
        assertThat(Arrays.copyOfRange(bands[0], 0, 4)).containsExactly(203.0f, 117.0f, 31.0f, 117.0f);
        assertThat(Arrays.copyOfRange(bands[1], 0, 4)).containsExactly(234.0f, 170.0f, 107.0f, 170.0f);
        assertThat(Arrays.copyOfRange(bands[2], 0, 4)).containsExactly(154.0f, 98.0f, 42.0f, 98.0f);
    }

    @Test
    void multiplyExampleProducesExpectedRgbValues() throws Exception {
        Path baseRelief = TestRasterFactory.createRaster(
                tempDir.resolve("base-relief.tif"),
                new float[] {1.0f},
                1,
                1,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path occlusion = TestRasterFactory.createRaster(
                tempDir.resolve("occlusion.tif"),
                new float[] {1.0f},
                1,
                1,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path output = tempDir.resolve("multiply.tif");
        Path style = writeTwoLayerStyle(
                tempDir.resolve("multiply-style.json"),
                """
                {
                  "input": "%s",
                  "valueMin": 0.0,
                  "valueMax": 1.0,
                  "colorFrom": "#F7E6C4",
                  "colorTo": "#8B6F47",
                  "blendMode": "normal",
                  "opacity": 1.0
                }
                """.formatted(escaped(baseRelief.toString())),
                """
                {
                  "input": "%s",
                  "valueMin": 0.0,
                  "valueMax": 1.0,
                  "colorFrom": "#FFFFFF",
                  "colorTo": "#404040",
                  "blendMode": "multiply",
                  "opacity": 0.7
                }
                """.formatted(escaped(occlusion.toString())));

        int exitCode = new CommandLine(new MainCommand()).execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,1,1",
                "--output-mode",
                "single-file",
                "--output",
                output.toString(),
                "--tile-size",
                "1");

        assertThat(exitCode).isZero();
        float[][] bands = TestRasterFactory.readRasterBands(output);
        assertThat(bands[0]).containsExactly(66.0f);
        assertThat(bands[1]).containsExactly(53.0f);
        assertThat(bands[2]).containsExactly(34.0f);
    }

    @Test
    void transparentTileIsSkippedAndRgbaOutputUsesFourBands() throws Exception {
        Path raster = TestRasterFactory.createRaster(
                tempDir.resolve("rgba-source.tif"),
                new float[] {
                        1.0f, 1.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f
                },
                4,
                4,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path alpha = TestRasterFactory.createRaster(
                tempDir.resolve("rgba-alpha.tif"),
                new float[] {
                        0.0f, 0.0f, 1.0f, 1.0f,
                        0.0f, 0.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f
                },
                4,
                4,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path style = writeSingleLayerStyle(tempDir.resolve("rgba-style.json"), raster.toString(), alpha.toString(), "#FFF2B2", "#D95F0E", "normal", 1.0);
        Path tiledDir = tempDir.resolve("rgba-tiles");

        int exitCode = new CommandLine(new MainCommand()).execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,4,4",
                "--output-mode",
                "tile-files",
                "--output",
                tiledDir.toString(),
                "--tile-size",
                "2",
                "--with-alpha");

        assertThat(exitCode).isZero();
        List<String> outputs = Files.list(tiledDir).map(path -> path.getFileName().toString()).sorted().toList();
        assertThat(outputs)
                .doesNotContain("00000000_tile_00000_00000.tif")
                .contains("00000001_tile_00001_00000.tif", "00000002_tile_00000_00001.tif", "00000003_tile_00001_00001.tif");
        assertThat(TestRasterFactory.readBandCount(tiledDir.resolve("00000001_tile_00001_00000.tif"))).isEqualTo(4);
    }

    @Test
    void startTileSkipsEarlierTiles() throws Exception {
        Path raster = createBaseRaster("start-source.tif");
        Path style = writeSingleLayerStyle(tempDir.resolve("start-style.json"), raster.toString(), null, "#000000", "#FFFFFF", "normal", 1.0);
        Path tiledDir = tempDir.resolve("start-tiles");

        int exitCode = new CommandLine(new MainCommand()).execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,4,4",
                "--output-mode",
                "tile-files",
                "--output",
                tiledDir.toString(),
                "--tile-size",
                "2",
                "--start-tile",
                "1");

        assertThat(exitCode).isZero();
        assertThat(Files.list(tiledDir).map(path -> path.getFileName().toString()).toList())
                .doesNotContain("00000000_tile_00000_00000.tif")
                .contains("00000001_tile_00001_00000.tif", "00000002_tile_00000_00001.tif", "00000003_tile_00001_00001.tif");
    }

    @Test
    void remoteLayerInputTriggersRangeRequests() throws Exception {
        Path raster = createBaseRaster("remote-render.tif");
        Path tiledDir = tempDir.resolve("remote-tiles");

        try (RangeAwareHttpServer server = new RangeAwareHttpServer(raster, "/remote-render.tif")) {
            Path style = writeSingleLayerStyle(
                    tempDir.resolve("remote-style.json"),
                    server.uri("/remote-render.tif").toString(),
                    null,
                    "#000000",
                    "#FFFFFF",
                    "normal",
                    1.0);

            int exitCode = new CommandLine(new MainCommand()).execute(
                    "render",
                    "compose",
                    "--style",
                    style.toString(),
                    "--bbox",
                    "0,0,4,4",
                    "--output-mode",
                    "tile-files",
                    "--output",
                    tiledDir.toString(),
                    "--tile-size",
                    "2");

            assertThat(exitCode).isZero();
            assertThat(server.rangeRequests()).isGreaterThan(0);
        }
    }

    @Test
    void misalignedLayersAreRejectedBeforeProcessing() throws Exception {
        Path aligned = createBaseRaster("aligned.tif");
        Path shifted = TestRasterFactory.createRaster(
                tempDir.resolve("shifted.tif"),
                new float[] {
                        0.0f, 0.5f, 1.0f, 0.5f,
                        0.5f, 1.0f, 0.0f, 0.5f,
                        1.0f, 0.5f, 0.0f, 0.5f,
                        0.5f, 0.0f, 0.5f, 1.0f
                },
                4,
                4,
                10.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path style = tempDir.resolve("misaligned-style.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "%s",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#000000",
                      "colorTo": "#FFFFFF",
                      "blendMode": "normal",
                      "opacity": 1.0
                    },
                    {
                      "input": "%s",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#00FF00",
                      "colorTo": "#008800",
                      "blendMode": "normal",
                      "opacity": 1.0
                    }
                  ]
                }
                """.formatted(aligned.toString().replace("\\", "\\\\"), shifted.toString().replace("\\", "\\\\")));

        int exitCode = new CommandLine(new MainCommand()).execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,4,4",
                "--output-mode",
                "single-file",
                "--output",
                tempDir.resolve("misaligned.tif").toString());

        assertThat(exitCode).isNotZero();
    }

    private Path createBaseRaster(String filename) throws Exception {
        return TestRasterFactory.createRaster(
                tempDir.resolve(filename),
                new float[] {
                        0.0f, 0.5f, 1.0f, 0.5f,
                        0.5f, 1.0f, 0.0f, 0.5f,
                        1.0f, 0.5f, 0.0f, 0.5f,
                        0.5f, 0.0f, 0.5f, 1.0f
                },
                4,
                4,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
    }

    private Path writeSingleLayerStyle(
            Path output,
            String input,
            String alphaInput,
            String colorFrom,
            String colorTo,
            String blendMode,
            double opacity) throws Exception {
        String alphaJson = alphaInput == null ? "" : """
                          "alphaInput": "%s",
                """.formatted(alphaInput.replace("\\", "\\\\"));
        Files.writeString(output, """
                {
                  "layers": [
                    {
                      "input": "%s",
                %s      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "%s",
                      "colorTo": "%s",
                      "blendMode": "%s",
                      "opacity": %.1f
                    }
                  ]
                }
                """.formatted(
                input.replace("\\", "\\\\"),
                alphaJson,
                colorFrom,
                colorTo,
                blendMode,
                opacity));
        return output;
    }

    private Path writeTwoLayerStyle(Path output, String firstLayer, String secondLayer) throws Exception {
        Files.writeString(output, """
                {
                  "layers": [
                    %s,
                    %s
                  ]
                }
                """.formatted(firstLayer.stripIndent().trim(), secondLayer.stripIndent().trim()));
        return output;
    }

    private float[][] mosaicTiles(Path tiledDir, int width, int height, int tileSize) throws Exception {
        float[][] mosaic = new float[3][width * height];
        for (Path tile : Files.list(tiledDir).toList()) {
            Matcher matcher = TILE_NAME.matcher(tile.getFileName().toString());
            assertThat(matcher.matches()).isTrue();
            int tileColumn = Integer.parseInt(matcher.group(1));
            int tileRow = Integer.parseInt(matcher.group(2));
            float[][] tileBands = TestRasterFactory.readRasterBands(tile);
            int tileWidth = Math.min(tileSize, width - (tileColumn * tileSize));
            int tileHeight = Math.min(tileSize, height - (tileRow * tileSize));
            for (int band = 0; band < mosaic.length; band++) {
                for (int y = 0; y < tileHeight; y++) {
                    for (int x = 0; x < tileWidth; x++) {
                        int sourceIndex = (y * tileWidth) + x;
                        int targetX = tileColumn * tileSize + x;
                        int targetY = tileRow * tileSize + y;
                        mosaic[band][(targetY * width) + targetX] = tileBands[band][sourceIndex];
                    }
                }
            }
        }
        return mosaic;
    }

    private String escaped(String value) {
        return value.replace("\\", "\\\\");
    }
}
