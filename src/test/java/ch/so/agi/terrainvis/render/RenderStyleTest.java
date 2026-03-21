package ch.so.agi.terrainvis.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderStyleTest {
    @TempDir
    Path tempDir;

    @Test
    void missingResamplingDefaultsToBilinear() throws Exception {
        Path stylePath = tempDir.resolve("style-default.json");
        Files.writeString(stylePath, """
                {
                  "layers": [
                    {
                      "input": "input.tif",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#000000",
                      "colorTo": "#FFFFFF",
                      "blendMode": "normal",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        RenderStyle style = RenderStyle.load(stylePath);

        assertThat(style.layers()).singleElement().extracting(RenderLayerSpec::resampling).isEqualTo(LayerResampling.BILINEAR);
    }

    @Test
    void explicitResamplingIsParsedFromStyle() throws Exception {
        Path stylePath = tempDir.resolve("style-max.json");
        Files.writeString(stylePath, """
                {
                  "layers": [
                    {
                      "input": "input.tif",
                      "stops": [
                        { "value": 0.0, "color": "#000000", "alpha": 1.0 },
                        { "value": 1.0, "color": "#FFFFFF", "alpha": 1.0 }
                      ],
                      "blendMode": "normal",
                      "opacity": 1.0,
                      "resampling": "max"
                    }
                  ]
                }
                """);

        RenderStyle style = RenderStyle.load(stylePath);

        assertThat(style.layers()).singleElement().extracting(RenderLayerSpec::resampling).isEqualTo(LayerResampling.MAX);
    }

    @Test
    void softLightAliasIsParsedFromStyle() throws Exception {
        Path stylePath = tempDir.resolve("style-softlight.json");
        Files.writeString(stylePath, """
                {
                  "layers": [
                    {
                      "input": "input.tif",
                      "valueMin": 0.0,
                      "valueMax": 1.0,
                      "colorFrom": "#000000",
                      "colorTo": "#FFFFFF",
                      "blendMode": "soft-light",
                      "opacity": 1.0
                    }
                  ]
                }
                """);

        RenderStyle style = RenderStyle.load(stylePath);

        assertThat(style.layers()).singleElement().extracting(RenderLayerSpec::blendMode).isEqualTo(BlendMode.SOFTLIGHT);
    }
}
