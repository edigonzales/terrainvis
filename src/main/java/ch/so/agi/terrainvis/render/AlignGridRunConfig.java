package ch.so.agi.terrainvis.render;

import java.nio.file.Path;

public record AlignGridRunConfig(
        String inputPath,
        String referencePath,
        Path outputPath,
        int tileSizePixels,
        int threads,
        boolean printInfo,
        boolean verbose) {

    public AlignGridRunConfig {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("inputPath is required");
        }
        if (referencePath == null || referencePath.isBlank()) {
            throw new IllegalArgumentException("referencePath is required");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("outputPath is required");
        }
        if (tileSizePixels <= 0) {
            throw new IllegalArgumentException("tileSizePixels must be > 0");
        }
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be > 0");
        }
    }
}
