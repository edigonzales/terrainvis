package ch.so.agi.terrainvis.cli;

import java.nio.file.Path;
import java.time.Clock;

import ch.so.agi.terrainvis.config.AlgorithmMode;
import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.CommonRunConfig;
import ch.so.agi.terrainvis.config.OutputConfig;
import ch.so.agi.terrainvis.config.OutputDataType;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.TilingConfig;
import ch.so.agi.terrainvis.rvt.RvtParameters;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;

final class CliSupport {
    private CliSupport() {
    }

    static CommonRunConfig commonRunConfig(CommonOptions options) {
        OutputMode outputMode = options.outputMode;
        Path output = options.output;
        if (output == null) {
            output = outputMode == OutputMode.SINGLE_FILE ? Path.of("output.tif") : Path.of("output_tiles");
        }
        int threads = options.threads > 0 ? options.threads : Runtime.getRuntime().availableProcessors();
        return new CommonRunConfig(
                options.inputLocation,
                options.bbox,
                new OutputConfig(outputMode, output, options.outputDataType),
                new TilingConfig(options.tileSizePixels, options.bufferPixels, options.bufferMeters, options.startTile, threads),
                options.printInfo,
                options.verbose);
    }

    static Clock defaultClock() {
        return Clock.systemDefaultZone();
    }

    static final class CommonOptions {
        @Option(names = "--input", required = true, description = "Input GeoTIFF path or remote COG URL.")
        String inputLocation;

        @Option(names = "--bbox", required = true, converter = BBoxConverter.class, description = "BBOX in raster CRS: minX,minY,maxX,maxY")
        BBox bbox;

        @Option(names = "--output-mode", defaultValue = "tile-files", converter = OutputModeConverter.class, description = "Output mode: single-file or tile-files.")
        OutputMode outputMode;

        @Option(names = "--output", description = "Output file path for single-file mode or directory for tile-files mode.")
        Path output;

        @Option(names = "--output-data-type", defaultValue = "float32", converter = OutputDataTypeConverter.class, description = "Output data type: float32 or uint8.")
        OutputDataType outputDataType;

        @Option(names = "--tile-size", defaultValue = "2000", description = "Tile size in pixels.")
        int tileSizePixels;

        @Option(names = "--buffer-px", description = "Tile buffer in pixels.")
        Integer bufferPixels;

        @Option(names = "--buffer-m", description = "Tile buffer in metres.")
        Double bufferMeters;

        @Option(names = "--threads", defaultValue = "0", description = "Number of worker threads. Default: available processors.")
        int threads;

        @Option(names = "--start-tile", defaultValue = "0", description = "Start processing at this tile id.")
        int startTile;

        @Option(names = "--info", description = "Print raster metadata before processing.")
        boolean printInfo;

        @Option(names = "--verbose", description = "Print additional detailed runtime logs.")
        boolean verbose;
    }

    static final class BBoxConverter implements ITypeConverter<BBox> {
        @Override
        public BBox convert(String value) {
            return BBox.parse(value);
        }
    }

    static final class OutputModeConverter implements ITypeConverter<OutputMode> {
        @Override
        public OutputMode convert(String value) {
            return OutputMode.fromCliValue(value);
        }
    }

    static final class OutputDataTypeConverter implements ITypeConverter<OutputDataType> {
        @Override
        public OutputDataType convert(String value) {
            return OutputDataType.fromCliValue(value);
        }
    }

    static final class AlgorithmModeConverter implements ITypeConverter<AlgorithmMode> {
        @Override
        public AlgorithmMode convert(String value) {
            return AlgorithmMode.fromCliValue(value);
        }
    }

    static final class TerrainConverter implements ITypeConverter<RvtParameters.Terrain> {
        @Override
        public RvtParameters.Terrain convert(String value) {
            return RvtParameters.Terrain.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        }
    }

    static final class MstpScaleConverter implements ITypeConverter<RvtParameters.MstpParameters.Scale> {
        @Override
        public RvtParameters.MstpParameters.Scale convert(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("scale is required");
            }
            String[] parts = value.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("scale must be min,max,step");
            }
            return new RvtParameters.MstpParameters.Scale(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        }
    }
}
