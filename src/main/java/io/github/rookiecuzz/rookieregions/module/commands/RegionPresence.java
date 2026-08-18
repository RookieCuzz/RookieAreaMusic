package io.github.rookiecuzz.rookieregions.module.commands;

/** Stable-string representation of one physically occupied region. */
public final class RegionPresence {
    private final String regionKey;
    private final int depth;

    public RegionPresence(String regionKey, int depth) {
        if(regionKey == null || regionKey.trim().isEmpty()){
            throw new IllegalArgumentException("region key must not be blank");
        }
        if(depth < 0){
            throw new IllegalArgumentException("region depth must not be negative");
        }
        this.regionKey = regionKey.trim();
        this.depth = depth;
    }

    public String getRegionKey() {
        return regionKey;
    }

    public int getDepth() {
        return depth;
    }
}
