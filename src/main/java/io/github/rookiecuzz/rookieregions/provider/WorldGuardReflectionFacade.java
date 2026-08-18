package io.github.rookiecuzz.rookieregions.provider;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Dependency-neutral capture seam around the reflective WorldGuard calls.
 *
 * <p>Tests and alternative loaders can inject a facade without loading Bukkit,
 * WorldGuard, or WorldEdit implementation classes.</p>
 */
@FunctionalInterface
public interface WorldGuardReflectionFacade {
    Capture capture() throws Exception;

    record Capture(List<WorldView> worlds) {
        public Capture {
            worlds = immutableList(worlds, "captured worlds");
        }
    }

    record WorldView(UUID uuid,
                     String namespacedKey,
                     List<RegionView> regions) {
        public WorldView {
            Objects.requireNonNull(uuid, "captured world UUID cannot be null");
            Objects.requireNonNull(
                    namespacedKey,
                    "captured world namespaced key cannot be null"
            );
            regions = immutableList(regions, "captured regions");
        }
    }

    record RegionView(String id,
                      int priority,
                      String parentId,
                      Set<UUID> ownerPlayers,
                      Set<String> ownerGroups,
                      Set<UUID> memberPlayers,
                      Set<String> memberGroups,
                      ShapeView shape) {
        public RegionView {
            Objects.requireNonNull(id, "captured region ID cannot be null");
            ownerPlayers = immutableSet(ownerPlayers, "captured owner players");
            ownerGroups = immutableSet(ownerGroups, "captured owner groups");
            memberPlayers = immutableSet(memberPlayers, "captured member players");
            memberGroups = immutableSet(memberGroups, "captured member groups");
            Objects.requireNonNull(shape, "captured region shape cannot be null");
        }
    }

    sealed interface ShapeView permits GlobalView, CuboidView, PolygonalView {
    }

    record GlobalView() implements ShapeView {
        public static final GlobalView INSTANCE = new GlobalView();
    }

    /** WorldGuard block coordinates, including both minimum and maximum. */
    record CuboidView(int minX,
                      int minY,
                      int minZ,
                      int maxX,
                      int maxY,
                      int maxZ) implements ShapeView {
        public CuboidView {
            if(minX > maxX || minY > maxY || minZ > maxZ){
                throw new IllegalArgumentException(
                        "captured cuboid minimum cannot exceed maximum"
                );
            }
        }
    }

    /** WorldGuard X/Z vertices and inclusive vertical block range. */
    record PolygonalView(int minY,
                         int maxY,
                         List<PointView> points) implements ShapeView {
        public PolygonalView {
            if(minY > maxY){
                throw new IllegalArgumentException(
                        "captured polygon minimum Y cannot exceed maximum Y"
                );
            }
            points = immutableList(points, "captured polygon points");
        }
    }

    record PointView(int x, int z) {
    }

    private static <T> List<T> immutableList(List<T> source, String name) {
        if(source == null){
            throw new IllegalArgumentException(name + " cannot be null");
        }
        try {
            return List.copyOf(source);
        } catch(NullPointerException exception){
            throw new IllegalArgumentException(name + " cannot contain null", exception);
        }
    }

    private static <T> Set<T> immutableSet(Set<T> source, String name) {
        if(source == null){
            throw new IllegalArgumentException(name + " cannot be null");
        }
        try {
            return Set.copyOf(source);
        } catch(NullPointerException exception){
            throw new IllegalArgumentException(name + " cannot contain null", exception);
        }
    }
}
