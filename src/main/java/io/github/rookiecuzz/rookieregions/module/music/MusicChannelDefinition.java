package io.github.rookiecuzz.rookieregions.module.music;

import java.util.Objects;

/** Immutable runtime selection settings for one named playback channel. */
public final class MusicChannelDefinition {
    private final String name;
    private final ChannelPlaybackMode playbackMode;
    private final int maxLayers;

    public MusicChannelDefinition(String name,
                                  ChannelPlaybackMode playbackMode,
                                  int maxLayers) {
        this.name = RegionMusicProfile.requireKey(name, "channel name");
        if(playbackMode == null){
            throw new IllegalArgumentException("channel playback mode must not be null");
        }
        if(maxLayers <= 0){
            throw new IllegalArgumentException("channel maxLayers must be positive");
        }
        if(playbackMode == ChannelPlaybackMode.EXCLUSIVE && maxLayers != 1){
            throw new IllegalArgumentException(
                    "exclusive channels must use maxLayers=1"
            );
        }
        this.playbackMode = playbackMode;
        this.maxLayers = maxLayers;
    }

    public static MusicChannelDefinition exclusive(String name){
        return new MusicChannelDefinition(name, ChannelPlaybackMode.EXCLUSIVE, 1);
    }

    public static MusicChannelDefinition layered(String name, int maxLayers){
        return new MusicChannelDefinition(name, ChannelPlaybackMode.LAYERED, maxLayers);
    }

    public String getName() {
        return name;
    }

    public ChannelPlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public int getMaxLayers() {
        return maxLayers;
    }

    @Override
    public boolean equals(Object value) {
        if(this == value){
            return true;
        }
        if(!(value instanceof MusicChannelDefinition)){
            return false;
        }
        MusicChannelDefinition other = (MusicChannelDefinition) value;
        return maxLayers == other.maxLayers
                && name.equals(other.name)
                && playbackMode == other.playbackMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, playbackMode, maxLayers);
    }
}
