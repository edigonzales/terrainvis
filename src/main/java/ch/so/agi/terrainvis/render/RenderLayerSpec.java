package ch.so.agi.terrainvis.render;

import java.util.List;

public record RenderLayerSpec(
        String input,
        String alphaInput,
        Double valueMin,
        Double valueMax,
        RenderColor colorFrom,
        RenderColor colorTo,
        BlendMode blendMode,
        double opacity,
        List<RenderRampStop> stops) {

    public RenderLayerSpec(
            String input,
            String alphaInput,
            double valueMin,
            double valueMax,
            RenderColor colorFrom,
            RenderColor colorTo,
            BlendMode blendMode,
            double opacity) {
        this(input, alphaInput, valueMin, valueMax, colorFrom, colorTo, blendMode, opacity, null);
    }

    public RenderLayerSpec(
            String input,
            String alphaInput,
            List<RenderRampStop> stops,
            BlendMode blendMode,
            double opacity) {
        this(input, alphaInput, null, null, null, null, blendMode, opacity, stops);
    }

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
        boolean hasLegacyField = valueMin != null || valueMax != null || colorFrom != null || colorTo != null;
        boolean hasAllLegacyFields = valueMin != null && valueMax != null && colorFrom != null && colorTo != null;
        boolean hasStops = stops != null;
        if (hasLegacyField && hasStops) {
            throw new IllegalArgumentException("Layer must define either valueMin/valueMax/colorFrom/colorTo or stops, not both");
        }
        if (!hasLegacyField && !hasStops) {
            throw new IllegalArgumentException("Layer must define either valueMin/valueMax/colorFrom/colorTo or stops");
        }
        if (hasLegacyField && !hasAllLegacyFields) {
            throw new IllegalArgumentException("Legacy color ramps require valueMin, valueMax, colorFrom and colorTo");
        }
        if (hasAllLegacyFields) {
            if (!Double.isFinite(valueMin) || !Double.isFinite(valueMax)) {
                throw new IllegalArgumentException("valueMin and valueMax must be finite");
            }
            if (valueMin >= valueMax) {
                throw new IllegalArgumentException("valueMin must be < valueMax");
            }
        }
        if (hasStops) {
            if (stops.isEmpty()) {
                throw new IllegalArgumentException("stops must contain at least two entries");
            }
            if (stops.size() < 2) {
                throw new IllegalArgumentException("stops must contain at least two entries");
            }
            float previousValue = Float.NEGATIVE_INFINITY;
            for (RenderRampStop stop : stops) {
                if (stop == null) {
                    throw new IllegalArgumentException("stops must not contain null entries");
                }
                float currentValue = (float) stop.value();
                if (!Float.isFinite(currentValue)) {
                    throw new IllegalArgumentException("stop values must be representable as finite float values");
                }
                if (currentValue <= previousValue) {
                    throw new IllegalArgumentException("stop values must be strictly increasing");
                }
                previousValue = currentValue;
            }
            stops = List.copyOf(stops);
        }
        if (blendMode == null) {
            throw new IllegalArgumentException("blendMode is required");
        }
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("opacity must be in [0,1]");
        }
    }
}
