package io.github.rookiecuzz.rookieregions.module.music;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable result for all configured playback channels. */
public final class MusicResolution {
    private final Map<String, ResolvedMusicChannel> channels;

    MusicResolution(Map<String, ResolvedMusicChannel> channels) {
        this.channels = Collections.unmodifiableMap(new LinkedHashMap<>(channels));
    }

    public Map<String, ResolvedMusicChannel> getChannels() {
        return channels;
    }

    public ResolvedMusicChannel getChannel(String channel){
        return channel == null ? null : channels.get(channel.trim());
    }
}
