package ch.so.agi.terrainvis.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ch.so.agi.terrainvis.config.LightingParameters;

class RaySamplerTest {
    @Test
    void producesDeterministicDirections() {
        RaySampler sampler = new RaySampler();
        LightingParameters parameters = new LightingParameters(0, 64, 1.0, 0.0, 1.0, 0.0, 0.0, 45.0, 11.4, 1.0);

        Vector3 first = sampler.primaryDirection(3, 17, 9, parameters);
        Vector3 second = sampler.primaryDirection(3, 17, 9, parameters);
        Vector3 third = sampler.primaryDirection(3, 17, 10, parameters);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(third);
    }
}
