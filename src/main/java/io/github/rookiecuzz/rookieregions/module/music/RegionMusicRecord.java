package io.github.rookiecuzz.rookieregions.module.music;

/**
 * Stable-string adapter used until the core RegionKey/RegionRecord API is
 * wired directly. The resolver expects the complete applicable ancestry.
 */
public final class RegionMusicRecord {
    private final String regionKey;
    private final String parentKey;
    private final RegionMusicProfile profile;

    public RegionMusicRecord(String regionKey,
                             String parentKey,
                             RegionMusicProfile profile) {
        this.regionKey = RegionMusicProfile.requireKey(regionKey, "region key");
        this.parentKey = parentKey == null
                ? null
                : RegionMusicProfile.requireKey(parentKey, "parent region key");
        if(this.regionKey.equals(this.parentKey)){
            throw new IllegalArgumentException("a music record cannot parent itself");
        }
        this.profile = profile == null ? RegionMusicProfile.empty() : profile;
    }

    public String getRegionKey() {
        return regionKey;
    }

    public String getParentKey() {
        return parentKey;
    }

    public RegionMusicProfile getProfile() {
        return profile;
    }
}
