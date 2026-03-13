package ch.so.agi.terrainvis.render;

import java.util.Arrays;
import java.util.List;

final class RenderRamp {
    private final float[] values;
    private final float[] red;
    private final float[] green;
    private final float[] blue;
    private final float[] alpha;

    private RenderRamp(float[] values, float[] red, float[] green, float[] blue, float[] alpha) {
        this.values = values;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    static RenderRamp fromSpec(RenderLayerSpec spec) {
        if (spec.stops() != null) {
            return fromStops(spec.stops());
        }
        return new RenderRamp(
                new float[] {spec.valueMin().floatValue(), spec.valueMax().floatValue()},
                new float[] {spec.colorFrom().red() / 255.0f, spec.colorTo().red() / 255.0f},
                new float[] {spec.colorFrom().green() / 255.0f, spec.colorTo().green() / 255.0f},
                new float[] {spec.colorFrom().blue() / 255.0f, spec.colorTo().blue() / 255.0f},
                new float[] {1.0f, 1.0f});
    }

    static RenderRamp fromStops(List<RenderRampStop> stops) {
        int size = stops.size();
        float[] values = new float[size];
        float[] red = new float[size];
        float[] green = new float[size];
        float[] blue = new float[size];
        float[] alpha = new float[size];
        for (int i = 0; i < size; i++) {
            RenderRampStop stop = stops.get(i);
            values[i] = (float) stop.value();
            red[i] = stop.color().red() / 255.0f;
            green[i] = stop.color().green() / 255.0f;
            blue[i] = stop.color().blue() / 255.0f;
            alpha[i] = stop.alpha().floatValue();
        }
        return new RenderRamp(values, red, green, blue, alpha);
    }

    void sample(float value, Sample sample) {
        int lastIndex = values.length - 1;
        if (value <= values[0]) {
            sample.set(red[0], green[0], blue[0], alpha[0]);
            return;
        }
        if (value >= values[lastIndex]) {
            sample.set(red[lastIndex], green[lastIndex], blue[lastIndex], alpha[lastIndex]);
            return;
        }

        int index = Arrays.binarySearch(values, value);
        if (index >= 0) {
            sample.set(red[index], green[index], blue[index], alpha[index]);
            return;
        }

        int upper = -index - 1;
        int lower = upper - 1;
        float t = (value - values[lower]) / (values[upper] - values[lower]);
        sample.set(
                interpolate(red[lower], red[upper], t),
                interpolate(green[lower], green[upper], t),
                interpolate(blue[lower], blue[upper], t),
                interpolate(alpha[lower], alpha[upper], t));
    }

    private float interpolate(float start, float end, float t) {
        return start + ((end - start) * t);
    }

    static final class Sample {
        private float red;
        private float green;
        private float blue;
        private float alpha;

        float red() {
            return red;
        }

        float green() {
            return green;
        }

        float blue() {
            return blue;
        }

        float alpha() {
            return alpha;
        }

        private void set(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }
    }
}
