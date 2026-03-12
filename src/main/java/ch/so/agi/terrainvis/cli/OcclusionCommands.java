package ch.so.agi.terrainvis.cli;

import java.io.PrintWriter;
import java.time.Clock;
import java.util.concurrent.Callable;

import ch.so.agi.terrainvis.config.AlgorithmMode;
import ch.so.agi.terrainvis.config.CommonRunConfig;
import ch.so.agi.terrainvis.config.HorizonParameters;
import ch.so.agi.terrainvis.config.LightingParameters;
import ch.so.agi.terrainvis.config.OcclusionJobConfig;
import ch.so.agi.terrainvis.pipeline.OcclusionPipeline;
import ch.so.agi.terrainvis.raster.GeoToolsCogRasterSource;
import ch.so.agi.terrainvis.util.ConsoleLogger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "occlusion",
        description = "terrainvis occlusion algorithms.",
        mixinStandardHelpOptions = true,
        subcommands = {OcclusionCommands.ExactCommand.class, OcclusionCommands.HorizonCommand.class})
public final class OcclusionCommands {
    private OcclusionCommands() {
    }

    abstract static class AbstractOcclusionCommand implements Callable<Integer> {
        @Spec
        private CommandSpec commandSpec;

        @Mixin
        private CliSupport.CommonOptions commonOptions;

        private final Clock clock;

        protected AbstractOcclusionCommand() {
            this(CliSupport.defaultClock());
        }

        AbstractOcclusionCommand(Clock clock) {
            this.clock = clock;
        }

        protected abstract AlgorithmMode algorithmMode();

        protected abstract LightingParameters lightingParameters();

        protected abstract HorizonParameters horizonParameters();

        protected abstract double exaggeration();

        @Override
        public Integer call() throws Exception {
            ConsoleLogger logger = new ConsoleLogger(
                    new PrintWriter(commandSpec.commandLine().getOut(), true),
                    new PrintWriter(commandSpec.commandLine().getErr(), true),
                    clock,
                    commonOptions.verbose);
            CommonRunConfig commonRunConfig = CliSupport.commonRunConfig(commonOptions);
            OcclusionJobConfig jobConfig = new OcclusionJobConfig(
                    commonRunConfig,
                    algorithmMode(),
                    exaggeration(),
                    lightingParameters(),
                    horizonParameters());
            try (GeoToolsCogRasterSource rasterSource = new GeoToolsCogRasterSource(commonRunConfig.inputLocation(), logger)) {
                if (commonRunConfig.printInfo()) {
                    commandSpec.commandLine().getOut().println(rasterSource.metadata().describe());
                    commandSpec.commandLine().getOut().printf("Threads: %d%n", commonRunConfig.tilingConfig().threads());
                }
                new OcclusionPipeline(logger, clock).run(rasterSource, jobConfig.toLegacyRunConfig());
            }
            return 0;
        }
    }

    @Command(name = "exact", description = "Exact CPU raytracing occlusion.", mixinStandardHelpOptions = true)
    static final class ExactCommand extends AbstractOcclusionCommand {
        @picocli.CommandLine.Option(names = "-r", defaultValue = "1024", description = "Rays per pixel.")
        private int raysPerPixel;

        @picocli.CommandLine.Option(names = "-B", defaultValue = "0", description = "Maximum number of ray bounces.")
        private int maxBounces;

        @picocli.CommandLine.Option(names = "--bias", defaultValue = "1.0", description = "Ray distribution bias.")
        private double bias;

        @picocli.CommandLine.Option(names = "--ambient-power", defaultValue = "0", description = "Ambient luminosity added after tracing.")
        private double ambientPower;

        @picocli.CommandLine.Option(names = "--sky-power", defaultValue = "1", description = "Uniform sky power.")
        private double skyPower;

        @picocli.CommandLine.Option(names = "--sun-power", defaultValue = "0", description = "Sun power.")
        private double sunPower;

        @picocli.CommandLine.Option(names = "--sun-azimuth", defaultValue = "0", description = "Sun azimuth in degrees clockwise from north.")
        private double sunAzimuth;

        @picocli.CommandLine.Option(names = "--sun-elevation", defaultValue = "45", description = "Sun elevation in degrees above the horizon.")
        private double sunElevation;

        @picocli.CommandLine.Option(names = "--sun-angular-diam", defaultValue = "11.4", description = "Sun angular diameter in degrees.")
        private double sunAngularDiameter;

        @picocli.CommandLine.Option(names = "-e", defaultValue = "1.0", description = "Vertical exaggeration.")
        private double exaggeration;

        @Override
        protected AlgorithmMode algorithmMode() {
            return AlgorithmMode.EXACT;
        }

        @Override
        protected LightingParameters lightingParameters() {
            return new LightingParameters(
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
        }

        @Override
        protected HorizonParameters horizonParameters() {
            return HorizonParameters.defaults();
        }

        @Override
        protected double exaggeration() {
            return exaggeration;
        }
    }

    @Command(name = "horizon", description = "Horizon-based approximate occlusion.", mixinStandardHelpOptions = true)
    static final class HorizonCommand extends AbstractOcclusionCommand {
        @picocli.CommandLine.Option(names = "--bias", defaultValue = "1.0", description = "Sky visibility bias.")
        private double bias;

        @picocli.CommandLine.Option(names = "--ambient-power", defaultValue = "0", description = "Ambient luminosity added after tracing.")
        private double ambientPower;

        @picocli.CommandLine.Option(names = "--sky-power", defaultValue = "1", description = "Uniform sky power.")
        private double skyPower;

        @picocli.CommandLine.Option(names = "--sun-power", defaultValue = "0", description = "Sun power.")
        private double sunPower;

        @picocli.CommandLine.Option(names = "--sun-azimuth", defaultValue = "0", description = "Sun azimuth in degrees clockwise from north.")
        private double sunAzimuth;

        @picocli.CommandLine.Option(names = "--sun-elevation", defaultValue = "45", description = "Sun elevation in degrees above the horizon.")
        private double sunElevation;

        @picocli.CommandLine.Option(names = "--sun-angular-diam", defaultValue = "11.4", description = "Sun angular diameter in degrees.")
        private double sunAngularDiameter;

        @picocli.CommandLine.Option(names = "--horizon-directions", defaultValue = "32", description = "Azimuth directions used by the horizon algorithm.")
        private int horizonDirections;

        @picocli.CommandLine.Option(names = "--horizon-radius-m", description = "Maximum horizon search distance in metres.")
        private Double horizonRadiusMeters;

        @picocli.CommandLine.Option(names = "-e", defaultValue = "1.0", description = "Vertical exaggeration.")
        private double exaggeration;

        @Override
        protected AlgorithmMode algorithmMode() {
            return AlgorithmMode.HORIZON;
        }

        @Override
        protected LightingParameters lightingParameters() {
            return new LightingParameters(
                    0,
                    1,
                    bias,
                    ambientPower,
                    skyPower,
                    sunPower,
                    sunAzimuth,
                    sunElevation,
                    sunAngularDiameter,
                    LightingParameters.defaults().materialReflectance());
        }

        @Override
        protected HorizonParameters horizonParameters() {
            return new HorizonParameters(horizonDirections, horizonRadiusMeters);
        }

        @Override
        protected double exaggeration() {
            return exaggeration;
        }
    }
}
