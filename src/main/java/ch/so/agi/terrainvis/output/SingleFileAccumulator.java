package ch.so.agi.terrainvis.output;

import java.io.IOException;
import java.nio.file.Path;

import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.core.TileComputationResult;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.PixelWindow;
import ch.so.agi.terrainvis.tiling.TilePlan;
import ch.so.agi.terrainvis.util.ConsoleLogger;

public final class SingleFileAccumulator implements TileWriter {
    private final Path outputFile;
    private final TilePlan tilePlan;
    private final int tileSizePixels;
    private final DiskBackedFloatImage image;
    private final ConsoleLogger logger;

    public SingleFileAccumulator(RunConfig runConfig, RasterMetadata metadata, TilePlan tilePlan, ConsoleLogger logger) throws IOException {
        this.outputFile = runConfig.outputFile();
        this.tilePlan = tilePlan;
        this.tileSizePixels = runConfig.tileSizePixels();
        this.logger = logger;
        this.image = new DiskBackedFloatImage(
                tilePlan.requestedWindow().width(),
                tilePlan.requestedWindow().height(),
                runConfig.tileSizePixels(),
                runConfig.tileSizePixels(),
                metadata.noDataValue());
        this.logger.verbose("Initialized single-file writer: output=%s", outputFile);
    }

    @Override
    public synchronized void write(TileComputationResult result) throws IOException {
        PixelWindow core = result.tileRequest().window().coreWindow();
        PixelWindow requested = tilePlan.requestedWindow();
        PixelWindow target = new PixelWindow(
                core.x() - requested.x(),
                core.y() - requested.y(),
                core.width(),
                core.height());
        image.writeWindow(target, result.data());
    }

    @Override
    public void finish() throws IOException {
        logger.verbose("Finalizing single-file output: %s", outputFile);
        GeoTiffSupport.writeRenderedImage(outputFile, image, tilePlan.requestedEnvelope(), tileSizePixels);
        logger.verbose("Finished single-file output: %s", outputFile);
    }

    @Override
    public void close() throws IOException {
        image.close();
    }
}
