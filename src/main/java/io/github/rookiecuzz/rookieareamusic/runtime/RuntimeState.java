package io.github.rookiecuzz.rookieareamusic.runtime;

import io.github.rookiecuzz.rookieareamusic.config.MusicDto;
import io.github.rookiecuzz.rookieareamusic.config.PlaybackChannelRegistry;
import io.github.rookiecuzz.rookieareamusic.spatial.RegionSpatialIndex;
import io.github.rookiecuzz.rookieareamusic.source.SoundSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable top-level runtime view published through one volatile write. */
public final class RuntimeState {
    private final Map<String, MusicDto> musics;
    private final PlaybackChannelRegistry channels;
    private final RegionSpatialIndex spatialIndex;
    private final List<SoundSource> soundSources;

    public RuntimeState(Map<String, MusicDto> musics,
                        PlaybackChannelRegistry channels,
                        RegionSpatialIndex spatialIndex,
                        Map<String, Map<String, SoundSource>> sources) {
        this.musics = musics == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(musics));
        this.channels = channels == null
                ? PlaybackChannelRegistry.defaults()
                : channels;
        this.spatialIndex = spatialIndex == null
                ? RegionSpatialIndex.empty()
                : spatialIndex;
        this.soundSources = flattenSources(sources);
    }

    public static RuntimeState empty(){
        return new RuntimeState(
                Collections.emptyMap(),
                PlaybackChannelRegistry.defaults(),
                RegionSpatialIndex.empty(),
                Collections.emptyMap()
        );
    }

    public Map<String, MusicDto> getMusics() {
        return musics;
    }

    public PlaybackChannelRegistry getChannels() {
        return channels;
    }

    public RegionSpatialIndex getSpatialIndex() {
        return spatialIndex;
    }

    public List<SoundSource> getSoundSources() {
        return soundSources;
    }

    private static List<SoundSource> flattenSources(
            Map<String, Map<String, SoundSource>> sources){
        if(sources == null || sources.isEmpty()){
            return Collections.emptyList();
        }
        List<SoundSource> result = new ArrayList<>();
        for(Map<String, SoundSource> worldSources : sources.values()){
            if(worldSources != null){
                result.addAll(worldSources.values());
            }
        }
        result.sort((first, second) -> first.getUuid().compareTo(second.getUuid()));
        return Collections.unmodifiableList(result);
    }
}
