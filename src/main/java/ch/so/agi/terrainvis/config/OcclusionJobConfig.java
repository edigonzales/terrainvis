package ch.so.agi.terrainvis.config;

public record OcclusionJobConfig(
        CommonRunConfig commonConfig,
        AlgorithmMode algorithmMode,
        double exaggeration,
        LightingParameters lightingParameters,
        HorizonParameters horizonParameters) {

    public OcclusionJobConfig {
        if (commonConfig == null) {
            throw new IllegalArgumentException("commonConfig is required");
        }
        if (algorithmMode == null) {
            throw new IllegalArgumentException("algorithmMode is required");
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
        if (algorithmMode == AlgorithmMode.HORIZON && lightingParameters.maxBounces() > 0) {
            throw new IllegalArgumentException("horizon supports only maxBounces=0");
        }
        if (commonConfig.outputConfig().dataType() == OutputDataType.UINT8
                && commonConfig.outputConfig().mode() != OutputMode.TILE_FILES) {
            throw new IllegalArgumentException("occlusion uint8 output is only supported in tile-files mode");
        }
    }

    public RunConfig toLegacyRunConfig() {
        OutputConfig outputConfig = commonConfig.outputConfig();
        TilingConfig tilingConfig = commonConfig.tilingConfig();
        return new RunConfig(
                commonConfig.inputLocation(),
                commonConfig.bbox(),
                algorithmMode,
                outputConfig.mode(),
                outputConfig.mode() == OutputMode.SINGLE_FILE ? outputConfig.outputFile() : java.nio.file.Path.of("output.tif"),
                outputConfig.mode() == OutputMode.TILE_FILES ? outputConfig.outputDirectory() : java.nio.file.Path.of("output_tiles"),
                tilingConfig.tileSizePixels(),
                tilingConfig.bufferPixelsOverride(),
                tilingConfig.bufferMetersOverride(),
                tilingConfig.startTile(),
                tilingConfig.threads(),
                outputConfig.dataType() == OutputDataType.UINT8,
                commonConfig.verbose(),
                commonConfig.printInfo(),
                exaggeration,
                lightingParameters,
                horizonParameters);
    }
}
