package com.gitee.niocho.areamusic.utils;

import com.gitee.niocho.areamusic.config.AreaDto;
import com.gitee.niocho.areamusic.config.Priority;
import com.gitee.niocho.areamusic.config.RegionShapeConfig;
import com.gitee.niocho.areamusic.geometry.SlicedPolygonVolume;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicUtilTest {
    private final MusicUtil musicUtil = new MusicUtil(null);

    @Test
    void regularLookupIncludesNonLoopingAreasButExcludesDisabledAreas(){
        AreaDto nonLooping = area("non-looping", true, false, Priority.NORMAL);
        AreaDto looping = area("looping", true, true, Priority.HIGH);
        AreaDto disabled = area("disabled", false, true, Priority.HIGHEST);

        List<AreaDto> result = musicUtil.filterInsideAreas(
                Arrays.asList(nonLooping, looping, disabled),
                5,
                5,
                5,
                false
        );

        assertEquals(Arrays.asList(nonLooping, looping), result);
    }

    @Test
    void optionalLoopFilterIncludesOnlyEnabledLoopingAreas(){
        AreaDto nonLooping = area("non-looping", true, false, Priority.NORMAL);
        AreaDto looping = area("looping", true, true, Priority.HIGH);
        AreaDto disabled = area("disabled", false, true, Priority.HIGHEST);

        List<AreaDto> result = musicUtil.filterInsideAreas(
                Arrays.asList(nonLooping, looping, disabled),
                5,
                5,
                5,
                true
        );

        assertEquals(1, result.size());
        assertSame(looping, result.get(0));
    }

    @Test
    void playerMembershipUsesSlicedPolygonInsteadOfBoundingBox(){
        SlicedPolygonVolume shape = new SlicedPolygonVolume(
                RegionShapeConfig.builder()
                        .slices(Arrays.asList(RegionShapeConfig.Slice.builder()
                                .y(10.0)
                                .polygon(Arrays.asList(
                                        RegionShapeConfig.Point.builder().x(0.0).z(0.0).build(),
                                        RegionShapeConfig.Point.builder().x(4.0).z(0.0).build(),
                                        RegionShapeConfig.Point.builder().x(0.0).z(4.0).build()
                                ))
                                .build()))
                        .build()
        );
        AreaDto area = area("slice", true, true, Priority.NORMAL);
        area.setShape(shape);

        assertTrue(musicUtil.isInside(area, 1, 10, 1));
        assertFalse(musicUtil.isInside(area, 3.5, 10, 3.5));
    }

    private AreaDto area(String id,
                         boolean enabled,
                         boolean loop,
                         Priority priority){
        return AreaDto.builder()
                .uuid(id)
                .areaId(id)
                .enabled(enabled)
                .loop(loop)
                .priority(priority)
                .order(0)
                .shape(new SlicedPolygonVolume(RegionShapeConfig.builder()
                        .slices(Arrays.asList(RegionShapeConfig.Slice.builder()
                                .y(5.0)
                                .polygon(Arrays.asList(
                                        RegionShapeConfig.Point.builder().x(0.0).z(0.0).build(),
                                        RegionShapeConfig.Point.builder().x(10.0).z(0.0).build(),
                                        RegionShapeConfig.Point.builder().x(10.0).z(10.0).build(),
                                        RegionShapeConfig.Point.builder().x(0.0).z(10.0).build()
                                ))
                                .build()))
                        .build()))
                .build();
    }
}
