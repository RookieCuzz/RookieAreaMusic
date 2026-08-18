package io.github.rookiecuzz.rookieregions.module.music;

import java.util.List;

/** One selected region playlist with all playback metadata preserved. */
public final class ResolvedMusicLayer {
    private final String regionKey;
    private final String channel;
    private final int depth;
    private final RegionMusicChannel source;

    ResolvedMusicLayer(String regionKey,
                       String channel,
                       int depth,
                       RegionMusicChannel source) {
        this.regionKey = regionKey;
        this.channel = channel;
        this.depth = depth;
        this.source = source;
    }

    public String getRegionKey() {
        return regionKey;
    }

    public String getChannel() {
        return channel;
    }

    public int getDepth() {
        return depth;
    }

    public int getOrder() {
        return source.getOrder();
    }

    public boolean isRandom() {
        return source.isRandom();
    }

    public boolean isLoop() {
        return source.isLoop();
    }

    public float getVolume() {
        return source.getVolume();
    }

    public float getPitch() {
        return source.getPitch();
    }

    public boolean isOverwrite() {
        return source.isOverwrite();
    }

    public List<MusicTrack> getTracks() {
        return source.getTracks();
    }
}
