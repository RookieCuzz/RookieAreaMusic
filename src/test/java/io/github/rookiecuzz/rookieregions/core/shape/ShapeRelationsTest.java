package io.github.rookiecuzz.rookieregions.core.shape;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeRelationsTest {
    @Test
    void classifiesAllSixCuboidRelationsAndTheirInverse(){
        CuboidShape base = cuboid(0, 0, 0, 10, 10, 10);
        assertPair(ShapeRelation.DISJOINT, base, cuboid(20, 0, 0, 30, 10, 10));
        assertPair(ShapeRelation.TOUCHING, base, cuboid(10, 2, 2, 15, 8, 8));
        assertPair(ShapeRelation.TOUCHING, base, cuboid(2, 10, 2, 8, 15, 8));
        assertPair(ShapeRelation.OVERLAP, base, cuboid(5, 5, 5, 15, 15, 15));
        assertPair(ShapeRelation.CONTAINS, base, cuboid(0, 2, 0, 5, 8, 5));
        assertPair(ShapeRelation.EQUAL, base, cuboid(0, 0, 0, 10, 10, 10));
    }

    @Test
    void cuboidAndEquivalentPolygonPrismAreGeometricallyEqual(){
        RegionShape cuboid = cuboid(0, 0, 0, 10, 10, 10);
        RegionShape polygon = prism(0, 10, square(0, 0, 10, 10));

        assertPair(ShapeRelation.EQUAL, cuboid, polygon);
    }

    @Test
    void properContainmentMaySharePartOfTheBoundary(){
        RegionShape parent = prism(0, 10, square(0, 0, 10, 10));
        RegionShape child = cuboid(0, 2, 0, 5, 8, 5);

        assertEquals(ShapeRelation.INSIDE, child.relationTo(parent));
        assertEquals(ShapeRelation.CONTAINS, parent.relationTo(child));
        assertFalse(child.relationTo(parent) == ShapeRelation.EQUAL);
    }

    @Test
    void distinguishesHorizontalContactFromPositiveAreaOverlap(){
        RegionShape first = prism(0, 5, square(0, 0, 4, 4));
        RegionShape edgeTouch = prism(1, 4, square(4, 1, 8, 3));
        RegionShape cornerTouch = prism(1, 4, square(4, 4, 8, 8));
        RegionShape partial = prism(1, 4, square(3, 1, 8, 3));

        assertPair(ShapeRelation.TOUCHING, first, edgeTouch);
        assertPair(ShapeRelation.TOUCHING, first, cornerTouch);
        assertPair(ShapeRelation.OVERLAP, first, partial);
    }

    @Test
    void disjointYGapCannotBeHiddenByEqualFootprints(){
        RegionShape first = prism(0, 5, square(0, 0, 4, 4));
        RegionShape touching = prism(5, 8, square(0, 0, 4, 4));
        RegionShape separated = prism(6, 8, square(0, 0, 4, 4));

        assertPair(ShapeRelation.TOUCHING, first, touching);
        assertPair(ShapeRelation.DISJOINT, first, separated);
    }

    @Test
    void concaveNotchPreventsFalseContainment(){
        RegionShape concave = prism(0, 5, List.of(
                point(0, 0), point(6, 0), point(6, 2),
                point(2, 2), point(2, 6), point(0, 6)
        ));
        RegionShape crossingNotch = prism(1, 4, square(1, 1, 5, 3));

        assertPair(ShapeRelation.OVERLAP, concave, crossingNotch);
    }

    @Test
    void redundantSlicedBoundariesDoNotChangeEquality(){
        RegionShape prism = prism(0, 10, square(0, 0, 5, 5));
        RegionShape sliced = new SlicedPolygonShape(0, 10, List.of(
                slice(0, square(0, 0, 5, 5)),
                slice(3, reversedSquare(0, 0, 5, 5)),
                slice(7, squareWithClosingVertex(0, 0, 5, 5))
        ));

        assertPair(ShapeRelation.EQUAL, prism, sliced);
    }

    @Test
    void cuboidAndSlicedPolygonUseTheSameCentralRelationEngine(){
        RegionShape cuboid = cuboid(0, 0, 0, 5, 10, 5);
        RegionShape sliced = new SlicedPolygonShape(0, 10, List.of(
                slice(0, square(0, 0, 5, 5)),
                slice(5, reversedSquare(0, 0, 5, 5))
        ));

        assertPair(ShapeRelation.EQUAL, cuboid, sliced);
    }

    @Test
    void outgoingSliceClosureCanTouchAtTransitionWithoutVolumeOverlap(){
        RegionShape stepped = new SlicedPolygonShape(0, 10, List.of(
                slice(0, square(0, 0, 4, 4)),
                slice(5, square(20, 20, 24, 24))
        ));
        RegionShape startsAtTransition = prism(5, 8, square(0, 0, 4, 4));

        assertPair(ShapeRelation.TOUCHING, stepped, startsAtTransition);
    }

    @Test
    void overlapInOnlyOnePositiveHeightSlabStillCounts(){
        RegionShape stepped = new SlicedPolygonShape(0, 10, List.of(
                slice(0, square(0, 0, 2, 2)),
                slice(5, square(10, 10, 14, 14))
        ));
        RegionShape other = prism(6, 9, square(12, 12, 16, 16));

        assertPair(ShapeRelation.OVERLAP, stepped, other);
    }

    @Test
    void positiveIntersectionVolumeCoversEveryFiniteCrossTypePair(){
        RegionShape cuboid = cuboid(0, 0, 0, 4, 10, 4);
        RegionShape polygon = prism(2, 8, square(2, 0, 6, 4));
        RegionShape sliced = new SlicedPolygonShape(0, 10, List.of(
                slice(0, square(0, 0, 2, 2)),
                slice(5, square(3, 0, 5, 4))
        ));

        assertVolume("48", cuboid, polygon);
        assertVolume("40", cuboid, sliced);
        assertVolume("24", polygon, sliced);
        assertVolume("0", cuboid, prism(10, 12, square(0, 0, 4, 4)));
    }

    @Test
    void globalRelationsAreExplicitAndDirectional(){
        RegionShape finite = prism(-10, 10, square(-5, -5, 5, 5));

        assertPair(ShapeRelation.INSIDE, finite, GlobalShape.INSTANCE);
        assertEquals(
                ShapeRelation.EQUAL,
                GlobalShape.INSTANCE.relationTo(GlobalShape.INSTANCE)
        );
    }

    @Test
    void relationHelpersExposePositiveVolumeAndDirection(){
        assertEquals(ShapeRelation.CONTAINS, ShapeRelation.INSIDE.inverse());
        assertEquals(ShapeRelation.INSIDE, ShapeRelation.CONTAINS.inverse());
        assertEquals(ShapeRelation.TOUCHING, ShapeRelation.TOUCHING.inverse());
        assertTrue(ShapeRelation.OVERLAP.hasPositiveVolumeIntersection());
        assertFalse(ShapeRelation.TOUCHING.hasPositiveVolumeIntersection());
    }

    private static void assertPair(ShapeRelation expected,
                                   RegionShape first,
                                   RegionShape second){
        assertEquals(expected, first.relationTo(second));
        assertEquals(expected.inverse(), second.relationTo(first));
    }

    private static void assertVolume(String expected,
                                     RegionShape first,
                                     RegionShape second){
        BigDecimal value = new BigDecimal(expected);
        assertEquals(0, value.compareTo(
                ShapeRelations.positiveIntersectionVolume(first, second)
        ));
        assertEquals(0, value.compareTo(
                ShapeRelations.positiveIntersectionVolume(second, first)
        ));
    }

    private static CuboidShape cuboid(double minX,
                                      double minY,
                                      double minZ,
                                      double maxX,
                                      double maxY,
                                      double maxZ){
        return new CuboidShape(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static PolygonPrismShape prism(double minY,
                                           double maxY,
                                           List<Point2D> vertices){
        return new PolygonPrismShape(minY, maxY, vertices);
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

    private static List<Point2D> reversedSquare(double minX,
                                                double minZ,
                                                double maxX,
                                                double maxZ){
        return List.of(
                point(minX, minZ), point(minX, maxZ),
                point(maxX, maxZ), point(maxX, minZ)
        );
    }

    private static List<Point2D> squareWithClosingVertex(double minX,
                                                         double minZ,
                                                         double maxX,
                                                         double maxZ){
        return List.of(
                point(minX, minZ), point(maxX, minZ),
                point(maxX, maxZ), point(minX, maxZ),
                point(minX, minZ)
        );
    }

    private static Point2D point(double x, double z){
        return new Point2D(x, z);
    }
}
