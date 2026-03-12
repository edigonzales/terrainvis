package ch.so.agi.terrainvis.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.geotools.geometry.jts.ReferencedEnvelope;

import ch.so.agi.terrainvis.config.CommonRunConfig;
import ch.so.agi.terrainvis.config.OutputDataType;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.util.ConsoleLogger;

public final class TiledRasterWriter implements RasterTileWriter {
    private final Path outputDirectory;
    private final RasterMetadata metadata;
    private final int tileSizePixels;
    private final OutputDataType outputDataType;
    private final ConsoleLogger logger;

    public TiledRasterWriter(CommonRunConfig runConfig, RasterMetadata metadata, ConsoleLogger logger) throws IOException {
        this.outputDirectory = runConfig.outputConfig().outputDirectory();
        this.metadata = metadata;
        this.tileSizePixels = runConfig.tilingConfig().tileSizePixels();
        this.outputDataType = runConfig.outputConfig().dataType();
        this.logger = logger;
        Files.createDirectories(outputDirectory);
        this.logger.verbose(
                "Initialized tiled raster writer: outputDir=%s, outputType=%s",
                outputDirectory,
                outputDataType);
    }

    @Override
    public void write(RasterTileResult result) throws IOException {
        if (result.skipped()) {
            return;
        }
        TileRequest request = result.tileRequest();
        Path output = outputDirectory.resolve(String.format(
                Locale.ROOT,
                "%08d_tile_%05d_%05d.tif",
                request.id(),
                request.tileColumn(),
                request.tileRow()));
        logger.verbose("Writing tiled raster output for tileId=%d to %s", request.id(), output);
        ReferencedEnvelope envelope = metadata.toEnvelope(request.window().coreWindow());
        if (outputDataType == OutputDataType.FLOAT32) {
            GeoTiffSupport.writeFloatBands(
                    output,
                    result.block().bands(),
                    result.block().width(),
                    result.block().height(),
                    envelope,
                    result.block().noDataValue(),
                    tileSizePixels);
            return;
        }
        GeoTiffSupport.writeByteBands(
                output,
                GeoTiffSupport.clampToByte(result.block()),
                result.block().width(),
                result.block().height(),
                envelope,
                result.block().noDataValue(),
                tileSizePixels);
    }

    @Override
    public void close() {
    }
}
