package io.github.rookiecuzz.rookieareamusic.geometry;

import io.github.rookiecuzz.rookieareamusic.config.RegionShapeConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlicedPolygonVolumeTest {
    @Test
    void copiesEachPolygonUpToTheNextSlice(){
        SlicedPolygonVolume volume = new SlicedPolygonVolume(RegionShapeConfig.builder()
                .slices(Arrays.asList(
                        slice(20.0, point(0, 0), point(2, 0), point(2, 2), point(0, 2)),
                        slice(40.0, point(0, 0), point(6, 0), point(6, 6), point(0, 6)),
                        slice(70.0, point(0, 0), point(2, 0), point(0, 2))
                ))
                .build());

        assertTrue(volume.contains(1, 20, 1));
        assertTrue(volume.contains(1, 39.999, 1));
        assertFalse(volume.contains(4, 39.999, 4));

        assertTrue(volume.contains(4, 40, 4));
        assertTrue(volume.contains(4, 69.999, 4));

        assertTrue(volume.contains(0.5, 70, 0.5));
        assertTrue(volume.contains(0.5, 70.999, 0.5));
        assertFalse(volume.contains(4, 70, 4));
        assertFalse(volume.contains(0.5, 71, 0.5));
    }

    @Test
    void acceptsDifferentVertexCountsAndUnsortedSlices(){
        SlicedPolygonVolume volume = new SlicedPolygonVolume(RegionShapeConfig.builder()
                .slices(Arrays.asList(
                        slice(30.0, point(0, 0), point(5, 0), point(2.5, 5)),
                        slice(10.0, point(0, 0), point(4, 0), point(4, 4), point(0, 4))
                ))
                .build());

        assertTrue(volume.contains(3, 15, 3));
        assertFalse(volume.contains(4, 30, 4));
    }

    @Test
    void polygonBoundaryCountsAsInside(){
        SlicedPolygonVolume volume = new SlicedPolygonVolume(RegionShapeConfig.builder()
                .slices(Arrays.asList(
                        slice(10.0, point(0, 0), point(4, 0), point(4, 4), point(0, 4))
                ))
                .build());

        assertTrue(volume.contains(0, 10, 2));
        assertTrue(volume.contains(4, 10, 4));
    }

    @Test
    void rejectsSelfIntersectingPolygons(){
        RegionShapeConfig invalid = RegionShapeConfig.builder()
                .slices(Arrays.asList(
                        slice(10.0, point(0, 0), point(4, 4), point(0, 4), point(4, 0))
                ))
                .build();

        assertThrows(IllegalArgumentException.class, () -> new SlicedPolygonVolume(invalid));
    }

    @Test
    void rejectsNonIntegerAndOutOfRangeSliceHeights(){
        assertThrows(IllegalArgumentException.class, () -> new SlicedPolygonVolume(
                RegionShapeConfig.builder()
                        .slices(Arrays.asList(slice(
                                10.5,
                                point(0, 0), point(2, 0), point(0, 2)
                        )))
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SlicedPolygonVolume(
                RegionShapeConfig.builder()
                        .slices(Arrays.asList(slice(
                                (double) Integer.MAX_VALUE + 1.0,
                                point(0, 0), point(2, 0), point(0, 2)
                        )))
                        .build()
        ));
    }

    @Test
    void rejectsTooManySlices(){
        List<RegionShapeConfig.Slice> slices = new ArrayList<>();
        for(int index = 0; index <= SlicedPolygonVolume.MAX_SLICE_COUNT; index++){
            slices.add(slice(
                    index,
                    point(0, 0), point(2, 0), point(0, 2)
            ));
        }

        assertThrows(IllegalArgumentException.class, () -> new SlicedPolygonVolume(
                RegionShapeConfig.builder().slices(slices).build()
        ));
    }

    @Test
    void rejectsTooManyVerticesInOneSlice(){
        List<RegionShapeConfig.Point> points = repeatedPoints(
                SlicedPolygonVolume.MAX_VERTICES_PER_SLICE + 1
        );

        assertThrows(IllegalArgumentException.class, () -> new SlicedPolygonVolume(
                RegionShapeConfig.builder()
                        .slices(Arrays.asList(RegionShapeConfig.Slice.builder()
                                .y(10.0)
                                .polygon(points)
                                .build()))
                        .build()
        ));
    }

    @Test
    void rejectsTooManyVerticesAcrossSlices(){
        int verticesPerSlice = SlicedPolygonVolume.MAX_VERTICES_PER_SLICE;
        int sliceCount = SlicedPolygonVolume.MAX_TOTAL_VERTICES / verticesPerSlice + 1;
        List<RegionShapeConfig.Slice> slices = new ArrayList<>();
        for(int index = 0; index < sliceCount; index++){
            slices.add(RegionShapeConfig.Slice.builder()
                    .y((double) index)
                    .polygon(repeatedPoints(verticesPerSlice))
                    .build());
        }

        assertThrows(IllegalArgumentException.class, () -> new SlicedPolygonVolume(
                RegionShapeConfig.builder().slices(slices).build()
        ));
    }

    @Test
    void selectsTheCorrectSliceAcrossManyBinarySearchLevels(){
        List<RegionShapeConfig.Slice> slices = new ArrayList<>();
        for(int index = 0; index < 128; index++){
            double size = index + 1.0;
            slices.add(slice(
                    index * 2.0,
                    point(0, 0),
                    point(size, 0),
                    point(size, size),
                    point(0, size)
            ));
        }
        SlicedPolygonVolume volume = new SlicedPolygonVolume(
                RegionShapeConfig.builder().slices(slices).build()
        );

        assertFalse(volume.contains(64.5, 126.999, 64.5));
        assertTrue(volume.contains(64.5, 128.0, 64.5));
        assertTrue(volume.contains(127.5, 254.999, 127.5));
        assertFalse(volume.contains(127.5, 255.0, 127.5));
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

    private List<RegionShapeConfig.Point> repeatedPoints(int count){
        List<RegionShapeConfig.Point> result = new ArrayList<>();
        for(int index = 0; index < count; index++){
            result.add(point(index, 0));
        }
        return result;
    }
}
