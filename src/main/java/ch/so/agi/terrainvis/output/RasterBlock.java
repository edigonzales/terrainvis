package ch.so.agi.terrainvis.output;

public record RasterBlock(
        int width,
        int height,
        int bandCount,
        double noDataValue,
        float[][] bands) {

    public RasterBlock {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        if (bandCount <= 0) {
            throw new IllegalArgumentException("bandCount must be > 0");
        }
        if (bands == null || bands.length != bandCount) {
            throw new IllegalArgumentException("bands must match bandCount");
        }
        int expectedSize = width * height;
        for (float[] band : bands) {
            if (band == null || band.length != expectedSize) {
                throw new IllegalArgumentException("each band must have width*height samples");
            }
        }
    }

    public static RasterBlock singleBand(int width, int height, double noDataValue, float[] data) {
        return new RasterBlock(width, height, 1, noDataValue, new float[][] {data});
    }
}
