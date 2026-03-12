package ch.so.agi.terrainvis.config;

import java.nio.file.Path;

public record OutputConfig(
        OutputMode mode,
        Path output,
        OutputDataType dataType) {

    public OutputConfig {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (output == null) {
            throw new IllegalArgumentException("output is required");
        }
        if (dataType == null) {
            throw new IllegalArgumentException("dataType is required");
        }
    }

    public Path outputFile() {
        if (mode != OutputMode.SINGLE_FILE) {
            throw new IllegalStateException("output file is only valid for single-file mode");
        }
        return output;
    }

    public Path outputDirectory() {
        if (mode != OutputMode.TILE_FILES) {
            throw new IllegalStateException("output directory is only valid for tile-files mode");
        }
        return output;
    }
}
