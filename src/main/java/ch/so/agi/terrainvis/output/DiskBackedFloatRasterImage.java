package ch.so.agi.terrainvis.output;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BandedSampleModel;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferFloat;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Vector;

import org.eclipse.imagen.PlanarImage;
import org.eclipse.imagen.media.range.NoDataContainer;

import ch.so.agi.terrainvis.tiling.PixelWindow;

final class DiskBackedFloatRasterImage implements RenderedImage, Closeable {
    private final int width;
    private final int height;
    private final int tileWidth;
    private final int tileHeight;
    private final int bandCount;
    private final double noDataValue;
    private final SampleModel sampleModel;
    private final ColorModel colorModel;
    private final Path tempFile;
    private final FileChannel channel;

    DiskBackedFloatRasterImage(
            int width,
            int height,
            int tileWidth,
            int tileHeight,
            int bandCount,
            double noDataValue) throws IOException {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.bandCount = bandCount;
        this.noDataValue = noDataValue;
        this.sampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, tileWidth, tileHeight, bandCount);
        this.colorModel = PlanarImage.createColorModel(sampleModel);
        this.tempFile = Files.createTempFile("terrainvis-output-", ".bin");
        this.channel = FileChannel.open(tempFile, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        initialize();
    }

    void writeWindow(PixelWindow targetWindow, float[][] data) throws IOException {
        for (int band = 0; band < bandCount; band++) {
            for (int row = 0; row < targetWindow.height(); row++) {
                ByteBuffer buffer = ByteBuffer.allocate(targetWindow.width() * Float.BYTES).order(ByteOrder.nativeOrder());
                int rowOffset = row * targetWindow.width();
                for (int column = 0; column < targetWindow.width(); column++) {
                    buffer.putFloat(data[band][rowOffset + column]);
                }
                buffer.flip();
                long position = positionOf(band, targetWindow.x(), targetWindow.y() + row);
                while (buffer.hasRemaining()) {
                    int written = channel.write(buffer, position);
                    position += written;
                }
            }
        }
    }

    @Override
    public Vector<RenderedImage> getSources() {
        return new Vector<>(Collections.emptyList());
    }

    @Override
    public Object getProperty(String name) {
        if (NoDataContainer.GC_NODATA.equals(name) && !Double.isNaN(noDataValue)) {
            return new NoDataContainer(noDataValue);
        }
        return java.awt.Image.UndefinedProperty;
    }

    @Override
    public String[] getPropertyNames() {
        return Double.isNaN(noDataValue) ? new String[0] : new String[] {NoDataContainer.GC_NODATA};
    }

    @Override
    public ColorModel getColorModel() {
        return colorModel;
    }

    @Override
    public SampleModel getSampleModel() {
        return sampleModel;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinX() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getNumXTiles() {
        return (width + tileWidth - 1) / tileWidth;
    }

    @Override
    public int getNumYTiles() {
        return (height + tileHeight - 1) / tileHeight;
    }

    @Override
    public int getMinTileX() {
        return 0;
    }

    @Override
    public int getMinTileY() {
        return 0;
    }

    @Override
    public int getTileWidth() {
        return tileWidth;
    }

    @Override
    public int getTileHeight() {
        return tileHeight;
    }

    @Override
    public int getTileGridXOffset() {
        return 0;
    }

    @Override
    public int getTileGridYOffset() {
        return 0;
    }

    @Override
    public Raster getTile(int tileX, int tileY) {
        int x = tileX * tileWidth;
        int y = tileY * tileHeight;
        int tileW = Math.min(tileWidth, this.width - x);
        int tileH = Math.min(tileHeight, this.height - y);
        try {
            return readWindow(new PixelWindow(x, y, tileW, tileH));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read tile " + tileX + "," + tileY, e);
        }
    }

    @Override
    public Raster getData() {
        try {
            return readWindow(new PixelWindow(0, 0, width, height));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read raster data", e);
        }
    }

    @Override
    public Raster getData(Rectangle rect) {
        try {
            return readWindow(new PixelWindow(rect.x, rect.y, rect.width, rect.height));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read raster data", e);
        }
    }

    @Override
    public WritableRaster copyData(WritableRaster outRaster) {
        if (outRaster == null) {
            Rectangle bounds = new Rectangle(getMinX(), getMinY(), getWidth(), getHeight());
            outRaster = Raster.createWritableRaster(sampleModel.createCompatibleSampleModel(bounds.width, bounds.height), new Point(bounds.x, bounds.y));
        }
        Raster source = getData(outRaster.getBounds());
        outRaster.setRect(source);
        return outRaster;
    }

    @Override
    public void close() throws IOException {
        channel.close();
        Files.deleteIfExists(tempFile);
    }

    private WritableRaster readWindow(PixelWindow window) throws IOException {
        float[][] bands = new float[bandCount][window.width() * window.height()];
        for (int band = 0; band < bandCount; band++) {
            float[] values = bands[band];
            for (int row = 0; row < window.height(); row++) {
                ByteBuffer buffer = ByteBuffer.allocate(window.width() * Float.BYTES).order(ByteOrder.nativeOrder());
                long position = positionOf(band, window.x(), window.y() + row);
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer, position);
                    if (read < 0) {
                        throw new IOException("Unexpected end of disk-backed raster.");
                    }
                    position += read;
                }
                buffer.flip();
                int rowOffset = row * window.width();
                for (int column = 0; column < window.width(); column++) {
                    values[rowOffset + column] = buffer.getFloat();
                }
            }
        }
        BandedSampleModel windowSampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, window.width(), window.height(), bandCount);
        DataBufferFloat buffer = new DataBufferFloat(bands, window.width() * window.height());
        return Raster.createWritableRaster(windowSampleModel, buffer, new Point(window.x(), window.y()));
    }

    private void initialize() throws IOException {
        int chunkSize = Math.max(1, Math.min(width * height, 16_384));
        ByteBuffer buffer = ByteBuffer.allocate(chunkSize * Float.BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < chunkSize; i++) {
            buffer.putFloat((float) noDataValue);
        }
        buffer.flip();
        long totalCells = (long) width * height;
        for (int band = 0; band < bandCount; band++) {
            long written = 0;
            while (written < totalCells) {
                ByteBuffer chunk = buffer.asReadOnlyBuffer();
                long remainingCells = totalCells - written;
                int writeCells = (int) Math.min(chunkSize, remainingCells);
                chunk.limit(writeCells * Float.BYTES);
                long position = positionOf(band, 0, 0) + (written * Float.BYTES);
                while (chunk.hasRemaining()) {
                    int bytesWritten = channel.write(chunk, position);
                    position += bytesWritten;
                }
                written += writeCells;
            }
        }
    }

    private long positionOf(int band, int x, int y) {
        long cellsPerBand = (long) width * height;
        long cellIndex = ((long) y * width) + x;
        return ((band * cellsPerBand) + cellIndex) * Float.BYTES;
    }
}
