package com.gitee.niocho.areamusic.spatial;

import com.gitee.niocho.areamusic.config.AreaDto;
import com.gitee.niocho.areamusic.geometry.SlicedPolygonVolume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RegionSpatialIndex {
    private static final long MAX_CHUNKS_PER_REGION = 4096L;

    private final Map<String, WorldIndex> worlds;

    private RegionSpatialIndex(Map<String, WorldIndex> worlds) {
        this.worlds = worlds;
    }

    public static RegionSpatialIndex empty(){
        return new RegionSpatialIndex(new HashMap<>());
    }

    public static RegionSpatialIndex build(Map<String, Map<String, AreaDto>> areas){
        Map<String, WorldIndex> result = new HashMap<>();
        if(areas == null){
            return new RegionSpatialIndex(result);
        }

        for(Map.Entry<String, Map<String, AreaDto>> worldEntry : areas.entrySet()){
            WorldIndex worldIndex = new WorldIndex();
            if(worldEntry.getValue() != null){
                for(AreaDto area : worldEntry.getValue().values()){
                    if(area != null){
                        worldIndex.add(area);
                    }
                }
            }
            result.put(worldEntry.getKey(), worldIndex);
        }
        return new RegionSpatialIndex(result);
    }

    public List<AreaDto> getCandidates(String worldName, double x, double z){
        WorldIndex worldIndex = worlds.get(worldName);
        if(worldIndex == null){
            return Collections.emptyList();
        }
        return worldIndex.get(chunkCoordinate(x), chunkCoordinate(z));
    }

    public int getIndexedChunkCount(String worldName){
        WorldIndex worldIndex = worlds.get(worldName);
        return worldIndex == null ? 0 : worldIndex.byChunk.size();
    }

    private static int chunkCoordinate(double coordinate){
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static long chunkKey(int chunkX, int chunkZ){
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static Bounds boundsOf(AreaDto area){
        SlicedPolygonVolume shape = area.getShape();
        if(shape != null){
            return new Bounds(shape.getMinX(), shape.getMaxX(), shape.getMinZ(), shape.getMaxZ());
        }
        if(area.getMinPoint() == null || area.getMaxPoint() == null){
            return null;
        }
        return new Bounds(
                Math.min(area.getMinPoint().getX(), area.getMaxPoint().getX()),
                Math.max(area.getMinPoint().getX(), area.getMaxPoint().getX()) + 1.0,
                Math.min(area.getMinPoint().getZ(), area.getMaxPoint().getZ()),
                Math.max(area.getMinPoint().getZ(), area.getMaxPoint().getZ()) + 1.0
        );
    }

    private static final class WorldIndex {
        private final Map<Long, List<AreaDto>> byChunk = new HashMap<>();
        private final List<AreaDto> largeRegions = new ArrayList<>();

        private void add(AreaDto area){
            Bounds bounds = boundsOf(area);
            if(bounds == null){
                return;
            }

            int minChunkX = chunkCoordinate(bounds.minX);
            int maxChunkX = chunkCoordinate(bounds.maxX);
            int minChunkZ = chunkCoordinate(bounds.minZ);
            int maxChunkZ = chunkCoordinate(bounds.maxZ);
            long width = (long) maxChunkX - minChunkX + 1L;
            long depth = (long) maxChunkZ - minChunkZ + 1L;
            if(width <= 0
                    || depth <= 0
                    || width > MAX_CHUNKS_PER_REGION
                    || depth > MAX_CHUNKS_PER_REGION
                    || width > MAX_CHUNKS_PER_REGION / depth){
                largeRegions.add(area);
                return;
            }

            for(int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++){
                for(int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++){
                    byChunk.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>())
                            .add(area);
                }
            }
        }

        private List<AreaDto> get(int chunkX, int chunkZ){
            List<AreaDto> local = byChunk.get(chunkKey(chunkX, chunkZ));
            if((local == null || local.isEmpty()) && largeRegions.isEmpty()){
                return Collections.emptyList();
            }

            int localSize = local == null ? 0 : local.size();
            List<AreaDto> result = new ArrayList<>(localSize + largeRegions.size());
            if(local != null){
                result.addAll(local);
            }
            result.addAll(largeRegions);
            return result;
        }
    }

    private static final class Bounds {
        private final double minX;
        private final double maxX;
        private final double minZ;
        private final double maxZ;

        private Bounds(double minX, double maxX, double minZ, double maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}
