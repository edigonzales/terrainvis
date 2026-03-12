package ch.so.agi.terrainvis.rvt;

import java.util.Locale;

public enum RvtProduct {
    SLOPE("slope"),
    HILLSHADE("hillshade"),
    MULTI_HILLSHADE("multi-hillshade"),
    SLRM("slrm"),
    SVF("svf"),
    ASVF("asvf"),
    POSITIVE_OPENNESS("positive-openness"),
    NEGATIVE_OPENNESS("negative-openness"),
    SKY_ILLUMINATION("sky-illumination"),
    LOCAL_DOMINANCE("local-dominance"),
    MSRM("msrm"),
    MSTP("mstp"),
    VAT("vat");

    private final String cliValue;

    RvtProduct(String cliValue) {
        this.cliValue = cliValue;
    }

    public static RvtProduct fromCliValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rvt product is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (RvtProduct product : values()) {
            if (product.cliValue.equals(normalized)) {
                return product;
            }
        }
        throw new IllegalArgumentException("Unsupported RVT product: " + value);
    }

    @Override
    public String toString() {
        return cliValue;
    }
}
