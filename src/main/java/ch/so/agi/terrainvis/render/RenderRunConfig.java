package ch.so.agi.terrainvis.render;

import java.nio.file.Path;

import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.OutputConfig;
import ch.so.agi.terrainvis.config.TilingConfig;

public record RenderRunConfig(
        Path stylePath,
        RenderStyle style,
        BBox bbox,
        OutputConfig outputConfig,
        TilingConfig tilingConfig,
        boolean withAlpha,
        boolean printInfo,
        boolean verbose) {

    public RenderRunConfig {
        if (stylePath == null) {
            throw new IllegalArgumentException("stylePath is required");
        }
        if (style == null) {
            throw new IllegalArgumentException("style is required");
        }
        if (bbox == null) {
            throw new IllegalArgumentException("bbox is required");
        }
        if (outputConfig == null) {
            throw new IllegalArgumentException("outputConfig is required");
        }
        if (tilingConfig == null) {
            throw new IllegalArgumentException("tilingConfig is required");
        }
        if (outputConfig.dataType() != ch.so.agi.terrainvis.config.OutputDataType.UINT8) {
            throw new IllegalArgumentException("render output must use uint8");
        }
    }

    public int outputBandCount() {
        return withAlpha ? 4 : 3;
    }
}
