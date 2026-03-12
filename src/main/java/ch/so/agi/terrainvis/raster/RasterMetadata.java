package ch.so.agi.terrainvis.raster;

import java.util.Locale;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;

import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.util.MathUtils;

public record RasterMetadata(
        int width,
        int height,
        ReferencedEnvelope envelope,
        CoordinateReferenceSystem crs,
        double resolutionX,
        double resolutionY,
        double noDataValue,
        int bandCount,
        String sourceDescription) {

    public RasterMetadata {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Raster dimensions must be > 0");
        }
        if (envelope == null || crs == null || sourceDescription == null) {
            throw new IllegalArgumentException("Raster metadata fields must not be null");
        }
        if (!(resolutionX > 0.0) || !(resolutionY > 0.0)) {
            throw new IllegalArgumentException("Raster resolution must be > 0");
        }
        if (bandCount <= 0) {
            throw new IllegalArgumentException("bandCount must be > 0");
        }
    }

    public PixelWindow snapAndClip(BBox bbox) {
        double clippedMinX = Math.max(bbox.minX(), envelope.getMinX());
        double clippedMaxX = Math.min(bbox.maxX(), envelope.getMaxX());
        double clippedMinY = Math.max(bbox.minY(), envelope.getMinY());
        double clippedMaxY = Math.min(bbox.maxY(), envelope.getMaxY());
        if (clippedMinX >= clippedMaxX || clippedMinY >= clippedMaxY) {
            throw new IllegalArgumentException("BBOX does not overlap the raster extent.");
        }
        int startX = MathUtils.clamp((int) Math.floor((clippedMinX - envelope.getMinX()) / resolutionX), 0, width);
        int endX = MathUtils.clamp((int) Math.ceil((clippedMaxX - envelope.getMinX()) / resolutionX), 0, width);
        int startY = MathUtils.clamp((int) Math.floor((envelope.getMaxY() - clippedMaxY) / resolutionY), 0, height);
        int endY = MathUtils.clamp((int) Math.ceil((envelope.getMaxY() - clippedMinY) / resolutionY), 0, height);
        PixelWindow window = new PixelWindow(startX, startY, Math.max(0, endX - startX), Math.max(0, endY - startY));
        if (window.isEmpty()) {
            throw new IllegalArgumentException("BBOX collapsed to an empty raster window.");
        }
        return window;
    }

    public ReferencedEnvelope toEnvelope(PixelWindow window) {
        double minX = envelope.getMinX() + window.x() * resolutionX;
        double maxX = envelope.getMinX() + window.endX() * resolutionX;
        double maxY = envelope.getMaxY() - window.y() * resolutionY;
        double minY = envelope.getMaxY() - window.endY() * resolutionY;
        return new ReferencedEnvelope(minX, maxX, minY, maxY, crs);
    }

    public int bufferPixelsX(double bufferMeters) {
        return (int) Math.ceil(bufferMeters / resolutionX);
    }

    public int bufferPixelsY(double bufferMeters) {
        return (int) Math.ceil(bufferMeters / resolutionY);
    }

    public String describe() {
        return String.format(
                Locale.ROOT,
                "Source: %s%nCRS: %s%nSize: %d x %d%nExtent: [%.3f, %.3f, %.3f, %.3f]%nResolution: %.3f x %.3f%nNoData: %s",
                sourceDescription,
                crs.getName().toString(),
                width,
                height,
                envelope.getMinX(),
                envelope.getMinY(),
                envelope.getMaxX(),
                envelope.getMaxY(),
                resolutionX,
                resolutionY,
                Double.isNaN(noDataValue) ? "NaN" : String.format(Locale.ROOT, "%.6f", noDataValue));
    }
}
