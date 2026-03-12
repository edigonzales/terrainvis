package ch.so.agi.terrainvis.cli;

import java.io.PrintWriter;
import java.time.Clock;
import java.util.concurrent.Callable;

import ch.so.agi.terrainvis.config.CommonRunConfig;
import ch.so.agi.terrainvis.pipeline.RvtPipeline;
import ch.so.agi.terrainvis.raster.GeoToolsCogRasterSource;
import ch.so.agi.terrainvis.rvt.RvtParameters;
import ch.so.agi.terrainvis.rvt.RvtProduct;
import ch.so.agi.terrainvis.rvt.RvtRunConfig;
import ch.so.agi.terrainvis.util.ConsoleLogger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
        name = "rvt",
        description = "terrainvis RVT-style terrain visualizations.",
        mixinStandardHelpOptions = true,
        subcommands = {
                RvtCommands.SlopeCommand.class,
                RvtCommands.HillshadeCommand.class,
                RvtCommands.MultiHillshadeCommand.class,
                RvtCommands.SlrmCommand.class,
                RvtCommands.SvfCommand.class,
                RvtCommands.AsvfCommand.class,
                RvtCommands.PositiveOpennessCommand.class,
                RvtCommands.NegativeOpennessCommand.class,
                RvtCommands.SkyIlluminationCommand.class,
                RvtCommands.LocalDominanceCommand.class,
                RvtCommands.MsrmCommand.class,
                RvtCommands.MstpCommand.class,
                RvtCommands.VatCommand.class})
public final class RvtCommands {
    private RvtCommands() {
    }

    abstract static class AbstractRvtCommand implements Callable<Integer> {
        @Spec
        private CommandSpec commandSpec;

        @Mixin
        private CliSupport.CommonOptions commonOptions;

        @Option(names = "-e", defaultValue = "1.0", description = "Vertical exaggeration.")
        private double exaggeration;

        private final Clock clock;

        protected AbstractRvtCommand() {
            this(CliSupport.defaultClock());
        }

        AbstractRvtCommand(Clock clock) {
            this.clock = clock;
        }

        protected abstract RvtProduct product();

        protected abstract RvtParameters parameters();

        @Override
        public Integer call() throws Exception {
            ConsoleLogger logger = new ConsoleLogger(
                    new PrintWriter(commandSpec.commandLine().getOut(), true),
                    new PrintWriter(commandSpec.commandLine().getErr(), true),
                    clock,
                    commonOptions.verbose);
            CommonRunConfig commonRunConfig = CliSupport.commonRunConfig(commonOptions);
            try (GeoToolsCogRasterSource rasterSource = new GeoToolsCogRasterSource(commonRunConfig.inputLocation(), logger)) {
                if (commonRunConfig.printInfo()) {
                    commandSpec.commandLine().getOut().println(rasterSource.metadata().describe());
                    commandSpec.commandLine().getOut().printf("Threads: %d%n", commonRunConfig.tilingConfig().threads());
                }
                new RvtPipeline(logger, clock).run(
                        rasterSource,
                        new RvtRunConfig(commonRunConfig, product(), exaggeration, parameters()));
            }
            return 0;
        }
    }

    @Command(name = "slope", description = "Slope gradient in degrees.", mixinStandardHelpOptions = true)
    static final class SlopeCommand extends AbstractRvtCommand {
        @Override
        protected RvtProduct product() {
            return RvtProduct.SLOPE;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.NoneParameters();
        }
    }

    @Command(name = "hillshade", description = "Single-direction hillshade.", mixinStandardHelpOptions = true)
    static final class HillshadeCommand extends AbstractRvtCommand {
        @Option(names = "--sun-azimuth", defaultValue = "315", description = "Sun azimuth in degrees clockwise from north.")
        private double sunAzimuth;

        @Option(names = "--sun-elevation", defaultValue = "35", description = "Sun elevation in degrees above the horizon.")
        private double sunElevation;

        @Override
        protected RvtProduct product() {
            return RvtProduct.HILLSHADE;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.HillshadeParameters(sunAzimuth, sunElevation);
        }
    }

    @Command(name = "multi-hillshade", description = "Multi-direction hillshade stack.", mixinStandardHelpOptions = true)
    static final class MultiHillshadeCommand extends AbstractRvtCommand {
        @Option(names = "--directions", defaultValue = "16", description = "Number of azimuth directions.")
        private int directions;

        @Option(names = "--sun-elevation", defaultValue = "35", description = "Sun elevation in degrees above the horizon.")
        private double sunElevation;

        @Override
        protected RvtProduct product() {
            return RvtProduct.MULTI_HILLSHADE;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.MultiHillshadeParameters(directions, sunElevation);
        }
    }

    @Command(name = "slrm", description = "Simple local relief model.", mixinStandardHelpOptions = true)
    static final class SlrmCommand extends AbstractRvtCommand {
        @Option(names = "--radius-cells", defaultValue = "20", description = "Trend radius in pixels.")
        private int radiusCells;

        @Override
        protected RvtProduct product() {
            return RvtProduct.SLRM;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.SlrmParameters(radiusCells);
        }
    }

    abstract static class AbstractSkyViewCommand extends AbstractRvtCommand {
        @Option(names = "--svf-directions", defaultValue = "16", description = "Number of directions.")
        private int directions;

        @Option(names = "--svf-radius-px", defaultValue = "10", description = "Maximum search radius in pixels.")
        private int radiusPixels;

        @Option(names = "--svf-noise", defaultValue = "0", description = "Noise reduction level in [0,3].")
        private int noiseLevel;

        @Option(names = "--asvf-direction", defaultValue = "315", description = "Main anisotropy direction in degrees.")
        private int anisotropyDirection;

        @Option(names = "--asvf-level", defaultValue = "1", description = "Anisotropy level: 1 or 2.")
        private int anisotropyLevel;

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.SkyViewParameters(
                    directions,
                    radiusPixels,
                    noiseLevel,
                    anisotropyDirection,
                    anisotropyLevel);
        }
    }

    @Command(name = "svf", description = "Sky-view factor.", mixinStandardHelpOptions = true)
    static final class SvfCommand extends AbstractSkyViewCommand {
        @Override
        protected RvtProduct product() {
            return RvtProduct.SVF;
        }
    }

    @Command(name = "asvf", description = "Anisotropic sky-view factor.", mixinStandardHelpOptions = true)
    static final class AsvfCommand extends AbstractSkyViewCommand {
        @Override
        protected RvtProduct product() {
            return RvtProduct.ASVF;
        }
    }

    @Command(name = "positive-openness", description = "Positive openness.", mixinStandardHelpOptions = true)
    static final class PositiveOpennessCommand extends AbstractSkyViewCommand {
        @Override
        protected RvtProduct product() {
            return RvtProduct.POSITIVE_OPENNESS;
        }
    }

    @Command(name = "negative-openness", description = "Negative openness.", mixinStandardHelpOptions = true)
    static final class NegativeOpennessCommand extends AbstractSkyViewCommand {
        @Override
        protected RvtProduct product() {
            return RvtProduct.NEGATIVE_OPENNESS;
        }
    }

    @Command(name = "sky-illumination", description = "Sky illumination.", mixinStandardHelpOptions = true)
    static final class SkyIlluminationCommand extends AbstractRvtCommand {
        @Option(names = "--sky-model", defaultValue = "overcast", description = "Sky model: overcast or uniform.")
        private String skyModel;

        @Option(names = "--compute-shadow", description = "Include binary shadow term.")
        private boolean computeShadow;

        @Option(names = "--directions", defaultValue = "32", description = "Number of directions.")
        private int directions;

        @Option(names = "--shadow-distance-px", defaultValue = "100", description = "Maximum shadow distance in pixels.")
        private int shadowDistancePixels;

        @Option(names = "--shadow-azimuth", defaultValue = "315", description = "Shadow azimuth in degrees clockwise from north.")
        private double shadowAzimuth;

        @Option(names = "--shadow-elevation", defaultValue = "35", description = "Shadow elevation in degrees above the horizon.")
        private double shadowElevation;

        @Override
        protected RvtProduct product() {
            return RvtProduct.SKY_ILLUMINATION;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.SkyIlluminationParameters(
                    skyModel,
                    computeShadow,
                    directions,
                    shadowDistancePixels,
                    shadowAzimuth,
                    shadowElevation);
        }
    }

    @Command(name = "local-dominance", description = "Local dominance.", mixinStandardHelpOptions = true)
    static final class LocalDominanceCommand extends AbstractRvtCommand {
        @Option(names = "--min-radius-px", defaultValue = "10", description = "Minimum radius in pixels.")
        private int minimumRadiusPixels;

        @Option(names = "--max-radius-px", defaultValue = "20", description = "Maximum radius in pixels.")
        private int maximumRadiusPixels;

        @Option(names = "--radius-inc-px", defaultValue = "1", description = "Radius increment in pixels.")
        private int radiusIncrementPixels;

        @Option(names = "--angular-resolution", defaultValue = "15", description = "Angular resolution in degrees.")
        private int angularResolutionDegrees;

        @Option(names = "--observer-height", defaultValue = "1.7", description = "Observer height.")
        private double observerHeight;

        @Override
        protected RvtProduct product() {
            return RvtProduct.LOCAL_DOMINANCE;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.LocalDominanceParameters(
                    minimumRadiusPixels,
                    maximumRadiusPixels,
                    radiusIncrementPixels,
                    angularResolutionDegrees,
                    observerHeight);
        }
    }

    @Command(name = "msrm", description = "Multi-scale relief model.", mixinStandardHelpOptions = true)
    static final class MsrmCommand extends AbstractRvtCommand {
        @Option(names = "--feature-min-m", defaultValue = "1", description = "Minimum feature size in metres.")
        private double featureMinimumMeters;

        @Option(names = "--feature-max-m", defaultValue = "20", description = "Maximum feature size in metres.")
        private double featureMaximumMeters;

        @Option(names = "--scaling-factor", defaultValue = "2", description = "Scaling factor.")
        private int scalingFactor;

        @Override
        protected RvtProduct product() {
            return RvtProduct.MSRM;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.MsrmParameters(featureMinimumMeters, featureMaximumMeters, scalingFactor);
        }
    }

    @Command(name = "mstp", description = "Multi-scale topographic position.", mixinStandardHelpOptions = true)
    static final class MstpCommand extends AbstractRvtCommand {
        @Option(names = "--local-scale", defaultValue = "1,5,1", converter = CliSupport.MstpScaleConverter.class, description = "Local scale min,max,step.")
        private RvtParameters.MstpParameters.Scale localScale;

        @Option(names = "--meso-scale", defaultValue = "5,50,5", converter = CliSupport.MstpScaleConverter.class, description = "Meso scale min,max,step.")
        private RvtParameters.MstpParameters.Scale mesoScale;

        @Option(names = "--broad-scale", defaultValue = "50,500,50", converter = CliSupport.MstpScaleConverter.class, description = "Broad scale min,max,step.")
        private RvtParameters.MstpParameters.Scale broadScale;

        @Option(names = "--lightness", defaultValue = "0.9", description = "MSTP lightness.")
        private double lightness;

        @Override
        protected RvtProduct product() {
            return RvtProduct.MSTP;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.MstpParameters(localScale, mesoScale, broadScale, lightness);
        }
    }

    @Command(name = "vat", description = "Visualisation for Archaeological Topography.", mixinStandardHelpOptions = true)
    static final class VatCommand extends AbstractRvtCommand {
        @Option(names = "--terrain", defaultValue = "combined", converter = CliSupport.TerrainConverter.class, description = "VAT terrain preset: general, flat or combined.")
        private RvtParameters.Terrain terrain;

        @Option(names = "--general-opacity", defaultValue = "50", description = "Opacity of VAT general over VAT flat for the combined preset.")
        private int generalOpacityPercent;

        @Override
        protected RvtProduct product() {
            return RvtProduct.VAT;
        }

        @Override
        protected RvtParameters parameters() {
            return new RvtParameters.VatParameters(terrain, generalOpacityPercent);
        }
    }
}
