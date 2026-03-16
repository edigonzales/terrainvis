package ch.so.agi.terrainvis.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.so.agi.terrainvis.testsupport.CapturedCommandOutput;
import picocli.CommandLine;

class RenderCommandsTest {
    @TempDir
    Path tempDir;

    @Test
    void renderComposeHelpWorksWithoutRequiredArguments() {
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute("render", "compose", "--help");

        assertThat(exitCode).isZero();
        assertThat(output.stdout()).contains("--style").contains("--with-alpha").doesNotContain("--input");
    }

    @Test
    void renderAlignGridHelpWorksWithoutRequiredArguments() {
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute("render", "align-grid", "--help");

        assertThat(exitCode).isZero();
        assertThat(output.stdout()).contains("--input").contains("--reference").contains("--output");
    }

    @Test
    void renderComposeRejectsInvalidJsonStyle() throws Exception {
        Path style = tempDir.resolve("invalid-json.json");
        Files.writeString(style, "{ invalid");

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("Invalid render style JSON");
    }

    @Test
    void renderComposeRejectsInvalidColor() throws Exception {
        Path style = tempDir.resolve("invalid-color.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#00GG00",
                      "colorTo": "#FFFFFF",
                      "blendMode": "normal",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("Expected color in #RRGGBB format");
    }

    @Test
    void renderComposeRejectsInvalidValueRange() throws Exception {
        Path style = tempDir.resolve("invalid-range.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "valueMin": 1.0,
                      "valueMax": 1.0,
                      "colorFrom": "#000000",
                      "colorTo": "#FFFFFF",
                      "blendMode": "normal",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("valueMin must be < valueMax");
    }

    @Test
    void renderComposeRejectsInvalidOpacity() throws Exception {
        Path style = tempDir.resolve("invalid-opacity.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#000000",
                      "colorTo": "#FFFFFF",
                      "blendMode": "normal",
                      "opacity": 1.5
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("opacity must be in [0,1]");
    }

    @Test
    void renderComposeRejectsInvalidBlendMode() throws Exception {
        Path style = tempDir.resolve("invalid-blend.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#000000",
                      "colorTo": "#FFFFFF",
                      "blendMode": "screen",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("Unsupported blendMode");
    }

    @Test
    void renderComposeRejectsInvalidResampling() throws Exception {
        Path style = tempDir.resolve("invalid-resampling.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#000000",
                      "colorTo": "#FFFFFF",
                      "blendMode": "normal",
                      "opacity": 1.0,
                      "resampling": "cubic"
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("Unsupported resampling");
    }

    @Test
    void renderComposeRejectsSingleStopRamp() throws Exception {
        Path style = tempDir.resolve("single-stop.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "stops": [
                        { "value": 0.0, "color": "#FFFFFF", "alpha": 1.0 }
                      ],
                      "blendMode": "normal",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("stops must contain at least two entries");
    }

    @Test
    void renderComposeRejectsUnsortedStops() throws Exception {
        Path style = tempDir.resolve("unsorted-stops.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "stops": [
                        { "value": 1.0, "color": "#FFFFFF", "alpha": 1.0 },
                        { "value": 0.5, "color": "#00FF00", "alpha": 1.0 }
                      ],
                      "blendMode": "normal",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("stop values must be strictly increasing");
    }

    @Test
    void renderComposeRejectsInvalidStopAlpha() throws Exception {
        Path style = tempDir.resolve("invalid-stop-alpha.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "stops": [
                        { "value": 0.0, "color": "#FFFFFF", "alpha": -0.1 },
                        { "value": 1.0, "color": "#00FF00", "alpha": 1.0 }
                      ],
                      "blendMode": "normal",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("stop alpha must be in [0,1]");
    }

    @Test
    void renderComposeRejectsMixedLegacyAndStopRampFields() throws Exception {
        Path style = tempDir.resolve("mixed-ramp.json");
        Files.writeString(style, """
                {
                  "layers": [
                    {
                      "input": "missing.tif",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#000000",
                      "colorTo": "#FFFFFF",
                      "stops": [
                        { "value": 0.0, "color": "#FFFFFF", "alpha": 1.0 },
                        { "value": 1.0, "color": "#00FF00", "alpha": 1.0 }
                      ],
                      "blendMode": "normal",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        CapturedCommandOutput output = execute(style);

        assertThat(output.stderr()).contains("Layer must define either valueMin/valueMax/colorFrom/colorTo or stops, not both");
    }

    private CapturedCommandOutput execute(Path style) {
        CapturedCommandOutput output = new CapturedCommandOutput();
        CommandLine commandLine = new CommandLine(new MainCommand());
        commandLine.setOut(output.stdoutWriter());
        commandLine.setErr(output.stderrWriter());

        int exitCode = commandLine.execute(
                "render",
                "compose",
                "--style",
                style.toString(),
                "--bbox",
                "0,0,1,1");

        assertThat(exitCode).isNotZero();
        return output;
    }
}
