package ch.so.agi.terrainvis.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.terrainvis.cli.MainCommand;
import ch.so.agi.terrainvis.output.GeoTiffSupport;
import ch.so.agi.terrainvis.raster.GeoToolsCogRasterSource;
import ch.so.agi.terrainvis.testsupport.CapturedCommandOutput;
import ch.so.agi.terrainvis.testsupport.TestRasterFactory;
import picocli.CommandLine;

class RenderGridAlignerEndToEndTest {
    @TempDir
    Path tempDir;

    @Test
    void alignGridProducesExpectedMaxAggregatedValues() throws Exception {
        Path input = TestRasterFactory.createRaster(
                tempDir.resolve("input.tif"),
                new float[] {
                        0.0f, 1.0f, 2.0f, 3.0f,
                        4.0f, 5.0f, 6.0f, 7.0f,
                        8.0f, 9.0f, 10.0f, 11.0f,
                        12.0f, 13.0f, 14.0f, 15.0f
                },
                4,
                4,
                0.0,
                0.0,
                0.5,
                "EPSG:2056",
                -9999.0);
        Path reference = TestRasterFactory.createRaster(
                tempDir.resolve("reference.tif"),
                new float[] {0.0f, 0.0f, 0.0f, 0.0f},
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path output = tempDir.resolve("aligned.tif");

        CommandResult result = execute(
                "render",
                "align-grid",
                "--input",
                input.toString(),
                "--reference",
                reference.toString(),
                "--output",
                output.toString(),
                "--tile-size",
                "1");

        assertThat(result.exitCode()).isZero();
        assertThat(TestRasterFactory.readRaster(output)).containsExactly(5.0f, 7.0f, 13.0f, 15.0f);
        try (GeoToolsCogRasterSource source = new GeoToolsCogRasterSource(output.toString())) {
            assertThat(source.metadata().width()).isEqualTo(2);
            assertThat(source.metadata().height()).isEqualTo(2);
            assertThat(source.metadata().resolutionX()).isEqualTo(1.0);
            assertThat(source.metadata().resolutionY()).isEqualTo(1.0);
        }
    }

    @Test
    void alignGridPropagatesNoDataWhenAllSourceSamplesAreMissing() throws Exception {
        float noData = -9999.0f;
        Path input = TestRasterFactory.createRaster(
                tempDir.resolve("input-nodata.tif"),
                new float[] {
                        noData, noData, 2.0f, 3.0f,
                        noData, noData, noData, 7.0f,
                        8.0f, noData, noData, noData,
                        12.0f, noData, noData, noData
                },
                4,
                4,
                0.0,
                0.0,
                0.5,
                "EPSG:2056",
                noData);
        Path reference = TestRasterFactory.createRaster(
                tempDir.resolve("reference-nodata.tif"),
                new float[] {0.0f, 0.0f, 0.0f, 0.0f},
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                noData);
        Path output = tempDir.resolve("aligned-nodata.tif");

        CommandResult result = execute(
                "render",
                "align-grid",
                "--input",
                input.toString(),
                "--reference",
                reference.toString(),
                "--output",
                output.toString(),
                "--tile-size",
                "1");

        assertThat(result.exitCode()).isZero();
        assertThat(TestRasterFactory.readRaster(output)).containsExactly(noData, 7.0f, 12.0f, noData);
    }

    @Test
    void alignGridRejectsDifferentCrs() throws Exception {
        Path input = TestRasterFactory.createRaster(
                tempDir.resolve("input-crs.tif"),
                new float[] {1.0f, 2.0f, 3.0f, 4.0f},
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path reference = TestRasterFactory.createRaster(
                tempDir.resolve("reference-crs.tif"),
                new float[] {0.0f, 0.0f, 0.0f, 0.0f},
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:3857",
                -9999.0);

        CommandResult result = execute(
                "render",
                "align-grid",
                "--input",
                input.toString(),
                "--reference",
                reference.toString(),
                "--output",
                tempDir.resolve("ignored-crs.tif").toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Input CRS does not match the reference raster.");
    }

    @Test
    void alignGridRejectsDifferentExtent() throws Exception {
        Path input = TestRasterFactory.createRaster(
                tempDir.resolve("input-extent.tif"),
                new float[] {1.0f, 2.0f, 3.0f, 4.0f},
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path reference = TestRasterFactory.createRaster(
                tempDir.resolve("reference-extent.tif"),
                new float[] {0.0f, 0.0f, 0.0f, 0.0f},
                2,
                2,
                1.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);

        CommandResult result = execute(
                "render",
                "align-grid",
                "--input",
                input.toString(),
                "--reference",
                reference.toString(),
                "--output",
                tempDir.resolve("ignored-extent.tif").toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Input extent does not match the reference raster.");
    }

    @Test
    void alignGridRejectsCoarserInput() throws Exception {
        Path input = TestRasterFactory.createRaster(
                tempDir.resolve("input-coarse.tif"),
                new float[] {1.0f, 2.0f, 3.0f, 4.0f},
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path reference = TestRasterFactory.createRaster(
                tempDir.resolve("reference-fine.tif"),
                new float[] {
                        0.0f, 0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 0.0f
                },
                4,
                4,
                0.0,
                0.0,
                0.5,
                "EPSG:2056",
                -9999.0);

        CommandResult result = execute(
                "render",
                "align-grid",
                "--input",
                input.toString(),
                "--reference",
                reference.toString(),
                "--output",
                tempDir.resolve("ignored-coarse.tif").toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Input resolution must be finer than or equal to the reference raster.");
    }

    @Test
    void alignGridRejectsNonIntegerResolutionRatio() throws Exception {
        Path input = TestRasterFactory.createRaster(
                tempDir.resolve("input-ratio.tif"),
                new float[100],
                10,
                10,
                0.0,
                0.0,
                0.3,
                "EPSG:2056",
                -9999.0);
        Path reference = TestRasterFactory.createRaster(
                tempDir.resolve("reference-ratio.tif"),
                new float[36],
                6,
                6,
                0.0,
                0.0,
                0.5,
                "EPSG:2056",
                -9999.0);

        CommandResult result = execute(
                "render",
                "align-grid",
                "--input",
                input.toString(),
                "--reference",
                reference.toString(),
                "--output",
                tempDir.resolve("ignored-ratio.tif").toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Reference-to-input resolution ratio must be an integer in both axes.");
    }

    @Test
    void alignGridRejectsMultiBandInput() throws Exception {
        Path input = createMultiBandRaster(tempDir.resolve("input-multiband.tif"), 2, 2, 1.0, "EPSG:2056");
        Path reference = TestRasterFactory.createRaster(
                tempDir.resolve("reference-multiband.tif"),
                new float[] {0.0f, 0.0f, 0.0f, 0.0f},
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);

        CommandResult result = execute(
                "render",
                "align-grid",
                "--input",
                input.toString(),
                "--reference",
                reference.toString(),
                "--output",
                tempDir.resolve("ignored-multiband.tif").toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Only single-band DSM rasters are supported.");
    }

    @Test
    void alignGridThenComposeProducesRgbaOutput() throws Exception {
        Path occlusion = TestRasterFactory.createRaster(
                tempDir.resolve("occlusion.tif"),
                new float[] {0.0f, 1.0f, 0.5f, 0.25f},
                2,
                2,
                0.0,
                0.0,
                1.0,
                "EPSG:2056",
                -9999.0);
        Path vegetationInput = TestRasterFactory.createRaster(
                tempDir.resolve("vegetation-input.tif"),
                new float[] {
                        0.0f, 0.0f, 10.0f, 10.0f,
                        0.0f, 0.0f, 10.0f, 10.0f,
                        20.0f, 20.0f, 40.0f, 40.0f,
                        20.0f, 20.0f, 40.0f, 40.0f
                },
                4,
                4,
                0.0,
                0.0,
                0.5,
                "EPSG:2056",
                -9999.0);
        Path vegetationAligned = tempDir.resolve("vegetation-aligned.tif");
        Path composeOutput = tempDir.resolve("compose-rgba.tif");
        Path style = tempDir.resolve("style-occlusion-vegetation.json");

        CommandResult alignResult = execute(
                "render",
                "align-grid",
                "--input",
                vegetationInput.toString(),
                "--reference",
                occlusion.toString(),
                "--output",
                vegetationAligned.toString(),
                "--tile-size",
                "1");

        assertThat(alignResult.exitCode()).isZero();

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
                      "valueMax": 30.0,
                      "colorFrom": "#CFE8B4",
                      "colorTo": "#1F5A2A",
                      "blendMode": "normal",
                      "opacity": 0.6
                    }
                  ]
                }
                """.formatted(escaped(occlusion.toString()), escaped(vegetationAligned.toString())));

        CommandResult composeResult = execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,2,2",
                "--output-mode",
                "single-file",
                "--output",
                composeOutput.toString(),
                "--tile-size",
                "1",
                "--with-alpha");

        assertThat(composeResult.exitCode()).isZero();
        assertThat(TestRasterFactory.readBandCount(composeOutput)).isEqualTo(4);
        float[][] bands = TestRasterFactory.readRasterBands(composeOutput);
        assertThat(bands[3]).containsExactly(255.0f, 255.0f, 255.0f, 255.0f);
        assertThat(bands[0][3]).isEqualTo(44.0f);
        assertThat(bands[1][3]).isEqualTo(80.0f);
        assertThat(bands[2][3]).isEqualTo(51.0f);
    }

    private CommandResult execute(String... args) {
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());
        int exitCode = commandLine.execute(args);
        return new CommandResult(exitCode, output.stdout(), output.stderr());
    }

    private Path createMultiBandRaster(Path output, int width, int height, double pixelSize, String epsgCode) throws Exception {
        ReferencedEnvelope envelope = new ReferencedEnvelope(
                0.0,
                width * pixelSize,
                0.0,
                height * pixelSize,
                CRS.decode(epsgCode, true));
        GeoTiffSupport.writeFloatBands(
                output,
                new float[][] {
                        new float[] {1.0f, 2.0f, 3.0f, 4.0f},
                        new float[] {5.0f, 6.0f, 7.0f, 8.0f}
                },
                width,
                height,
                envelope,
                -9999.0,
                2);
        return output;
    }

    private String escaped(String value) {
        return value.replace("\\", "\\\\");
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
