package io.github.rookiecuzz.rookieregions.core;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Case-insensitive, world-scoped region identity. */
public record RegionKey(WorldId world, String id) implements Comparable<RegionKey> {
    public static final String GLOBAL_ID = "__global__";
    private static final Pattern ID = Pattern.compile("[a-z0-9._-]+");

    public RegionKey {
        Objects.requireNonNull(world, "region world cannot be null");
        id = normalizeId(id);
    }

    public static String normalizeId(String value) {
        if(value == null){
            throw new IllegalArgumentException("region ID cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if(!ID.matcher(normalized).matches()){
            throw new IllegalArgumentException("invalid region ID: " + value);
        }
        return normalized;
    }

    public static RegionKey global(WorldId world) {
        return new RegionKey(world, GLOBAL_ID);
    }

    public boolean isGlobal() {
        return GLOBAL_ID.equals(id);
    }

    @Override
    public int compareTo(RegionKey other) {
        int worldOrder = world.compareTo(other.world);
        return worldOrder != 0 ? worldOrder : id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return world.namespacedKey() + "/" + id;
    }
}
