package ch.so.agi.terrainvis.config;

import java.nio.file.Path;

public record RunConfig(
        String inputLocation,
        BBox bbox,
        AlgorithmMode algorithmMode,
        OutputMode outputMode,
        Path outputFile,
        Path outputDirectory,
        int tileSizePixels,
        Integer bufferPixelsOverride,
        Double bufferMetersOverride,
        int startTile,
        int threads,
        boolean outputByte,
        boolean verbose,
        boolean printInfo,
        double exaggeration,
        LightingParameters lightingParameters,
        HorizonParameters horizonParameters) {

    public RunConfig {
        if (inputLocation == null || inputLocation.isBlank()) {
            throw new IllegalArgumentException("inputLocation is required");
        }
        if (bbox == null) {
            throw new IllegalArgumentException("bbox is required");
        }
        if (algorithmMode == null) {
            throw new IllegalArgumentException("algorithmMode is required");
        }
        if (outputMode == null) {
            throw new IllegalArgumentException("outputMode is required");
        }
        if (tileSizePixels <= 0) {
            throw new IllegalArgumentException("tileSizePixels must be > 0");
        }
        if (bufferPixelsOverride != null && bufferPixelsOverride < 0) {
            throw new IllegalArgumentException("bufferPixelsOverride must be >= 0");
        }
        if (bufferMetersOverride != null && bufferMetersOverride < 0.0) {
            throw new IllegalArgumentException("bufferMetersOverride must be >= 0");
        }
        if (startTile < 0) {
            throw new IllegalArgumentException("startTile must be >= 0");
        }
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be > 0");
        }
        if (exaggeration <= 0.0) {
            throw new IllegalArgumentException("exaggeration must be > 0");
        }
        if (lightingParameters == null) {
            throw new IllegalArgumentException("lightingParameters is required");
        }
        if (horizonParameters == null) {
            throw new IllegalArgumentException("horizonParameters is required");
        }
        if (outputByte && outputMode != OutputMode.TILE_FILES) {
            throw new IllegalArgumentException("--outputByte is only supported in tiled mode");
        }
        if (bufferPixelsOverride != null && bufferMetersOverride != null) {
            throw new IllegalArgumentException("Use either -b or --bufferMeters, not both");
        }
        if (algorithmMode == AlgorithmMode.HORIZON && lightingParameters.maxBounces() > 0) {
            throw new IllegalArgumentException("--algorithm=horizon supports only maxBounces=0");
        }
    }

    public int defaultBufferPixels() {
        return tileSizePixels / 3;
    }
}
