package io.github.rookiecuzz.rookieregions.core.shape;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeContainsTest {
    @Test
    void cuboidIncludesXZBoundaryAndUsesHalfOpenY(){
        CuboidShape shape = new CuboidShape(-2, 4, -3, 6, 9, 8);

        assertTrue(shape.contains(-2, 4, -3));
        assertTrue(shape.contains(6, 8.999, 8));
        assertFalse(shape.contains(0, 9, 0));
        assertFalse(shape.contains(6.001, 5, 0));
        assertFalse(shape.contains(Double.NaN, 5, 0));
    }

    @Test
    void polygonPrismSupportsConcavityAndIncludesEdges(){
        PolygonPrismShape shape = new PolygonPrismShape(10, 20, List.of(
                point(0, 0), point(6, 0), point(6, 2),
                point(2, 2), point(2, 6), point(0, 6)
        ));

        assertTrue(shape.contains(1, 10, 5));
        assertTrue(shape.contains(2, 15, 4));
        assertTrue(shape.contains(0, 19.999, 0));
        assertFalse(shape.contains(4, 15, 4));
        assertFalse(shape.contains(1, 20, 1));
    }

    @Test
    void slicedShapeSwitchesExactlyAtEachSlice(){
        SlicedPolygonShape shape = new SlicedPolygonShape(0, 21, List.of(
                slice(0, square(0, 0, 2, 2)),
                slice(10, square(10, 10, 14, 14)),
                slice(20, square(-5, -5, -1, -1))
        ));

        assertTrue(shape.contains(1, 9.999, 1));
        assertFalse(shape.contains(1, 10, 1));
        assertTrue(shape.contains(12, 10, 12));
        assertFalse(shape.contains(12, 20, 12));
        assertTrue(shape.contains(-3, 20, -3));
        assertFalse(shape.contains(-3, 21, -3));
    }

    @Test
    void globalAcceptsOnlyFiniteCoordinates(){
        assertTrue(GlobalShape.INSTANCE.contains(30_000_000, -64, -30_000_000));
        assertFalse(GlobalShape.INSTANCE.contains(Double.POSITIVE_INFINITY, 0, 0));
        assertFalse(GlobalShape.INSTANCE.contains(0, Double.NaN, 0));
        assertFalse(GlobalShape.INSTANCE.bounds().isFinite());
    }

    @Test
    void valueObjectsAndShapesRejectInvalidGeometry(){
        assertThrows(IllegalArgumentException.class, () -> new Point2D(Double.NaN, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Bounds3D(2, 0, 0, 1, 1, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CuboidShape(Bounds3D.unbounded())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CuboidShape(0, 0, 0, 0, 1, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PolygonPrismShape(0, 1, List.of(
                        point(0, 0), point(2, 2), point(0, 2), point(2, 0)
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlicedPolygonShape(0, 5, List.of(
                        slice(0, square(0, 0, 2, 2)),
                        slice(0, square(3, 3, 5, 5))
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlicedPolygonShape(0, 4, List.of(
                        slice(0, square(0, 0, 2, 2)),
                        slice(4, square(3, 3, 5, 5))
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SlicedPolygonShape(1, 5, List.of(
                        slice(0, square(0, 0, 2, 2))
                ))
        );
    }

    @Test
    void polygonAndSliceCollectionsAreDefensivelyFrozen(){
        PolygonPrismShape polygon = new PolygonPrismShape(0, 2, square(0, 0, 2, 2));
        SlicedPolygonShape sliced = new SlicedPolygonShape(0, 2, List.of(
                slice(0, square(0, 0, 2, 2))
        ));

        assertThrows(UnsupportedOperationException.class, () -> polygon.vertices().clear());
        assertThrows(UnsupportedOperationException.class, () -> sliced.slices().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> sliced.slices().get(0).vertices().clear()
        );
        assertEquals(0.0, sliced.minY());
        assertEquals(2.0, sliced.maxY());
    }

    private static SlicedPolygonShape.Slice slice(double y, List<Point2D> vertices){
        return new SlicedPolygonShape.Slice(y, vertices);
    }

    private static List<Point2D> square(double minX,
                                        double minZ,
                                        double maxX,
                                        double maxZ){
        return List.of(
                point(minX, minZ), point(maxX, minZ),
                point(maxX, maxZ), point(minX, maxZ)
        );
    }

    private static Point2D point(double x, double z){
        return new Point2D(x, z);
    }
}
