package io.github.rookiecuzz.rookieregions.editor.model;

import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.Bounds3D;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.Point2D;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;
import io.github.rookiecuzz.rookieregions.core.shape.SlicedPolygonShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionDraftTest {
    private static final WorldId WORLD = new WorldId(
            UUID.fromString("71000000-0000-0000-0000-000000000001"),
            "minecraft:overworld"
    );

    @Test
    void cuboidSelectionUsesInclusiveBlocksAndExclusiveMaximumBounds() {
        RegionDraft draft = RegionDraft.cuboid(WORLD)
                .setPos1(new BlockPoint(5, 10, 7))
                .setPos2(new BlockPoint(3, 8, 7));

        CuboidShape shape = assertInstanceOf(CuboidShape.class, draft.buildShape());

        assertEquals(new Bounds3D(3, 8, 7, 6, 11, 8), shape.bounds());
        assertTrue(shape.contains(3, 8, 7));
        assertTrue(shape.contains(5, 10, 7));
        assertFalse(shape.contains(5, 11, 7));
        assertThrows(
                IllegalStateException.class,
                () -> RegionDraft.cuboid(WORLD)
                        .setPos1(new BlockPoint(0, 0, 0))
                        .buildShape()
        );
    }

    @Test
    void cuboidMaximumIntegerCoordinateDoesNotOverflow() {
        CuboidShape shape = assertInstanceOf(
                CuboidShape.class,
                RegionDraft.cuboid(WORLD)
                        .setPos1(new BlockPoint(Integer.MAX_VALUE, 0, 0))
                        .setPos2(new BlockPoint(Integer.MAX_VALUE, 0, 0))
                        .buildShape()
        );

        assertEquals(2147483648.0d, shape.bounds().maxX());
        assertEquals(1.0d, shape.bounds().maxY());
    }

    @Test
    void polygonSupportsAddUndoClearAndExplicitHeightRange() {
        RegionDraft draft = RegionDraft.polygon(WORLD);
        draft.addPoint(0, 0).addPoint(4, 0).addPoint(0, 4);

        assertEquals(new Point2D(0, 4), draft.undoPoint().orElseThrow());
        assertEquals(2, draft.points().size());
        draft.addPoint(new BlockPoint(0, 999, 4));
        assertThrows(IllegalStateException.class, draft::buildShape);

        draft.setPolygonHeights(-5, 20);
        PolygonPrismShape shape = assertInstanceOf(
                PolygonPrismShape.class,
                draft.buildShape()
        );
        assertEquals(-5, shape.minY());
        assertEquals(20, shape.maxY());
        assertEquals(List.of(
                new Point2D(0, 0), new Point2D(4, 0), new Point2D(0, 4)
        ), shape.vertices());

        assertEquals(3, draft.clearPoints());
        assertTrue(draft.points().isEmpty());
        assertThrows(IllegalStateException.class, draft::buildShape);
    }

    @Test
    void polygonRejectsInvalidHeightAndInvalidGeometry() {
        RegionDraft draft = RegionDraft.polygon(WORLD)
                .setHeightRange(5, 5)
                .addPoint(0, 0)
                .addPoint(1, 1)
                .addPoint(2, 2);

        assertThrows(IllegalStateException.class, draft::buildShape);
        draft.setMaxY(6);
        assertThrows(IllegalArgumentException.class, draft::buildShape);
        assertThrows(IllegalArgumentException.class, () -> draft.setMinY(Double.NaN));
    }

    @Test
    void slicedDraftEditsCurrentSliceAndBuildsSortedStepwiseShape() {
        RegionDraft draft = RegionDraft.sliced(WORLD);
        assertThrows(IllegalStateException.class, () -> draft.addPoint(0, 0));

        addSquare(draft.selectSlice(10), 10, 10, 12, 12);
        addSquare(draft.switchSlice(0), 0, 0, 4, 4);
        draft.setSlicedMaxY(20);

        SlicedPolygonShape shape = assertInstanceOf(
                SlicedPolygonShape.class,
                draft.buildShape()
        );
        assertEquals(List.of(0.0d, 10.0d), shape.slices().stream()
                .map(SlicedPolygonShape.Slice::y)
                .toList());
        assertEquals(0, shape.minY());
        assertEquals(0, draft.minY().orElseThrow());
        assertEquals(20, shape.maxY());
        assertEquals(0, draft.currentSliceY().orElseThrow());
        assertTrue(shape.contains(2, 5, 2));
        assertFalse(shape.contains(2, 15, 2));
        assertTrue(shape.contains(11, 15, 11));
    }

    @Test
    void slicedDraftRequiresEverySliceAndFinalMaxYToBeValid() {
        RegionDraft draft = RegionDraft.sliced(WORLD);
        addSquare(draft.selectSlice(5), 0, 0, 2, 2);

        assertThrows(IllegalStateException.class, draft::buildShape);
        draft.setMaxY(5);
        assertThrows(IllegalStateException.class, draft::buildShape);
        draft.setMaxY(6).selectSlice(4).addPoint(0, 0).addPoint(1, 0);
        assertThrows(IllegalStateException.class, draft::buildShape);

        RegionDraft mismatchedMin = RegionDraft.sliced(WORLD);
        addSquare(mismatchedMin.selectSlice(5), 0, 0, 2, 2);
        mismatchedMin.setSlicedMinY(4).setSlicedMaxY(6);
        assertThrows(IllegalStateException.class, mismatchedMin::buildShape);
    }

    @Test
    void explicitMinYEditsPolygonAndMovesTheFirstSlicedLayer() {
        RegionDraft polygon = RegionDraft.polygon(WORLD)
                .setPolygonHeights(0, 10)
                .setMinY(-4);
        addSquare(polygon, 0, 0, 2, 2);
        assertEquals(-4, polygon.buildShape().minY());

        RegionDraft sliced = RegionDraft.sliced(WORLD);
        addSquare(sliced.selectSlice(5), 0, 0, 2, 2);
        addSquare(sliced.selectSlice(10), 1, 1, 3, 3);
        sliced.setMaxY(20).selectSlice(5).setMinY(2);
        SlicedPolygonShape shape = assertInstanceOf(
                SlicedPolygonShape.class, sliced.buildShape()
        );
        assertEquals(2, shape.minY());
        assertEquals(List.of(2.0d, 10.0d), shape.slices().stream()
                .map(SlicedPolygonShape.Slice::y).toList());
        assertEquals(2, sliced.currentSliceY().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> sliced.setMinY(10));
    }

    @Test
    void existingShapesRoundTripAndDraftCopiesAreIndependent() {
        CuboidShape cuboid = new CuboidShape(-2, 0, 3, 5, 7, 9);
        assertEquals(
                ShapeRelation.EQUAL,
                cuboid.relationTo(RegionDraft.fromShape(WORLD, cuboid).buildShape())
        );

        PolygonPrismShape polygon = new PolygonPrismShape(0.5, 8.5, List.of(
                new Point2D(0.25, 0.25),
                new Point2D(4.25, 0.25),
                new Point2D(0.25, 4.25)
        ));
        RegionDraft original = RegionDraft.fromShape(WORLD, polygon);
        RegionDraft copy = original.copy();
        copy.addPoint(9, 9);
        assertEquals(3, original.points().size());
        assertEquals(4, copy.points().size());
        assertEquals(ShapeRelation.EQUAL, polygon.relationTo(original.buildShape()));

        SlicedPolygonShape sliced = new SlicedPolygonShape(2, 9, List.of(
                new SlicedPolygonShape.Slice(2, List.of(
                        new Point2D(0, 0), new Point2D(3, 0), new Point2D(0, 3)
                )),
                new SlicedPolygonShape.Slice(6, List.of(
                        new Point2D(1, 1), new Point2D(4, 1), new Point2D(1, 4)
                ))
        ));
        RegionDraft slicedDraft = RegionDraft.fromShape(WORLD, sliced);
        assertEquals(2, slicedDraft.minY().orElseThrow());
        assertEquals(ShapeRelation.EQUAL, sliced.relationTo(slicedDraft.buildShape()));

        assertThrows(
                IllegalArgumentException.class,
                () -> RegionDraft.fromShape(WORLD, GlobalShape.INSTANCE)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RegionDraft.fromShape(
                        WORLD, new CuboidShape(0.5, 0, 0, 2, 2, 2)
                )
        );
    }

    @Test
    void operationsAreRestrictedToTheirShapeKind() {
        assertThrows(
                IllegalStateException.class,
                () -> RegionDraft.cuboid(WORLD).addPoint(0, 0)
        );
        assertThrows(
                IllegalStateException.class,
                () -> RegionDraft.polygon(WORLD).selectSlice(0)
        );
        assertThrows(
                IllegalStateException.class,
                () -> RegionDraft.cuboid(WORLD).setMinY(0)
        );
    }

    private static void addSquare(RegionDraft draft,
                                  int minX,
                                  int minZ,
                                  int maxX,
                                  int maxZ) {
        draft.addPoint(minX, minZ)
                .addPoint(maxX, minZ)
                .addPoint(maxX, maxZ)
                .addPoint(minX, maxZ);
    }
}
