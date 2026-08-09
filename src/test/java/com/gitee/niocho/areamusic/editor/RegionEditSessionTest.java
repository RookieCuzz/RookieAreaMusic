package com.gitee.niocho.areamusic.editor;

import com.gitee.niocho.areamusic.config.RegionShapeConfig;
import com.gitee.niocho.areamusic.geometry.SlicedPolygonVolume;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEditSessionTest {
    @Test
    void supportsAddUndoAndClear(){
        RegionEditSession session = createAt(10);

        session.addPoint(0.5, 0.5);
        session.addPoint(4.5, 0.5);
        assertEquals(2, session.getDraft().size());
        assertTrue(session.undoLastPoint());
        assertEquals(1, session.getDraft().size());
        session.clearCurrentSlice();
        assertTrue(session.getDraft().isEmpty());
        assertFalse(session.undoLastPoint());
    }

    @Test
    void copiesPolygonAndUsesHigherPlayerYForNextSlice(){
        RegionEditSession session = createAt(10);
        addSquare(session, 0.0, 4.0);

        session.saveAndNext(15, false);

        assertEquals(15, session.getCurrentY());
        assertEquals(4, session.getDraft().size());
        assertEquals(1, session.getSavedSliceCount());
        assertEquals(0.0, session.getDraft().get(0).getX());
    }

    @Test
    void nextSliceFallsBackToCurrentYPlusOne(){
        RegionEditSession session = createAt(10);
        addSquare(session, 0.0, 4.0);

        session.saveAndNext(9, false);

        assertEquals(11, session.getCurrentY());
    }

    @Test
    void sneakingNextStartsBlankEvenWhenTargetWasPreviouslySaved(){
        RegionEditSession session = createAt(10);
        addSquare(session, 0.0, 4.0);
        session.saveAndNext(12, false);
        session.saveAndPrevious();

        session.saveAndNext(12, true);

        assertEquals(12, session.getCurrentY());
        assertTrue(session.getDraft().isEmpty());
    }

    @Test
    void previousSavesCurrentAndReturnsToLowerSavedSlice(){
        RegionEditSession session = createAt(10);
        addSquare(session, 0.0, 4.0);
        session.saveAndNext(11, true);
        addTriangle(session);

        session.saveAndPrevious();

        assertEquals(10, session.getCurrentY());
        assertEquals(2, session.getSavedSliceCount());
        assertEquals(4, session.getDraft().size());
    }

    @Test
    void enforcesWorldHeightBoundaries(){
        RegionEditSession session = new RegionEditSession(
                RegionEditSession.Mode.CREATE,
                "world",
                "roi",
                0,
                10,
                10,
                null
        );
        addSquare(session, 0.0, 4.0);

        assertThrows(IllegalStateException.class, () -> session.saveAndNext(10, false));
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                RegionEditSession.Mode.CREATE,
                "world",
                "roi2",
                0,
                10,
                11,
                null
        ));
    }

    @Test
    void rejectsTooFewPointsAndZeroArea(){
        RegionEditSession session = createAt(10);
        session.addPoint(0.0, 0.0);
        session.addPoint(1.0, 0.0);
        assertNotNull(session.currentValidationError());
        assertThrows(IllegalStateException.class, session::saveCurrentSlice);

        session.addPoint(2.0, 0.0);
        assertTrue(session.currentValidationError().contains("面积"));
    }

    @Test
    void rejectsSelfIntersectingPolygon(){
        RegionEditSession session = createAt(10);
        session.addPoint(0.0, 0.0);
        session.addPoint(4.0, 4.0);
        session.addPoint(0.0, 4.0);
        session.addPoint(4.0, 0.0);
        session.addPoint(5.0, 2.0);

        assertNotNull(session.currentValidationError());
        assertThrows(IllegalStateException.class, session::saveCurrentSlice);
    }

    @Test
    void finishSortsSlicesAndBuildsProductionShape(){
        RegionEditSession session = createAt(10);
        addSquare(session, 0.0, 4.0);
        session.saveAndNext(15, true);
        addTriangle(session);

        RegionShapeConfig result = session.finish();

        assertEquals("sliced_polygon", result.getType());
        assertEquals(2, result.getSlices().size());
        assertEquals(10.0, result.getSlices().get(0).getY());
        assertEquals(15.0, result.getSlices().get(1).getY());
    }

    @Test
    void editRejectsNonIntegerDuplicateAndOutOfRangeSlices(){
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                RegionEditSession.Mode.EDIT,
                "world",
                "decimal",
                0,
                20,
                10,
                shape(slice(10.5))
        ));
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                RegionEditSession.Mode.EDIT,
                "world",
                "duplicate",
                0,
                20,
                10,
                shape(slice(10.0), slice(10.0))
        ));
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                RegionEditSession.Mode.EDIT,
                "world",
                "outside",
                0,
                20,
                10,
                shape(slice(21.0))
        ));
    }

    @Test
    void cancelRequiresSecondUseWithinFiveSeconds(){
        RegionEditSession session = createAt(10);

        assertFalse(session.confirmCancel(1_000L));
        assertTrue(session.confirmCancel(5_999L));
        assertFalse(session.confirmCancel(6_000L));
        assertFalse(session.confirmCancel(11_001L));
    }

    @Test
    void initialShapeIsCopiedSoCancelCannotMutateOriginal(){
        RegionShapeConfig original = shape(slice(10.0));
        RegionEditSession session = new RegionEditSession(
                RegionEditSession.Mode.EDIT,
                "world",
                "existing",
                0,
                20,
                10,
                original
        );

        session.clearCurrentSlice();

        assertEquals(4, original.getSlices().get(0).getPolygon().size());
        assertNotNull(session.currentValidationError());
    }

    @Test
    void refusesPointsBeyondPerSliceLimit(){
        RegionEditSession session = createAt(10);
        for(int index = 0; index < SlicedPolygonVolume.MAX_VERTICES_PER_SLICE; index++){
            session.addPoint(index, 0.0);
        }

        assertThrows(IllegalStateException.class, () -> session.addPoint(999.0, 0.0));
        assertEquals(SlicedPolygonVolume.MAX_VERTICES_PER_SLICE, session.getDraft().size());
    }

    @Test
    void refusesNavigatingToMoreThanTheMaximumSlices(){
        List<RegionShapeConfig.Slice> slices = new ArrayList<>();
        for(int index = 0; index < SlicedPolygonVolume.MAX_SLICE_COUNT; index++){
            slices.add(triangleSlice(index, 1.0));
        }
        RegionEditSession session = new RegionEditSession(
                RegionEditSession.Mode.EDIT,
                "world",
                "many-slices",
                0,
                SlicedPolygonVolume.MAX_SLICE_COUNT,
                SlicedPolygonVolume.MAX_SLICE_COUNT - 1,
                RegionShapeConfig.builder().slices(slices).build()
        );

        assertThrows(IllegalStateException.class, () -> session.saveAndNext(
                SlicedPolygonVolume.MAX_SLICE_COUNT,
                true
        ));
    }

    @Test
    void refusesSavingBeyondTheTotalVertexLimit(){
        int verticesPerSlice = 128;
        int sliceCount = SlicedPolygonVolume.MAX_TOTAL_VERTICES / verticesPerSlice;
        List<RegionShapeConfig.Slice> slices = new ArrayList<>();
        for(int index = 0; index < sliceCount; index++){
            slices.add(RegionShapeConfig.Slice.builder()
                    .y((double) index)
                    .polygon(regularPolygon(verticesPerSlice, 10.0))
                    .build());
        }
        RegionEditSession session = new RegionEditSession(
                RegionEditSession.Mode.EDIT,
                "world",
                "vertex-budget",
                0,
                sliceCount,
                sliceCount - 1,
                RegionShapeConfig.builder().slices(slices).build()
        );
        session.saveAndNext(sliceCount, true);
        addTriangle(session);

        assertThrows(IllegalStateException.class, session::saveCurrentSlice);
    }

    private RegionEditSession createAt(int y){
        return new RegionEditSession(
                RegionEditSession.Mode.CREATE,
                "world",
                "roi",
                0,
                100,
                y,
                null
        );
    }

    private void addSquare(RegionEditSession session, double min, double max){
        session.addPoint(min, min);
        session.addPoint(max, min);
        session.addPoint(max, max);
        session.addPoint(min, max);
    }

    private void addTriangle(RegionEditSession session){
        session.addPoint(0.0, 0.0);
        session.addPoint(3.0, 0.0);
        session.addPoint(0.0, 3.0);
    }

    private RegionShapeConfig shape(RegionShapeConfig.Slice... slices){
        return RegionShapeConfig.builder().slices(Arrays.asList(slices)).build();
    }

    private RegionShapeConfig.Slice slice(double y){
        return RegionShapeConfig.Slice.builder()
                .y(y)
                .polygon(Arrays.asList(
                        point(0.0, 0.0),
                        point(4.0, 0.0),
                        point(4.0, 4.0),
                        point(0.0, 4.0)
                ))
                .build();
    }

    private RegionShapeConfig.Point point(double x, double z){
        return RegionShapeConfig.Point.builder().x(x).z(z).build();
    }

    private RegionShapeConfig.Slice triangleSlice(int y, double size){
        return RegionShapeConfig.Slice.builder()
                .y((double) y)
                .polygon(Arrays.asList(
                        point(0.0, 0.0),
                        point(size, 0.0),
                        point(0.0, size)
                ))
                .build();
    }

    private List<RegionShapeConfig.Point> regularPolygon(int count, double radius){
        List<RegionShapeConfig.Point> result = new ArrayList<>();
        for(int index = 0; index < count; index++){
            double angle = Math.PI * 2.0 * index / count;
            result.add(point(Math.cos(angle) * radius, Math.sin(angle) * radius));
        }
        return result;
    }
}
