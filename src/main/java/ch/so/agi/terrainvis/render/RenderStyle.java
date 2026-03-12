package ch.so.agi.terrainvis.render;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

public record RenderStyle(List<RenderLayerSpec> layers) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public RenderStyle {
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException("layers must contain at least one entry");
        }
        layers = List.copyOf(layers);
    }

    public static RenderStyle load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("style path is required");
        }
        try {
            return OBJECT_MAPPER.readValue(path.toFile(), RenderStyle.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid render style JSON: " + e.getOriginalMessage(), e);
        }
    }
}
