package com.gitee.niocho.areamusic.spatial;

import com.gitee.niocho.areamusic.config.AreaDto;
import com.gitee.niocho.areamusic.config.RegionShapeConfig;
import com.gitee.niocho.areamusic.geometry.SlicedPolygonVolume;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSpatialIndexTest {
    @Test
    void returnsOnlyAreasIndexedForThePlayersChunk(){
        AreaDto origin = area("origin", "world", 0, 0, 15, 15);
        AreaDto distant = area("distant", "world", 160, 160, 175, 175);

        RegionSpatialIndex index = RegionSpatialIndex.build(areas(origin, distant));

        List<AreaDto> originCandidates = index.getCandidates("world", 5, 5);
        assertEquals(1, originCandidates.size());
        assertSame(origin, originCandidates.get(0));
        assertFalse(originCandidates.contains(distant));

        List<AreaDto> distantCandidates = index.getCandidates("world", 165, 165);
        assertEquals(1, distantCandidates.size());
        assertSame(distant, distantCandidates.get(0));
    }

    @Test
    void indexesNegativeCoordinatesAndChunkBoundaries(){
        AreaDto negative = area("negative", "world", -20, -20, -1, -1);
        AreaDto boundary = area("boundary", "world", 15, 0, 16, 1);

        RegionSpatialIndex index = RegionSpatialIndex.build(areas(negative, boundary));

        assertTrue(index.getCandidates("world", -17, -17).contains(negative));
        assertTrue(index.getCandidates("world", -1, -1).contains(negative));
        assertTrue(index.getCandidates("world", 15.5, 0.5).contains(boundary));
        assertTrue(index.getCandidates("world", 16, 0.5).contains(boundary));
    }

    @Test
    void keepsVeryLargeAreasAsWorldLevelCandidates(){
        AreaDto huge = area("huge", "world", 0, 0, 1048576, 1048576);

        RegionSpatialIndex index = RegionSpatialIndex.build(areas(huge));

        assertEquals(0, index.getIndexedChunkCount("world"));
        assertTrue(index.getCandidates("world", 8, 8).contains(huge));
        assertTrue(index.getCandidates("world", 500000, 500000).contains(huge));
    }

    @Test
    void separatesWorldIndexes(){
        AreaDto overworld = area("overworld", "world", 0, 0, 15, 15);
        AreaDto nether = area("nether", "world_nether", 0, 0, 15, 15);
        Map<String, Map<String, AreaDto>> source = new LinkedHashMap<>();
        source.put("world", singletonAreaMap(overworld));
        source.put("world_nether", singletonAreaMap(nether));

        RegionSpatialIndex index = RegionSpatialIndex.build(source);

        assertEquals(Arrays.asList(overworld), index.getCandidates("world", 1, 1));
        assertEquals(Arrays.asList(nether), index.getCandidates("world_nether", 1, 1));
        assertTrue(index.getCandidates("missing", 1, 1).isEmpty());
    }

    private Map<String, Map<String, AreaDto>> areas(AreaDto... values){
        Map<String, AreaDto> worldAreas = new LinkedHashMap<>();
        for(AreaDto area : values){
            worldAreas.put(area.getUuid(), area);
        }
        Map<String, Map<String, AreaDto>> result = new LinkedHashMap<>();
        result.put("world", worldAreas);
        return result;
    }

    private Map<String, AreaDto> singletonAreaMap(AreaDto area){
        Map<String, AreaDto> result = new LinkedHashMap<>();
        result.put(area.getUuid(), area);
        return result;
    }

    private AreaDto area(String uuid,
                         String world,
                         double minX,
                         double minZ,
                         double maxX,
                         double maxZ){
        SlicedPolygonVolume shape = new SlicedPolygonVolume(RegionShapeConfig.builder()
                .slices(Arrays.asList(RegionShapeConfig.Slice.builder()
                        .y(0.0)
                        .polygon(Arrays.asList(
                                point(minX, minZ),
                                point(maxX, minZ),
                                point(maxX, maxZ),
                                point(minX, maxZ)
                        ))
                        .build()))
                .build());
        return AreaDto.builder()
                .uuid(uuid)
                .world(world)
                .shape(shape)
                .minPoint(shape.getMinPoint())
                .maxPoint(shape.getMaxPoint())
                .build();
    }

    private RegionShapeConfig.Point point(double x, double z){
        return RegionShapeConfig.Point.builder().x(x).z(z).build();
    }
}
