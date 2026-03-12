package ch.so.agi.terrainvis.tiling;

import static org.assertj.core.api.Assertions.assertThat;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;

import ch.so.agi.terrainvis.config.BBox;
import ch.so.agi.terrainvis.raster.RasterMetadata;

class TilePlannerTest {
    @Test
    void plansSnappedTilesWithinClippedRequestedWindow() throws Exception {
        RasterMetadata metadata = new RasterMetadata(
                10,
                10,
                new ReferencedEnvelope(100.0, 110.0, 190.0, 200.0, CRS.decode("EPSG:2056", true)),
                CRS.decode("EPSG:2056", true),
                1.0,
                1.0,
                -9999.0,
                1,
                "test");

        TilePlan plan = new TilePlanner().plan(metadata, new BBox(100.2, 193.7, 104.1, 198.4), 3, 1, 2);

        assertThat(plan.requestedWindow()).isEqualTo(new PixelWindow(0, 1, 5, 6));
        assertThat(plan.tiles()).hasSize(4);
        assertThat(plan.tiles().get(0).window().bufferedWindow()).isEqualTo(new PixelWindow(0, 0, 4, 6));
        assertThat(plan.tiles().get(3).window().coreWindow()).isEqualTo(new PixelWindow(3, 4, 2, 3));
    }
}
