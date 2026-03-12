package ch.so.agi.terrainvis.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Callable;

import ch.so.agi.terrainvis.config.AlgorithmMode;
import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.HorizonParameters;
import ch.so.agi.terrainvis.config.LightingParameters;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.RunConfig;
import ch.so.agi.terrainvis.pipeline.OcclusionPipeline;
import ch.so.agi.terrainvis.raster.GeoToolsCogRasterSource;
import ch.so.agi.terrainvis.util.ConsoleLogger;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "terrainvis",
        mixinStandardHelpOptions = true,
        description = "CPU-based DSM occlusion for local or remote COG GeoTIFF inputs.")
public final class OcclusionCommand implements Callable<Integer> {
    @Spec
    private CommandSpec commandSpec;
    private final Clock clock;

    public OcclusionCommand() {
        this(Clock.systemDefaultZone());
    }

    OcclusionCommand(Clock clock) {
        this.clock = clock;
    }

    @Option(names = "-i", required = true, description = "Input COG GeoTIFF URL or local path.")
    private String inputLocation;

    @Option(names = "--bbox", required = true, converter = BBoxConverter.class, description = "BBOX in raster CRS: minX,minY,maxX,maxY")
    private BBox bbox;

    @Option(names = "--algorithm", defaultValue = "exact", converter = AlgorithmModeConverter.class, description = "Computation algorithm: exact or horizon.")
    private AlgorithmMode algorithmMode;

    @Option(names = "-o", defaultValue = "output.tif", description = "Single-file output path.")
    private Path outputFile;

    @Option(names = "--outputDir", defaultValue = "output_tiles", description = "Output directory for tiled mode.")
    private Path outputDirectory;

    @Option(names = "-r", defaultValue = "1024", description = "Rays per pixel.")
    private int raysPerPixel;

    @Option(names = "-t", defaultValue = "2000", description = "Tile size in pixels.")
    private int tileSizePixels;

    @Option(names = "-b", description = "Tile buffer in pixels.")
    private Integer bufferPixels;

    @Option(names = "--bufferMeters", description = "Tile buffer in metres.")
    private Double bufferMeters;

    @Option(names = "-e", defaultValue = "1.0", description = "Vertical exaggeration.")
    private double exaggeration;

    @Option(names = "-B", defaultValue = "0", description = "Maximum number of ray bounces.")
    private int maxBounces;

    @Option(names = "--bias", defaultValue = "1.0", description = "Ray distribution bias.")
    private double bias;

    @Option(names = "--ambientPower", defaultValue = "0", description = "Ambient luminosity added after tracing.")
    private double ambientPower;

    @Option(names = "--skyPower", defaultValue = "1", description = "Uniform sky power.")
    private double skyPower;

    @Option(names = "--sunPower", defaultValue = "0", description = "Sun power.")
    private double sunPower;

    @Option(names = "--sunAzimuth", defaultValue = "0", description = "Sun azimuth in degrees clockwise from north.")
    private double sunAzimuth;

    @Option(names = "--sunElevation", defaultValue = "45", description = "Sun elevation in degrees above the horizon.")
    private double sunElevation;

    @Option(names = "--sunAngularDiam", defaultValue = "11.4", description = "Sun angular diameter in degrees.")
    private double sunAngularDiameter;

    @Option(names = "--horizonDirections", defaultValue = "32", description = "Azimuth directions used by --algorithm=horizon.")
    private int horizonDirections;

    @Option(names = "--horizonRadiusMeters", description = "Maximum horizon search distance in metres for --algorithm=horizon.")
    private Double horizonRadiusMeters;

    @Option(names = "--tiled", description = "Compatibility alias; tiled mode is already the default.")
    private boolean tiled;

    @Option(names = "--singleFile", description = "Write one GeoTIFF covering the snapped requested BBOX.")
    private boolean singleFile;

    @Option(names = "--startTile", defaultValue = "0", description = "Start processing at this tile id.")
    private int startTile;

    @Option(names = "--outputByte", description = "Write tiled outputs as Byte after clamping [0,1] to [0,255].")
    private boolean outputByte;

    @Option(names = "--info", description = "Print raster metadata before processing.")
    private boolean printInfo;

    @Option(names = "--verbose", description = "Print additional detailed runtime logs.")
    private boolean verbose;

    @Option(names = "--threads", defaultValue = "0", description = "Number of worker threads. Default: available processors.")
    private int threads;

    @Override
    public Integer call() throws Exception {
        ConsoleLogger logger = new ConsoleLogger(
                new PrintWriter(commandSpec.commandLine().getOut(), true),
                new PrintWriter(commandSpec.commandLine().getErr(), true),
                clock,
                verbose);
        validateCli(logger);
        LightingParameters lightingParameters = new LightingParameters(
                maxBounces,
                raysPerPixel,
                bias,
                ambientPower,
                skyPower,
                sunPower,
                sunAzimuth,
                sunElevation,
                sunAngularDiameter,
                LightingParameters.defaults().materialReflectance());

        RunConfig runConfig = new RunConfig(
                inputLocation,
                bbox,
                algorithmMode,
                singleFile ? OutputMode.SINGLE_FILE : OutputMode.TILE_FILES,
                outputFile,
                outputDirectory,
                tileSizePixels,
                bufferPixels,
                bufferMeters,
                startTile,
                threads > 0 ? threads : Runtime.getRuntime().availableProcessors(),
                outputByte,
                verbose,
                printInfo,
                exaggeration,
                lightingParameters,
                new HorizonParameters(horizonDirections, horizonRadiusMeters));

        logger.info(
                "Run start: input=%s, bbox=%s, algorithm=%s, outputMode=%s, outputTarget=%s",
                runConfig.inputLocation(),
                runConfig.bbox(),
                runConfig.algorithmMode(),
                runConfig.outputMode(),
                runConfig.outputMode() == OutputMode.SINGLE_FILE ? runConfig.outputFile() : runConfig.outputDirectory());

        try (GeoToolsCogRasterSource rasterSource = new GeoToolsCogRasterSource(runConfig.inputLocation(), logger)) {
            logger.info(
                    "Raster opened: size=%dx%d, resolution=%.3f x %.3f, crs=%s, noData=%s",
                    rasterSource.metadata().width(),
                    rasterSource.metadata().height(),
                    rasterSource.metadata().resolutionX(),
                    rasterSource.metadata().resolutionY(),
                    rasterSource.metadata().crs().getName(),
                    Double.isNaN(rasterSource.metadata().noDataValue()) ? "NaN" : rasterSource.metadata().noDataValue());
            if (printInfo) {
                commandSpec.commandLine().getOut().println(rasterSource.metadata().describe());
                commandSpec.commandLine().getOut().printf("Threads: %d%n", runConfig.threads());
            }
            new OcclusionPipeline(logger, clock).run(rasterSource, runConfig);
        }
        return 0;
    }

    private void validateCli(ConsoleLogger logger) {
        if (singleFile && tiled) {
            throw new IllegalArgumentException("--singleFile and --tiled cannot be used together.");
        }
        if (!singleFile && outputFile != null && !"output.tif".equals(outputFile.toString())) {
            logger.warn("Ignoring -o because tiled mode is the default.");
        }
        ParseResult parseResult = commandSpec.commandLine().getParseResult();
        if (algorithmMode == AlgorithmMode.HORIZON) {
            if (maxBounces > 0) {
                throw new IllegalArgumentException("--algorithm=horizon supports only maxBounces=0.");
            }
            if (parseResult != null && parseResult.hasMatchedOption("-r")) {
                logger.warn("Ignoring -r because --algorithm=horizon does not use rays.");
            }
        } else if (parseResult != null) {
            if (parseResult.hasMatchedOption("--horizonDirections")) {
                logger.warn("Ignoring --horizonDirections because --algorithm=exact is active.");
            }
            if (parseResult.hasMatchedOption("--horizonRadiusMeters")) {
                logger.warn("Ignoring --horizonRadiusMeters because --algorithm=exact is active.");
            }
        }
    }

    public static final class BBoxConverter implements ITypeConverter<BBox> {
        @Override
        public BBox convert(String value) {
            return BBox.parse(value);
        }
    }

    public static final class AlgorithmModeConverter implements ITypeConverter<AlgorithmMode> {
        @Override
        public AlgorithmMode convert(String value) {
            return AlgorithmMode.fromCliValue(value);
        }
    }
}
