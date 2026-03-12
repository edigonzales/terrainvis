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
