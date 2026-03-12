package ch.so.agi.terrainvis.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ch.so.agi.terrainvis.config.LightingParameters;

class LightingModelTest {
    @Test
    void addsSkyAndSunWhenRayPointsAtSun() {
        LightingParameters parameters = new LightingParameters(0, 16, 1.0, 0.0, 1.5, 2.0, 90.0, 0.0, 45.0, 1.0);

        double contribution = LightingModel.visibleContribution(new Vector3(1.0, 0.0, 0.0), parameters, 1.0);

        assertThat(contribution).isEqualTo(3.5);
    }
}
