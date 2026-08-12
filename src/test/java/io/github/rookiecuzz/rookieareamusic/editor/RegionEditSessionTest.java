package io.github.rookiecuzz.rookieareamusic.editor;

import io.github.rookiecuzz.rookieareamusic.config.RegionShapeConfig;
import io.github.rookiecuzz.rookieareamusic.geometry.SlicedPolygonVolume;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEditSessionTest {
    @Test
    void createStartsWithoutHeightAndFirstClickUsesBlockY(){
        RegionEditSession session = create();

        assertTrue(session.isAwaitingHeight());
        assertNull(session.getCurrentY());
        assertEquals(
                RegionEditSession.AddPointResult.HEIGHT_LOCKED_AND_POINT_ADDED,
                session.addPoint(42, 0.5, 0.5)
        );
        assertEquals(42, session.getCurrentY());
        assertEquals(1, session.getDraft().size());
    }

    @Test
    void laterClicksOnlyUseXZAndKeepFirstBlockY(){
        RegionEditSession session = create();
        session.addPoint(42, 0.0, 0.0);

        assertEquals(
                RegionEditSession.AddPointResult.POINT_ADDED,
                session.addPoint(99, 4.0, 0.0)
        );
        assertEquals(42, session.getCurrentY());
        assertEquals(2, session.getDraft().size());
    }

    @Test
    void undoingFirstPointUnlocksNewSliceHeight(){
        RegionEditSession session = create();
        session.addPoint(42, 0.0, 0.0);

        assertTrue(session.undoLastPoint());
        assertTrue(session.isAwaitingHeight());
        session.addPoint(55, 1.0, 1.0);
        assertEquals(55, session.getCurrentY());
    }

    @Test
    void clearUnlocksUnsavedBlankSliceButPreservesExistingSliceHeight(){
        RegionEditSession created = create();
        created.addPoint(42, 0.0, 0.0);
        created.clearCurrentSlice();
        assertTrue(created.isAwaitingHeight());

        RegionEditSession existing = edit(shape(slice(10.0)), null);
        existing.clearCurrentSlice();
        assertEquals(10, existing.getCurrentY());
        assertFalse(existing.isAwaitingHeight());
    }

    @Test
    void normalNextCopiesPolygonAndFirstClickOnlyLocksNewHeight(){
        RegionEditSession session = create();
        addSquare(session, 10, 0.0, 4.0);
        session.saveAndNext(false);

        assertTrue(session.isAwaitingHeight());
        assertEquals(4, session.getDraft().size());
        assertEquals(
                RegionEditSession.AddPointResult.HEIGHT_LOCKED_FOR_COPIED_POLYGON,
                session.addPoint(15, 99.5, 99.5)
        );
        assertEquals(15, session.getCurrentY());
        assertEquals(4, session.getDraft().size());
        assertEquals(0.0, session.getDraft().get(0).getX());
    }

    @Test
    void copiedHeightLockCanBeUndoneWithoutDeletingPolygon(){
        RegionEditSession session = create();
        addSquare(session, 10, 0.0, 4.0);
        session.saveAndNext(false);
        session.addPoint(15, 99.0, 99.0);

        assertTrue(session.undoLastPoint());
        assertTrue(session.isAwaitingHeight());
        assertEquals(4, session.getDraft().size());
        session.addPoint(18, 99.0, 99.0);
        assertEquals(18, session.getCurrentY());
        assertEquals(4, session.getDraft().size());
    }

    @Test
    void undoBeforeCopiedSliceHeightIsChosenDoesNotMutateInvisibleDraft(){
        RegionEditSession session = create();
        addSquare(session, 10, 0.0, 4.0);
        session.saveAndNext(false);

        assertFalse(session.undoLastPoint());
        assertTrue(session.isAwaitingHeight());
        assertEquals(4, session.getDraft().size());
    }

    @Test
    void clearingPendingCopyTurnsItIntoBlankNewSlice(){
        RegionEditSession session = edit(shape(slice(10.0), slice(20.0)), null);
        session.saveAndNext(false);
        session.clearCurrentSlice();

        assertEquals(
                RegionEditSession.AddPointResult.HEIGHT_LOCKED_AND_EXISTING_POLYGON_REPLACED,
                session.addPoint(20, 1.0, 1.0)
        );
        assertEquals(20, session.getCurrentY());
        assertEquals(1, session.getDraft().size());
    }

    @Test
    void sneakingNextStartsBlankAndFirstClickAddsFirstPoint(){
        RegionEditSession session = create();
        addSquare(session, 10, 0.0, 4.0);
        session.saveAndNext(true);

        assertTrue(session.getDraft().isEmpty());
        assertEquals(
                RegionEditSession.AddPointResult.HEIGHT_LOCKED_AND_POINT_ADDED,
                session.addPoint(15, 8.0, 8.0)
        );
        assertEquals(1, session.getDraft().size());
    }

    @Test
    void nextCanLoadExistingHigherSliceSelectedByFirstClick(){
        RegionEditSession session = edit(
                shape(slice(10.0), triangleSlice(20, 3.0)),
                null
        );
        assertEquals(10, session.getCurrentY());

        session.saveAndNext(false);
        assertTrue(session.isAwaitingHeight());
        assertEquals(
                RegionEditSession.AddPointResult.HEIGHT_LOCKED_FOR_EXISTING_POLYGON,
                session.addPoint(20, 123.0, 456.0)
        );
        assertEquals(20, session.getCurrentY());
        assertEquals(3, session.getDraft().size());
        assertEquals(3.0, session.getDraft().get(1).getX());
    }

    @Test
    void nextCanInsertNewSliceBetweenExistingSlices(){
        RegionEditSession session = edit(
                shape(slice(10.0), triangleSlice(20, 3.0)),
                null
        );
        session.saveAndNext(false);
        session.addPoint(15, 0.0, 0.0);

        RegionShapeConfig result = session.finish();
        assertEquals(Arrays.asList(10.0, 15.0, 20.0), Arrays.asList(
                result.getSlices().get(0).getY(),
                result.getSlices().get(1).getY(),
                result.getSlices().get(2).getY()
        ));
    }

    @Test
    void pendingNewSliceRejectsLowerOrEqualHeightAtomically(){
        RegionEditSession session = create();
        addSquare(session, 10, 0.0, 4.0);
        session.saveAndNext(false);

        assertThrows(IllegalArgumentException.class, () -> session.addPoint(10, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> session.addPoint(9, 1.0, 1.0));
        assertTrue(session.isAwaitingHeight());
        assertEquals(4, session.getDraft().size());
    }

    @Test
    void blankNewSliceCanRedrawAnExistingHeight(){
        RegionEditSession session = edit(shape(slice(10.0), slice(20.0)), null);
        session.saveAndNext(true);

        assertEquals(
                RegionEditSession.AddPointResult.HEIGHT_LOCKED_AND_EXISTING_POLYGON_REPLACED,
                session.addPoint(20, 1.0, 1.0)
        );
        assertEquals(20, session.getCurrentY());
        assertEquals(1, session.getDraft().size());
    }

    @Test
    void undoingExistingHeightSelectionRestoresPendingCopyWithoutMutation(){
        RegionEditSession session = edit(
                shape(slice(10.0), triangleSlice(20, 3.0)),
                null
        );
        session.saveAndNext(false);
        session.addPoint(20, 99.0, 99.0);

        assertTrue(session.undoLastPoint());
        assertTrue(session.isAwaitingHeight());
        assertEquals(4, session.getDraft().size());
        assertEquals(3, session.getSavedSlices().get(20).size());
        assertEquals(3.0, session.getSavedSlices().get(20).get(1).getX());
    }

    @Test
    void blankModeSurvivesUndoingItsFirstPoint(){
        RegionEditSession session = edit(shape(slice(10.0), slice(20.0)), null);
        session.saveAndNext(true);
        session.addPoint(15, 1.0, 1.0);

        assertTrue(session.undoLastPoint());
        assertTrue(session.isAwaitingHeight());
        assertEquals(
                RegionEditSession.AddPointResult.HEIGHT_LOCKED_AND_EXISTING_POLYGON_REPLACED,
                session.addPoint(20, 2.0, 2.0)
        );
        assertEquals(1, session.getDraft().size());
    }

    @Test
    void blankModeSurvivesClearingItsFirstPoint(){
        RegionEditSession session = edit(shape(slice(10.0), slice(20.0)), null);
        session.saveAndNext(true);
        session.addPoint(15, 1.0, 1.0);

        session.clearCurrentSlice();
        assertTrue(session.isAwaitingHeight());
        assertEquals(
                RegionEditSession.AddPointResult.HEIGHT_LOCKED_AND_EXISTING_POLYGON_REPLACED,
                session.addPoint(20, 2.0, 2.0)
        );
        assertEquals(1, session.getDraft().size());
    }

    @Test
    void pendingPreviousDiscardsUnsavedSliceAndReturnsToLowerSlice(){
        RegionEditSession session = create();
        addSquare(session, 10, 0.0, 4.0);
        session.saveAndNext(false);

        session.saveAndPrevious();

        assertEquals(10, session.getCurrentY());
        assertEquals(4, session.getDraft().size());
        assertEquals(1, session.getSavedSliceCount());
    }

    @Test
    void finishRejectsPendingHeightInsteadOfSilentlyDroppingIt(){
        RegionEditSession session = create();
        addSquare(session, 10, 0.0, 4.0);
        session.saveAndNext(false);

        assertThrows(IllegalStateException.class, session::finish);
    }

    @Test
    void editExistingSliceKeepsConfiguredHeightOnClicks(){
        RegionEditSession session = edit(shape(slice(70.0)), null);

        session.addPoint(5, 8.0, 8.0);

        assertEquals(70, session.getCurrentY());
        assertEquals(5, session.getDraft().size());
    }

    @Test
    void nullSelectionStartsAtLowestWhileAnOptionalHintCanSelectExisting(){
        RegionShapeConfig original = shape(slice(10.0), slice(20.0));

        assertEquals(10, edit(original, null).getCurrentY());
        assertEquals(20, edit(original, 25).getCurrentY());
    }

    @Test
    void failedNextAtWorldTopDoesNotSaveDraft(){
        RegionEditSession session = edit(shape(slice(100.0)), null);
        session.clearCurrentSlice();
        addTriangle(session, 100);

        assertThrows(IllegalStateException.class, () -> session.saveAndNext(false));
        assertEquals(4, session.getSavedSlices().get(100).size());
        assertEquals(3, session.getDraft().size());
    }

    @Test
    void failedPreviousAtFirstSliceDoesNotSaveDraft(){
        RegionEditSession session = edit(shape(slice(0.0)), null);
        session.clearCurrentSlice();
        addTriangle(session, 0);

        assertThrows(IllegalStateException.class, session::saveAndPrevious);
        assertEquals(4, session.getSavedSlices().get(0).size());
        assertEquals(3, session.getDraft().size());
    }

    @Test
    void supportsAddUndoAndClear(){
        RegionEditSession session = create();
        session.addPoint(10, 0.5, 0.5);
        session.addPoint(20, 4.5, 0.5);
        assertEquals(2, session.getDraft().size());
        assertTrue(session.undoLastPoint());
        assertEquals(1, session.getDraft().size());
        session.clearCurrentSlice();
        assertTrue(session.getDraft().isEmpty());
        assertTrue(session.isAwaitingHeight());
        assertFalse(session.undoLastPoint());
    }

    @Test
    void rejectsTooFewPointsAndZeroArea(){
        RegionEditSession session = create();
        session.addPoint(10, 0.0, 0.0);
        session.addPoint(99, 1.0, 0.0);
        assertNotNull(session.currentValidationError());
        assertThrows(IllegalStateException.class, session::saveCurrentSlice);
        session.addPoint(99, 2.0, 0.0);
        assertTrue(session.currentValidationError().contains("面积"));
    }

    @Test
    void rejectsSelfIntersectingPolygon(){
        RegionEditSession session = create();
        session.addPoint(10, 0.0, 0.0);
        session.addPoint(10, 4.0, 4.0);
        session.addPoint(10, 0.0, 4.0);
        session.addPoint(10, 4.0, 0.0);
        session.addPoint(10, 5.0, 2.0);
        assertNotNull(session.currentValidationError());
        assertThrows(IllegalStateException.class, session::saveCurrentSlice);
    }

    @Test
    void cancelRequiresSecondUseWithinFiveSeconds(){
        RegionEditSession session = create();
        assertFalse(session.confirmCancel(1_000L));
        assertTrue(session.confirmCancel(5_999L));
        assertFalse(session.confirmCancel(6_000L));
        assertFalse(session.confirmCancel(11_001L));
    }

    @Test
    void initialShapeIsCopiedSoCancelCannotMutateOriginal(){
        RegionShapeConfig original = shape(slice(10.0));
        RegionEditSession session = edit(original, null);
        session.clearCurrentSlice();
        assertEquals(4, original.getSlices().get(0).getPolygon().size());
        assertNotNull(session.currentValidationError());
    }

    @Test
    void editRejectsNonIntegerDuplicateAndOutOfRangeSlices(){
        assertThrows(IllegalArgumentException.class, () -> edit(shape(slice(10.5)), null));
        assertThrows(IllegalArgumentException.class, () -> edit(
                shape(slice(10.0), slice(10.0)), null
        ));
        assertThrows(IllegalArgumentException.class, () -> edit(shape(slice(101.0)), null));
    }

    @Test
    void enforcesClickedBlockWorldHeight(){
        RegionEditSession session = create();
        assertThrows(IllegalArgumentException.class, () -> session.addPoint(-1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> session.addPoint(101, 0.0, 0.0));
        assertTrue(session.isAwaitingHeight());
    }

    @Test
    void refusesPointsBeyondPerSliceLimit(){
        RegionEditSession session = create();
        for(int index = 0; index < SlicedPolygonVolume.MAX_VERTICES_PER_SLICE; index++){
            session.addPoint(10, index, 0.0);
        }
        assertThrows(IllegalStateException.class, () -> session.addPoint(10, 999.0, 0.0));
        assertEquals(SlicedPolygonVolume.MAX_VERTICES_PER_SLICE, session.getDraft().size());
    }

    @Test
    void refusesCreatingMoreThanTheMaximumSlices(){
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
        session.saveAndNext(true);
        assertThrows(IllegalStateException.class, () -> session.addPoint(
                SlicedPolygonVolume.MAX_SLICE_COUNT,
                0.0,
                0.0
        ));
    }

    @Test
    void refusesSavingBeyondTotalVertexLimit(){
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
        session.saveAndNext(true);
        addTriangle(session, sliceCount);
        assertThrows(IllegalStateException.class, session::saveCurrentSlice);
    }

    private RegionEditSession create(){
        return new RegionEditSession(
                RegionEditSession.Mode.CREATE,
                "world",
                "roi",
                0,
                100,
                null,
                null
        );
    }

    private RegionEditSession edit(RegionShapeConfig initial, Integer selectionY){
        return new RegionEditSession(
                RegionEditSession.Mode.EDIT,
                "world",
                "roi",
                0,
                100,
                selectionY,
                initial
        );
    }

    private void addSquare(RegionEditSession session, int y, double min, double max){
        session.addPoint(y, min, min);
        session.addPoint(y, max, min);
        session.addPoint(y, max, max);
        session.addPoint(y, min, max);
    }

    private void addTriangle(RegionEditSession session, int y){
        session.addPoint(y, 0.0, 0.0);
        session.addPoint(y, 3.0, 0.0);
        session.addPoint(y, 0.0, 3.0);
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

    private RegionShapeConfig.Point point(double x, double z){
        return RegionShapeConfig.Point.builder().x(x).z(z).build();
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
