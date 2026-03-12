package ch.so.agi.terrainvis.rvt;

import ch.so.agi.terrainvis.config.CommonRunConfig;

public record RvtRunConfig(
        CommonRunConfig commonConfig,
        RvtProduct product,
        double exaggeration,
        RvtParameters parameters) {

    public RvtRunConfig {
        if (commonConfig == null) {
            throw new IllegalArgumentException("commonConfig is required");
        }
        if (product == null) {
            throw new IllegalArgumentException("product is required");
        }
        if (exaggeration <= 0.0) {
            throw new IllegalArgumentException("exaggeration must be > 0");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("parameters are required");
        }
    }
}
