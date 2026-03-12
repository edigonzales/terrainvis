package ch.so.agi.terrainvis.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.geotools.geometry.jts.ReferencedEnvelope;

import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.core.TileComputationResult;
import ch.so.agi.terrainvis.raster.RasterMetadata;
import ch.so.agi.terrainvis.tiling.TileRequest;
import ch.so.agi.terrainvis.util.ConsoleLogger;

public final class TiledTileWriter implements TileWriter {
    private final Path outputDirectory;
    private final RasterMetadata metadata;
    private final int tileSizePixels;
    private final boolean outputByte;
    private final ConsoleLogger logger;

    public TiledTileWriter(RunConfig runConfig, RasterMetadata metadata, ConsoleLogger logger) throws IOException {
        this.outputDirectory = runConfig.outputDirectory();
        this.metadata = metadata;
        this.tileSizePixels = runConfig.tileSizePixels();
        this.outputByte = runConfig.outputByte();
        this.logger = logger;
        Files.createDirectories(outputDirectory);
        this.logger.verbose(
                "Initialized tiled writer: outputDir=%s, outputType=%s",
                outputDirectory,
                outputByte ? "Byte" : "Float32");
    }

    @Override
    public void write(TileComputationResult result) throws IOException {
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
        logger.verbose("Writing tiled output for tileId=%d to %s", request.id(), output);
        ReferencedEnvelope envelope = metadata.toEnvelope(request.window().coreWindow());
        if (outputByte) {
            byte[] byteValues = new byte[result.data().length];
            for (int i = 0; i < result.data().length; i++) {
                float value = result.data()[i];
                double clamped = Math.max(0.0, Math.min(1.0, value));
                byteValues[i] = (byte) Math.round(clamped * 255.0);
            }
            GeoTiffSupport.writeByteArray(
                    output,
                    byteValues,
                    request.window().coreWindow().width(),
                    request.window().coreWindow().height(),
                    envelope,
                    0.0,
                    tileSizePixels);
        } else {
            GeoTiffSupport.writeFloatArray(
                    output,
                    result.data(),
                    request.window().coreWindow().width(),
                    request.window().coreWindow().height(),
                    envelope,
                    metadata.noDataValue(),
                    tileSizePixels);
        }
    }

    @Override
    public void close() {
    }
}
