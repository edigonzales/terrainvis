package ch.so.agi.terrainvis.config;

public record CommonRunConfig(
        String inputLocation,
        BBox bbox,
        OutputConfig outputConfig,
        TilingConfig tilingConfig,
        boolean printInfo,
        boolean verbose) {

    public CommonRunConfig {
        if (inputLocation == null || inputLocation.isBlank()) {
            throw new IllegalArgumentException("inputLocation is required");
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
    }
}
