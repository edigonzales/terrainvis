package ch.so.agi.terrainvis.output;

import java.io.IOException;
import java.nio.file.Path;

import ch.so.agi.terrainvis.config.CommonRunConfig;
import ch.so.agi.terrainvis.config.OutputDataType;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TilePlan;
import ch.so.agi.terrainvis.util.ConsoleLogger;

public final class SingleFileRasterAccumulator implements RasterTileWriter {
    private final Path outputFile;
    private final TilePlan tilePlan;
    private final int tileSizePixels;
    private final OutputDataType outputDataType;
    private final double noDataValue;
    private final DiskBackedFloatRasterImage image;
    private final ConsoleLogger logger;

    public SingleFileRasterAccumulator(
            CommonRunConfig runConfig,
            TilePlan tilePlan,
            int bandCount,
            double noDataValue,
            ConsoleLogger logger) throws IOException {
        this.outputFile = runConfig.outputConfig().outputFile();
        this.tilePlan = tilePlan;
        this.tileSizePixels = runConfig.tilingConfig().tileSizePixels();
        this.outputDataType = runConfig.outputConfig().dataType();
        this.noDataValue = noDataValue;
        this.logger = logger;
        this.image = new DiskBackedFloatRasterImage(
                tilePlan.requestedWindow().width(),
                tilePlan.requestedWindow().height(),
                runConfig.tilingConfig().tileSizePixels(),
                runConfig.tilingConfig().tileSizePixels(),
                bandCount,
                noDataValue);
        this.logger.verbose("Initialized single-file raster writer: output=%s", outputFile);
    }

    @Override
    public synchronized void write(RasterTileResult result) throws IOException {
        if (result.skipped()) {
            return;
        }
        PixelWindow core = result.tileRequest().window().coreWindow();
        PixelWindow requested = tilePlan.requestedWindow();
        PixelWindow target = new PixelWindow(
                core.x() - requested.x(),
                core.y() - requested.y(),
                core.width(),
                core.height());
        image.writeWindow(target, result.block().bands());
    }

    @Override
    public void finish() throws IOException {
        logger.verbose("Finalizing single-file raster output: %s", outputFile);
        if (outputDataType == OutputDataType.FLOAT32) {
            GeoTiffSupport.writeRenderedImage(outputFile, image, tilePlan.requestedEnvelope(), tileSizePixels);
        } else {
            RasterBlock block = new RasterBlock(
                    tilePlan.requestedWindow().width(),
                    tilePlan.requestedWindow().height(),
                    image.getSampleModel().getNumBands(),
                    noDataValue,
                    readAllBands());
            GeoTiffSupport.writeByteBands(
                    outputFile,
                    GeoTiffSupport.clampToByte(block),
                    block.width(),
                    block.height(),
                    tilePlan.requestedEnvelope(),
                    noDataValue,
                    tileSizePixels);
        }
        logger.verbose("Finished single-file raster output: %s", outputFile);
    }

    @Override
    public void close() throws IOException {
        image.close();
    }

    private float[][] readAllBands() {
        java.awt.image.Raster raster = image.getData();
        int width = raster.getWidth();
        int height = raster.getHeight();
        int bandCount = raster.getNumBands();
        float[][] bands = new float[bandCount][width * height];
        for (int band = 0; band < bandCount; band++) {
            bands[band] = raster.getSamples(raster.getMinX(), raster.getMinY(), width, height, band, (float[]) null);
        }
        return bands;
    }
}
