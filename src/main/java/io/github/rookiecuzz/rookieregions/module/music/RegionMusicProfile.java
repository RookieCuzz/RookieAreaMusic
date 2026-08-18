package io.github.rookiecuzz.rookieregions.module.music;

import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable music-module attachment for one region record. */
public final class RegionMusicProfile {
    private static final RegionMusicProfile EMPTY =
            new RegionMusicProfile(
                    Collections.<String, RegionMusicChannel>emptyMap(),
                    ModuleRegionBinding.nativeSelf()
            );

    private final Map<String, RegionMusicChannel> channels;
    private final ModuleRegionBinding binding;

    public RegionMusicProfile(Map<String, RegionMusicChannel> channels) {
        this(channels, ModuleRegionBinding.nativeSelf());
    }

    public RegionMusicProfile(Map<String, RegionMusicChannel> channels,
                              ModuleRegionBinding binding) {
        Map<String, RegionMusicChannel> copy = new LinkedHashMap<>();
        if(channels != null){
            for(Map.Entry<String, RegionMusicChannel> entry : channels.entrySet()){
                String channel = requireKey(entry.getKey(), "music channel");
                if(entry.getValue() == null){
                    throw new IllegalArgumentException(
                            "music channel policy must not be null: " + channel
                    );
                }
                if(copy.put(channel, entry.getValue()) != null){
                    throw new IllegalArgumentException(
                            "duplicate normalized music channel: " + channel
                    );
                }
            }
        }
        this.channels = Collections.unmodifiableMap(copy);
        this.binding = Objects.requireNonNull(
                binding,
                "music module binding cannot be null"
        );
    }

    public static RegionMusicProfile empty(){
        return EMPTY;
    }

    public Map<String, RegionMusicChannel> getChannels() {
        return channels;
    }

    public RegionMusicChannel getChannel(String channel){
        return channel == null ? null : channels.get(channel.trim());
    }

    public ModuleRegionBinding getBinding() {
        return binding;
    }

    public RegionMusicProfile withBinding(ModuleRegionBinding changed) {
        return new RegionMusicProfile(channels, changed);
    }

    public boolean isEmpty(){
        return channels.isEmpty();
    }

    @Override
    public boolean equals(Object value) {
        if(this == value){
            return true;
        }
        if(!(value instanceof RegionMusicProfile)){
            return false;
        }
        RegionMusicProfile other = (RegionMusicProfile) value;
        return channels.equals(other.channels) && binding.equals(other.binding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channels, binding);
    }

    static String requireKey(String value, String label){
        if(value == null || value.trim().isEmpty()){
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
