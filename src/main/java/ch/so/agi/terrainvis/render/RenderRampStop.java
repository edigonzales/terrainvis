package ch.so.agi.terrainvis.render;

public record RenderRampStop(double value, RenderColor color, Double alpha) {
    public RenderRampStop {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("stop value must be finite");
        }
        if (color == null) {
            throw new IllegalArgumentException("stop color is required");
        }
        alpha = alpha == null ? 1.0 : alpha;
        if (!Double.isFinite(alpha) || alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("stop alpha must be in [0,1]");
        }
    }
}
