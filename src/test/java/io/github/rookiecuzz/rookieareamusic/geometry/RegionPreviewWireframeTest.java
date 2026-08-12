package io.github.rookiecuzz.rookieareamusic.geometry;

import io.github.rookiecuzz.rookieareamusic.config.RegionShapeConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionPreviewWireframeTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void singleSliceIncludesItsExclusiveTopAtYPlusOne(){
        RegionPreviewWireframe wireframe = RegionPreviewWireframe.from(shape(
                slice(10, point(0, 0), point(2, 0), point(2, 2), point(0, 2))
        ));

        assertEquals(10.0, wireframe.getMinY(), EPSILON);
        assertEquals(11.0, wireframe.getMaxExclusiveY(), EPSILON);
        assertEquals(1, wireframe.getSliceCount());

        List<RegionPreviewWireframe.Sample> samples = wireframe.sample(1000, 0.0);
        assertFalse(samples.isEmpty());
        assertTrue(hasStyle(samples, RegionPreviewWireframe.Style.BASE));
        assertTrue(hasStyle(samples, RegionPreviewWireframe.Style.VERTICAL));
        assertTrue(hasStyle(samples, RegionPreviewWireframe.Style.FINAL_TOP));
        assertFalse(hasStyle(samples, RegionPreviewWireframe.Style.TRANSITION_TOP));

        for(RegionPreviewWireframe.Sample sample : samples){
            switch(sample.getStyle()){
                case BASE:
                    assertEquals(10.0, sample.getY(), EPSILON);
                    assertTrue(onSquareBoundary(sample, 0, 2));
                    break;
                case FINAL_TOP:
                    assertEquals(11.0, sample.getY(), EPSILON);
                    assertTrue(onSquareBoundary(sample, 0, 2));
                    break;
                case VERTICAL:
                    assertTrue(isSquareVertex(sample, 0, 2));
                    assertTrue(sample.getY() >= 10.0 - EPSILON);
                    assertTrue(sample.getY() <= 11.0 + EPSILON);
                    break;
                default:
                    throw new AssertionError("Unexpected style " + sample.getStyle());
            }
        }
    }

    @Test
    void buildsIndependentPrismsForDifferentVertexCounts(){
        RegionPreviewWireframe wireframe = RegionPreviewWireframe.from(shape(
                // Deliberately unsorted: production normalization must define
                // the active height order used by the preview.
                slice(15, point(10, 10), point(13, 10), point(10, 13)),
                slice(10, point(0, 0), point(4, 0), point(4, 4), point(0, 4))
        ));

        assertEquals(10.0, wireframe.getMinY(), EPSILON);
        assertEquals(16.0, wireframe.getMaxExclusiveY(), EPSILON);
        assertEquals(2, wireframe.getSliceCount());

        List<RegionPreviewWireframe.Sample> samples = wireframe.sample(10000, 0.37);
        assertTrue(hasStyle(samples, RegionPreviewWireframe.Style.BASE));
        assertTrue(hasStyle(samples, RegionPreviewWireframe.Style.TRANSITION_TOP));
        assertTrue(hasStyle(samples, RegionPreviewWireframe.Style.VERTICAL));
        assertTrue(hasStyle(samples, RegionPreviewWireframe.Style.FINAL_TOP));

        for(RegionPreviewWireframe.Sample sample : samples){
            switch(sample.getStyle()){
                case BASE:
                    assertTrue(
                            (near(sample.getY(), 10.0) && onSquareBoundary(sample, 0, 4))
                                    || (near(sample.getY(), 15.0) && onTriangleBoundary(sample))
                    );
                    break;
                case TRANSITION_TOP:
                    assertEquals(15.0, sample.getY(), EPSILON);
                    assertTrue(onSquareBoundary(sample, 0, 4));
                    break;
                case FINAL_TOP:
                    assertEquals(16.0, sample.getY(), EPSILON);
                    assertTrue(onTriangleBoundary(sample));
                    break;
                case VERTICAL:
                    boolean oldPrism = isSquareVertex(sample, 0, 4)
                            && sample.getY() >= 10.0 - EPSILON
                            && sample.getY() <= 15.0 + EPSILON;
                    boolean lastPrism = isTriangleVertex(sample)
                            && sample.getY() >= 15.0 - EPSILON
                            && sample.getY() <= 16.0 + EPSILON;
                    assertTrue(oldPrism || lastPrism,
                            "vertical samples must not connect unrelated slice vertices");
                    break;
                default:
                    throw new AssertionError("Unexpected style " + sample.getStyle());
            }
        }
    }

    @Test
    void constrainedBudgetReservesSamplesForEveryVisualRole(){
        RegionPreviewWireframe wireframe = RegionPreviewWireframe.from(shape(
                slice(0, point(0, 0), point(100, 0), point(100, 100), point(0, 100)),
                slice(100, point(1000, 1000), point(1001, 1000), point(1000, 1001))
        ));

        List<RegionPreviewWireframe.Sample> samples = wireframe.sample(8, 0.0);
        assertEquals(8, samples.size());
        Map<RegionPreviewWireframe.Style, Integer> counts = countStyles(samples);
        for(RegionPreviewWireframe.Style style : RegionPreviewWireframe.Style.values()){
            assertEquals(2, counts.get(style));
        }
    }

    @Test
    void cyclicPhaseRotatesStylesWhenBudgetIsSmallerThanStyleCount(){
        RegionPreviewWireframe wireframe = RegionPreviewWireframe.from(shape(
                slice(0, point(0, 0), point(3, 0), point(0, 3)),
                slice(2, point(10, 10), point(13, 10), point(10, 13))
        ));

        List<RegionPreviewWireframe.Sample> first = wireframe.sample(1, 0.0);
        List<RegionPreviewWireframe.Sample> second = wireframe.sample(1, 0.26);
        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(RegionPreviewWireframe.Style.BASE, first.get(0).getStyle());
        assertEquals(RegionPreviewWireframe.Style.TRANSITION_TOP,
                second.get(0).getStyle());
    }

    @Test
    void samplingNeverExceedsBudgetAndHandlesDisabledBudget(){
        RegionPreviewWireframe wireframe = RegionPreviewWireframe.from(shape(
                slice(4, point(0, 0), point(5, 0), point(0, 5))
        ));

        assertTrue(wireframe.sample(0, 0.0).isEmpty());
        assertTrue(wireframe.sample(-1, 0.0).isEmpty());
        assertTrue(wireframe.sample(7, Double.NaN).size() <= 7);
        assertTrue(wireframe.sample(7, Double.POSITIVE_INFINITY).size() <= 7);
    }

    private Map<RegionPreviewWireframe.Style, Integer> countStyles(
            List<RegionPreviewWireframe.Sample> samples){
        Map<RegionPreviewWireframe.Style, Integer> result =
                new EnumMap<>(RegionPreviewWireframe.Style.class);
        for(RegionPreviewWireframe.Style style : RegionPreviewWireframe.Style.values()){
            result.put(style, 0);
        }
        for(RegionPreviewWireframe.Sample sample : samples){
            result.put(sample.getStyle(), result.get(sample.getStyle()) + 1);
        }
        return result;
    }

    private boolean hasStyle(List<RegionPreviewWireframe.Sample> samples,
                             RegionPreviewWireframe.Style expected){
        for(RegionPreviewWireframe.Sample sample : samples){
            if(sample.getStyle() == expected){
                return true;
            }
        }
        return false;
    }

    private boolean onSquareBoundary(RegionPreviewWireframe.Sample sample,
                                     double minimum,
                                     double maximum){
        boolean xEdge = (near(sample.getX(), minimum) || near(sample.getX(), maximum))
                && sample.getZ() >= minimum - EPSILON
                && sample.getZ() <= maximum + EPSILON;
        boolean zEdge = (near(sample.getZ(), minimum) || near(sample.getZ(), maximum))
                && sample.getX() >= minimum - EPSILON
                && sample.getX() <= maximum + EPSILON;
        return xEdge || zEdge;
    }

    private boolean isSquareVertex(RegionPreviewWireframe.Sample sample,
                                   double minimum,
                                   double maximum){
        return (near(sample.getX(), minimum) || near(sample.getX(), maximum))
                && (near(sample.getZ(), minimum) || near(sample.getZ(), maximum));
    }

    private boolean onTriangleBoundary(RegionPreviewWireframe.Sample sample){
        double x = sample.getX();
        double z = sample.getZ();
        boolean first = near(z, 10.0) && x >= 10.0 - EPSILON && x <= 13.0 + EPSILON;
        boolean second = near(x + z, 23.0)
                && x >= 10.0 - EPSILON && x <= 13.0 + EPSILON
                && z >= 10.0 - EPSILON && z <= 13.0 + EPSILON;
        boolean third = near(x, 10.0) && z >= 10.0 - EPSILON && z <= 13.0 + EPSILON;
        return first || second || third;
    }

    private boolean isTriangleVertex(RegionPreviewWireframe.Sample sample){
        return (near(sample.getX(), 10.0) && near(sample.getZ(), 10.0))
                || (near(sample.getX(), 13.0) && near(sample.getZ(), 10.0))
                || (near(sample.getX(), 10.0) && near(sample.getZ(), 13.0));
    }

    private boolean near(double first, double second){
        return Math.abs(first - second) <= EPSILON;
    }

    private RegionShapeConfig shape(RegionShapeConfig.Slice... slices){
        return RegionShapeConfig.builder().slices(Arrays.asList(slices)).build();
    }

    private RegionShapeConfig.Slice slice(double y, RegionShapeConfig.Point... points){
        return RegionShapeConfig.Slice.builder()
                .y(y)
                .polygon(Arrays.asList(points))
                .build();
    }

    private RegionShapeConfig.Point point(double x, double z){
        return RegionShapeConfig.Point.builder().x(x).z(z).build();
    }
}
