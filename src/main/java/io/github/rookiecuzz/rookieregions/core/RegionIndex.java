package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.core.shape.Bounds3D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable chunk-bucket broad-phase index; globals are handled separately. */
public final class RegionIndex {
    private static final long MAX_CHUNKS_PER_REGION = 4096L;

    private final Map<WorldId, WorldIndex> worlds;

    private RegionIndex(Map<WorldId, WorldIndex> worlds) {
        this.worlds = Map.copyOf(worlds);
    }

    public static RegionIndex empty() {
        return new RegionIndex(Map.of());
    }

    public static RegionIndex build(Collection<Region> regions) {
        if(regions == null || regions.isEmpty()){
            return empty();
        }
        HashMap<WorldId, MutableWorldIndex> mutable = new HashMap<>();
        for(Region region : regions){
            if(region == null || region.key().isGlobal()){
                continue;
            }
            mutable.computeIfAbsent(
                    region.key().world(),
                    ignored -> new MutableWorldIndex()
            ).add(region);
        }
        HashMap<WorldId, WorldIndex> frozen = new HashMap<>();
        mutable.forEach((world, index) -> frozen.put(world, index.freeze()));
        return new RegionIndex(frozen);
    }

    public List<Region> pointCandidates(WorldId world, double x, double z) {
        WorldIndex index = worlds.get(world);
        return index == null
                ? List.of()
                : index.at(chunkCoordinate(x), chunkCoordinate(z));
    }

    public List<Region> boundsCandidates(WorldId world, Bounds3D bounds) {
        WorldIndex index = worlds.get(world);
        return index == null || bounds == null ? List.of() : index.within(bounds);
    }

    public int indexedChunkCount(WorldId world) {
        WorldIndex index = worlds.get(world);
        return index == null ? 0 : index.byChunk().size();
    }

    private static int chunkCoordinate(double coordinate) {
        if(coordinate <= Integer.MIN_VALUE){
            return Integer.MIN_VALUE >> 4;
        }
        if(coordinate >= Integer.MAX_VALUE){
            return Integer.MAX_VALUE >> 4;
        }
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private record WorldIndex(Map<Long, List<Region>> byChunk,
                              List<Region> largeRegions) {
        private List<Region> at(int x, int z) {
            List<Region> local = byChunk.get(chunkKey(x, z));
            if((local == null || local.isEmpty()) && largeRegions.isEmpty()){
                return List.of();
            }
            LinkedHashSet<Region> result = new LinkedHashSet<>();
            if(local != null){
                result.addAll(local);
            }
            result.addAll(largeRegions);
            return List.copyOf(result);
        }

        private List<Region> within(Bounds3D bounds) {
            if(!bounds.isFinite()){
                return all();
            }
            int minX = chunkCoordinate(bounds.minX());
            int maxX = chunkCoordinate(bounds.maxX());
            int minZ = chunkCoordinate(bounds.minZ());
            int maxZ = chunkCoordinate(bounds.maxZ());
            if(tooLarge(minX, maxX, minZ, maxZ)){
                return all();
            }
            LinkedHashSet<Region> result = new LinkedHashSet<>();
            for(int x = minX; x <= maxX; x++){
                for(int z = minZ; z <= maxZ; z++){
                    List<Region> bucket = byChunk.get(chunkKey(x, z));
                    if(bucket != null){
                        result.addAll(bucket);
                    }
                }
            }
            result.addAll(largeRegions);
            return List.copyOf(result);
        }

        private List<Region> all() {
            LinkedHashSet<Region> result = new LinkedHashSet<>();
            byChunk.values().forEach(result::addAll);
            result.addAll(largeRegions);
            return List.copyOf(result);
        }
    }

    private static final class MutableWorldIndex {
        private final Map<Long, List<Region>> byChunk = new HashMap<>();
        private final List<Region> large = new ArrayList<>();

        private void add(Region region) {
            Bounds3D bounds = region.shape().bounds();
            int minX = chunkCoordinate(bounds.minX());
            int maxX = chunkCoordinate(bounds.maxX());
            int minZ = chunkCoordinate(bounds.minZ());
            int maxZ = chunkCoordinate(bounds.maxZ());
            if(!bounds.isFinite() || tooLarge(minX, maxX, minZ, maxZ)){
                large.add(region);
                return;
            }
            for(int x = minX; x <= maxX; x++){
                for(int z = minZ; z <= maxZ; z++){
                    byChunk.computeIfAbsent(chunkKey(x, z), ignored -> new ArrayList<>())
                            .add(region);
                }
            }
        }

        private WorldIndex freeze() {
            HashMap<Long, List<Region>> buckets = new HashMap<>();
            byChunk.forEach((key, value) -> {
                ArrayList<Region> sorted = new ArrayList<>(value);
                sorted.sort((first, second) -> first.key().compareTo(second.key()));
                buckets.put(key, List.copyOf(sorted));
            });
            ArrayList<Region> sortedLarge = new ArrayList<>(large);
            sortedLarge.sort((first, second) -> first.key().compareTo(second.key()));
            return new WorldIndex(Collections.unmodifiableMap(buckets), List.copyOf(sortedLarge));
        }
    }

    private static boolean tooLarge(int minX, int maxX, int minZ, int maxZ) {
        long width = (long) maxX - minX + 1L;
        long depth = (long) maxZ - minZ + 1L;
        return width <= 0L
                || depth <= 0L
                || width > MAX_CHUNKS_PER_REGION
                || depth > MAX_CHUNKS_PER_REGION
                || width > MAX_CHUNKS_PER_REGION / depth;
    }
}
