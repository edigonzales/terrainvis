package ch.so.agi.terrainvis.output;

import java.awt.Point;
import java.awt.image.BandedSampleModel;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferFloat;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageWriteParam;

import org.eclipse.imagen.PlanarImage;
import org.eclipse.imagen.TiledImage;
import org.eclipse.imagen.media.range.NoDataContainer;
import org.geotools.api.parameter.GeneralParameterValue;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.imageio.GeoToolsWriteParams;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.api.parameter.ParameterValueGroup;

public final class GeoTiffSupport {
    private GeoTiffSupport() {
    }

    public static void writeFloatArray(
            Path output,
            float[] data,
            int width,
            int height,
            ReferencedEnvelope envelope,
            double noData,
            int tileSizePixels) throws IOException {
        writeFloatBands(output, new float[][] {data}, width, height, envelope, noData, tileSizePixels);
    }

    public static void writeByteArray(
            Path output,
            byte[] data,
            int width,
            int height,
            ReferencedEnvelope envelope,
            double noData,
            int tileSizePixels) throws IOException {
        writeByteBands(output, new byte[][] {data}, width, height, envelope, noData, tileSizePixels);
    }

    public static void writeFloatBands(
            Path output,
            float[][] bands,
            int width,
            int height,
            ReferencedEnvelope envelope,
            double noData,
            int tileSizePixels) throws IOException {
        RenderedImage image = createFloatImage(bands, width, height, noData);
        writeRenderedImage(output, image, envelope, tileSizePixels);
    }

    public static void writeByteBands(
            Path output,
            byte[][] bands,
            int width,
            int height,
            ReferencedEnvelope envelope,
            double noData,
            int tileSizePixels) throws IOException {
        RenderedImage image = createByteImage(bands, width, height, noData);
        writeRenderedImage(output, image, envelope, tileSizePixels);
    }

    public static RenderedImage createFloatImage(float[][] bands, int width, int height, double noData) {
        int bandCount = bands.length;
        BandedSampleModel sampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, width, height, bandCount);
        DataBufferFloat buffer = new DataBufferFloat(bands, width * height);
        WritableRaster raster = Raster.createWritableRaster(sampleModel, buffer, new Point(0, 0));
        TiledImage image = createImage(width, height, sampleModel);
        image.setData(raster);
        if (!Double.isNaN(noData)) {
            image.setProperty(NoDataContainer.GC_NODATA, new NoDataContainer(noData));
        }
        return image;
    }

    public static RenderedImage createByteImage(byte[][] bands, int width, int height, double noData) {
        int bandCount = bands.length;
        BandedSampleModel sampleModel = new BandedSampleModel(DataBuffer.TYPE_BYTE, width, height, bandCount);
        DataBufferByte buffer = new DataBufferByte(bands, width * height);
        WritableRaster raster = Raster.createWritableRaster(sampleModel, buffer, new Point(0, 0));
        TiledImage image = createImage(width, height, sampleModel);
        image.setData(raster);
        if (!Double.isNaN(noData)) {
            image.setProperty(NoDataContainer.GC_NODATA, new NoDataContainer(noData));
        }
        return image;
    }

    public static void writeRasterBlock(
            Path output,
            RasterBlock block,
            OutputEncoder encoder,
            ReferencedEnvelope envelope,
            int tileSizePixels) throws IOException {
        if (encoder.dataType() == ch.so.agi.terrainvis.config.OutputDataType.FLOAT32) {
            writeFloatBands(output, block.bands(), block.width(), block.height(), envelope, block.noDataValue(), tileSizePixels);
            return;
        }
        writeByteBands(output, encoder.byteBands(), block.width(), block.height(), envelope, encoder.noDataValue(), tileSizePixels);
    }

    public record OutputEncoder(
            ch.so.agi.terrainvis.config.OutputDataType dataType,
            byte[][] byteBands,
            double noDataValue) {
    }

    public static OutputEncoder encodeByte(RasterBlock block, byte[][] byteBands, double noDataValue) {
        return new OutputEncoder(ch.so.agi.terrainvis.config.OutputDataType.UINT8, byteBands, noDataValue);
    }

    public static OutputEncoder floatEncoder(RasterBlock block) {
        return new OutputEncoder(ch.so.agi.terrainvis.config.OutputDataType.FLOAT32, null, block.noDataValue());
    }

    public static byte[][] clampToByte(RasterBlock block) {
        byte[][] output = new byte[block.bandCount()][block.width() * block.height()];
        for (int band = 0; band < block.bandCount(); band++) {
            float[] source = block.bands()[band];
            byte[] target = output[band];
            for (int i = 0; i < source.length; i++) {
                float value = source[i];
                if (Float.isNaN(value) || Double.compare(value, block.noDataValue()) == 0) {
                    target[i] = 0;
                    continue;
                }
                double clamped = Math.max(0.0, Math.min(1.0, value));
                target[i] = (byte) Math.round(clamped * 255.0);
            }
        }
        return output;
    }

    public static void writeRenderedImage(
            Path output,
            RenderedImage image,
            ReferencedEnvelope envelope,
            int tileSizePixels) throws IOException {
        GridCoverageFactory coverageFactory = new GridCoverageFactory();
        GridCoverage2D coverage = coverageFactory.create("terrainvis", image, envelope);
        GeoTiffFormat format = new GeoTiffFormat();
        GeoTiffWriteParams writeParams = new GeoTiffWriteParams();
        writeParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParams.setCompressionType("Deflate");
        writeParams.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
        writeParams.setTiling(tileSizePixels, tileSizePixels);

        ParameterValueGroup parameters = format.getWriteParameters();
        parameters.parameter(AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName().toString()).setValue(writeParams);
        Object noDataProperty = image.getProperty(NoDataContainer.GC_NODATA);
        boolean hasNoData = noDataProperty instanceof NoDataContainer;
        if (hasNoData) {
            parameters.parameter(GeoTiffFormat.WRITE_NODATA.getName().toString()).setValue(Boolean.TRUE);
        }

        GeoTiffWriter writer = new GeoTiffWriter(output.toFile());
        try {
            writer.write(coverage, parameters.values().toArray(GeneralParameterValue[]::new));
        } finally {
            writer.dispose();
            coverage.dispose(true);
        }
    }

    private static TiledImage createImage(int width, int height, BandedSampleModel sampleModel) {
        ColorModel colorModel = PlanarImage.createColorModel(sampleModel);
        return new TiledImage(0, 0, width, height, 0, 0, sampleModel, colorModel);
    }
}
