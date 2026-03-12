package ch.so.agi.terrainvis.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Callable;

import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.config.OutputConfig;
import ch.so.agi.terrainvis.config.OutputDataType;
import ch.so.agi.terrainvis.config.OutputMode;
import ch.so.agi.terrainvis.config.TilingConfig;
import ch.so.agi.terrainvis.render.RenderPipeline;
import ch.so.agi.terrainvis.render.RenderRunConfig;
import ch.so.agi.terrainvis.render.RenderStyle;
import ch.so.agi.terrainvis.util.ConsoleLogger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "render",
        description = "terrainvis generic raster rendering and compositing.",
        mixinStandardHelpOptions = true,
        subcommands = {RenderCommands.ComposeCommand.class})
public final class RenderCommands {
    private RenderCommands() {
    }

    @Command(name = "compose", description = "Colorize and composite single-band rasters tile by tile.", mixinStandardHelpOptions = true)
    static final class ComposeCommand implements Callable<Integer> {
        @Spec
        private CommandSpec commandSpec;

        @Option(names = "--style", required = true, description = "Render style JSON file.")
        private Path stylePath;

        @Option(names = "--bbox", required = true, converter = CliSupport.BBoxConverter.class, description = "BBOX in raster CRS: minX,minY,maxX,maxY")
        private BBox bbox;

        @Option(names = "--output-mode", defaultValue = "tile-files", converter = CliSupport.OutputModeConverter.class, description = "Output mode: single-file or tile-files.")
        private OutputMode outputMode;

        @Option(names = "--output", description = "Output file path for single-file mode or directory for tile-files mode.")
        private Path output;

        @Option(names = "--tile-size", defaultValue = "2000", description = "Tile size in pixels.")
        private int tileSizePixels;

        @Option(names = "--threads", defaultValue = "0", description = "Number of worker threads. Default: available processors.")
        private int threads;

        @Option(names = "--start-tile", defaultValue = "0", description = "Start processing at this tile id.")
        private int startTile;

        @Option(names = "--with-alpha", description = "Write RGBA output instead of RGB.")
        private boolean withAlpha;

        @Option(names = "--info", description = "Print raster metadata before processing.")
        private boolean printInfo;

        @Option(names = "--verbose", description = "Print additional detailed runtime logs.")
        private boolean verbose;

        private final Clock clock;

        ComposeCommand() {
            this(CliSupport.defaultClock());
        }

        ComposeCommand(Clock clock) {
            this.clock = clock;
        }

        @Override
        public Integer call() throws Exception {
            ConsoleLogger logger = new ConsoleLogger(
                    new PrintWriter(commandSpec.commandLine().getOut(), true),
                    new PrintWriter(commandSpec.commandLine().getErr(), true),
                    clock,
                    verbose);
            Path resolvedOutput = output;
            if (resolvedOutput == null) {
                resolvedOutput = outputMode == OutputMode.SINGLE_FILE ? Path.of("output.tif") : Path.of("output_tiles");
            }
            int resolvedThreads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
            RenderStyle style = RenderStyle.load(stylePath);
            RenderRunConfig runConfig = new RenderRunConfig(
                    stylePath,
                    style,
                    bbox,
                    new OutputConfig(outputMode, resolvedOutput, OutputDataType.UINT8),
                    new TilingConfig(tileSizePixels, null, null, startTile, resolvedThreads),
                    withAlpha,
                    printInfo,
                    verbose);
            new RenderPipeline(logger, clock).run(runConfig);
            return 0;
        }
    }
}
