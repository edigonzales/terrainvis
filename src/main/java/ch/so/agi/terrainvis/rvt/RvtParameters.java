package ch.so.agi.terrainvis.rvt;

public sealed interface RvtParameters permits
        RvtParameters.NoneParameters,
        RvtParameters.HillshadeParameters,
        RvtParameters.MultiHillshadeParameters,
        RvtParameters.SlrmParameters,
        RvtParameters.SkyViewParameters,
        RvtParameters.SkyIlluminationParameters,
        RvtParameters.LocalDominanceParameters,
        RvtParameters.MsrmParameters,
        RvtParameters.MstpParameters,
        RvtParameters.VatParameters {

    record NoneParameters() implements RvtParameters {
    }

    record HillshadeParameters(double sunAzimuthDegrees, double sunElevationDegrees) implements RvtParameters {
        public HillshadeParameters {
            validateSun(sunAzimuthDegrees, sunElevationDegrees);
        }
    }

    record MultiHillshadeParameters(int directions, double sunElevationDegrees) implements RvtParameters {
        public MultiHillshadeParameters {
            if (directions <= 0) {
                throw new IllegalArgumentException("directions must be > 0");
            }
            validateSun(0.0, sunElevationDegrees);
        }
    }

    record SlrmParameters(int radiusCells) implements RvtParameters {
        public SlrmParameters {
            if (radiusCells <= 0) {
                throw new IllegalArgumentException("radiusCells must be > 0");
            }
        }
    }

    record SkyViewParameters(
            int directions,
            int radiusPixels,
            int noiseLevel,
            int anisotropyDirectionDegrees,
            int anisotropyLevel) implements RvtParameters {
        public SkyViewParameters {
            if (directions <= 0) {
                throw new IllegalArgumentException("directions must be > 0");
            }
            if (radiusPixels <= 0) {
                throw new IllegalArgumentException("radiusPixels must be > 0");
            }
            if (noiseLevel < 0 || noiseLevel > 3) {
                throw new IllegalArgumentException("noiseLevel must be in [0, 3]");
            }
            if (anisotropyDirectionDegrees < 0 || anisotropyDirectionDegrees > 360) {
                throw new IllegalArgumentException("anisotropyDirectionDegrees must be in [0, 360]");
            }
            if (anisotropyLevel < 1 || anisotropyLevel > 2) {
                throw new IllegalArgumentException("anisotropyLevel must be 1 or 2");
            }
        }
    }

    record SkyIlluminationParameters(
            String skyModel,
            boolean computeShadow,
            int directions,
            int shadowDistancePixels,
            double shadowAzimuthDegrees,
            double shadowElevationDegrees) implements RvtParameters {
        public SkyIlluminationParameters {
            if (!"overcast".equalsIgnoreCase(skyModel) && !"uniform".equalsIgnoreCase(skyModel)) {
                throw new IllegalArgumentException("skyModel must be overcast or uniform");
            }
            if (directions <= 0) {
                throw new IllegalArgumentException("directions must be > 0");
            }
            if (shadowDistancePixels <= 0) {
                throw new IllegalArgumentException("shadowDistancePixels must be > 0");
            }
            validateSun(shadowAzimuthDegrees, shadowElevationDegrees);
        }
    }

    record LocalDominanceParameters(
            int minimumRadiusPixels,
            int maximumRadiusPixels,
            int radiusIncrementPixels,
            int angularResolutionDegrees,
            double observerHeight) implements RvtParameters {
        public LocalDominanceParameters {
            if (minimumRadiusPixels <= 0 || maximumRadiusPixels < minimumRadiusPixels) {
                throw new IllegalArgumentException("local dominance radius range is invalid");
            }
            if (radiusIncrementPixels <= 0) {
                throw new IllegalArgumentException("radiusIncrementPixels must be > 0");
            }
            if (angularResolutionDegrees <= 0 || angularResolutionDegrees > 180) {
                throw new IllegalArgumentException("angularResolutionDegrees must be in (0, 180]");
            }
            if (observerHeight < 0.0) {
                throw new IllegalArgumentException("observerHeight must be >= 0");
            }
        }
    }

    record MsrmParameters(
            double featureMinimumMeters,
            double featureMaximumMeters,
            int scalingFactor) implements RvtParameters {
        public MsrmParameters {
            if (featureMinimumMeters < 0.0 || featureMaximumMeters <= featureMinimumMeters) {
                throw new IllegalArgumentException("msrm feature range is invalid");
            }
            if (scalingFactor <= 0) {
                throw new IllegalArgumentException("scalingFactor must be > 0");
            }
        }
    }

    record MstpParameters(
            Scale localScale,
            Scale mesoScale,
            Scale broadScale,
            double lightness) implements RvtParameters {
        public MstpParameters {
            if (localScale == null || mesoScale == null || broadScale == null) {
                throw new IllegalArgumentException("all mstp scales are required");
            }
            if (lightness <= 0.0) {
                throw new IllegalArgumentException("lightness must be > 0");
            }
        }

        public record Scale(int minimumRadius, int maximumRadius, int step) {
            public Scale {
                if (minimumRadius <= 0 || maximumRadius < minimumRadius) {
                    throw new IllegalArgumentException("invalid MSTP radius range");
                }
                if (step <= 0) {
                    throw new IllegalArgumentException("step must be > 0");
                }
            }
        }
    }

    record VatParameters(Terrain terrain, int generalOpacityPercent) implements RvtParameters {
        public VatParameters {
            if (terrain == null) {
                throw new IllegalArgumentException("terrain is required");
            }
            if (generalOpacityPercent < 0 || generalOpacityPercent > 100) {
                throw new IllegalArgumentException("generalOpacityPercent must be in [0, 100]");
            }
        }
    }

    enum Terrain {
        GENERAL,
        FLAT,
        COMBINED
    }

    private static void validateSun(double azimuthDegrees, double elevationDegrees) {
        if (azimuthDegrees < 0.0 || azimuthDegrees > 360.0) {
            throw new IllegalArgumentException("sun azimuth must be in [0, 360]");
        }
        if (elevationDegrees < 0.0 || elevationDegrees > 90.0) {
            throw new IllegalArgumentException("sun elevation must be in [0, 90]");
        }
    }
}
