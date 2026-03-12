package ch.so.agi.terrainvis.testsupport;

import java.io.IOException;
import java.awt.image.Raster;
import java.nio.file.Path;

import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;

import ch.so.agi.terrainvis.output.GeoTiffSupport;

public final class TestRasterFactory {
    private TestRasterFactory() {
    }

    public static Path createRaster(
            Path output,
            float[] values,
            int width,
            int height,
            double minX,
            double minY,
            double pixelSize,
            String epsgCode,
            double noData) throws Exception {
        ReferencedEnvelope envelope = new ReferencedEnvelope(
                minX,
                minX + width * pixelSize,
                minY,
                minY + height * pixelSize,
                CRS.decode(epsgCode, true));
        GeoTiffSupport.writeFloatArray(output, values, width, height, envelope, noData, Math.min(128, Math.max(width, height)));
        return output;
    }

    public static float[] readRaster(Path rasterPath) throws IOException {
        try (TestRasterReader reader = new TestRasterReader(rasterPath.toString())) {
            return reader.readAll();
        }
    }

    public static float[][] readRasterBands(Path rasterPath) throws IOException {
        GeoTiffReader reader = new GeoTiffReader(rasterPath.toFile());
        try {
            GridCoverage2D coverage = reader.read((GeneralParameterValue[]) null);
            try {
                Raster raster = coverage.getRenderedImage().getData();
                int width = raster.getWidth();
                int height = raster.getHeight();
                int bandCount = raster.getNumBands();
                float[][] bands = new float[bandCount][width * height];
                for (int band = 0; band < bandCount; band++) {
                    bands[band] = raster.getSamples(raster.getMinX(), raster.getMinY(), width, height, band, (float[]) null);
                }
                return bands;
            } finally {
                coverage.dispose(true);
            }
        } finally {
            reader.dispose();
        }
    }

    public static int readBandCount(Path rasterPath) throws IOException {
        GeoTiffReader reader = new GeoTiffReader(rasterPath.toFile());
        try {
            GridCoverage2D coverage = reader.read((GeneralParameterValue[]) null);
            try {
                return coverage.getNumSampleDimensions();
            } finally {
                coverage.dispose(true);
            }
        } finally {
            reader.dispose();
        }
    }
}
