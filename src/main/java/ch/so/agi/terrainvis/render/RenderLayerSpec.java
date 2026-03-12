package ch.so.agi.terrainvis.render;

public record RenderLayerSpec(
        String input,
        String alphaInput,
        double valueMin,
        double valueMax,
        RenderColor colorFrom,
        RenderColor colorTo,
        BlendMode blendMode,
        double opacity) {

    public RenderLayerSpec {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input is required");
        }
        input = input.trim();
        if (alphaInput != null && alphaInput.isBlank()) {
            alphaInput = null;
        }
        if (alphaInput != null) {
            alphaInput = alphaInput.trim();
        }
        if (!Double.isFinite(valueMin) || !Double.isFinite(valueMax)) {
            throw new IllegalArgumentException("valueMin and valueMax must be finite");
        }
        if (valueMin >= valueMax) {
            throw new IllegalArgumentException("valueMin must be < valueMax");
        }
        if (colorFrom == null || colorTo == null) {
            throw new IllegalArgumentException("colorFrom and colorTo are required");
        }
        if (blendMode == null) {
            throw new IllegalArgumentException("blendMode is required");
        }
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("opacity must be in [0,1]");
        }
    }
}
