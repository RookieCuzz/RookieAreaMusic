package io.github.rookiecuzz.rookieregions.module.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable policy and playback settings contributed to one channel. */
public final class RegionMusicChannel {
    private final MusicPolicyMode policy;
    private final int order;
    private final boolean random;
    private final boolean loop;
    private final float volume;
    private final float pitch;
    private final boolean overwrite;
    private final List<MusicTrack> tracks;

    private RegionMusicChannel(Builder builder) {
        if(builder.policy == null){
            throw new IllegalArgumentException("music policy must be explicit");
        }
        validateVolume(builder.volume);
        validatePitch(builder.pitch);

        List<MusicTrack> copiedTracks = immutableTracks(builder.tracks);
        boolean contributesTracks = builder.policy == MusicPolicyMode.ADD
                || builder.policy == MusicPolicyMode.REPLACE;
        if(contributesTracks && copiedTracks.isEmpty()){
            throw new IllegalArgumentException(
                    builder.policy + " music policy requires at least one track"
            );
        }
        if(!contributesTracks && !copiedTracks.isEmpty()){
            throw new IllegalArgumentException(
                    builder.policy + " music policy must not contain tracks"
            );
        }

        this.policy = builder.policy;
        this.order = builder.order;
        this.random = builder.random;
        this.loop = builder.loop;
        this.volume = builder.volume;
        this.pitch = builder.pitch;
        this.overwrite = builder.overwrite;
        this.tracks = copiedTracks;
    }

    public static Builder builder(){
        return new Builder();
    }

    public MusicPolicyMode getPolicy() {
        return policy;
    }

    public int getOrder() {
        return order;
    }

    public boolean isRandom() {
        return random;
    }

    public boolean isLoop() {
        return loop;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public List<MusicTrack> getTracks() {
        return tracks;
    }

    @Override
    public boolean equals(Object value) {
        if(this == value){
            return true;
        }
        if(!(value instanceof RegionMusicChannel)){
            return false;
        }
        RegionMusicChannel other = (RegionMusicChannel) value;
        return order == other.order
                && random == other.random
                && loop == other.loop
                && Float.compare(volume, other.volume) == 0
                && Float.compare(pitch, other.pitch) == 0
                && overwrite == other.overwrite
                && policy == other.policy
                && tracks.equals(other.tracks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                policy, order, random, loop, volume, pitch, overwrite, tracks
        );
    }

    private static List<MusicTrack> immutableTracks(List<MusicTrack> source){
        List<MusicTrack> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if(source != null){
            for(MusicTrack track : source){
                if(track == null){
                    throw new IllegalArgumentException("music tracks must not contain null");
                }
                if(!ids.add(track.getId())){
                    throw new IllegalArgumentException(
                            "duplicate music track id: " + track.getId()
                    );
                }
                result.add(track);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static void validateVolume(float value){
        if(Float.isNaN(value) || Float.isInfinite(value)
                || value < 0.0f || value > 1.0f){
            throw new IllegalArgumentException("music volume must be between 0 and 1");
        }
    }

    private static void validatePitch(float value){
        if(Float.isNaN(value) || Float.isInfinite(value)
                || value <= 0.0f || value > 2.0f){
            throw new IllegalArgumentException("music pitch must be greater than 0 and at most 2");
        }
    }

    public static final class Builder {
        private MusicPolicyMode policy;
        private int order;
        private boolean random;
        private boolean loop = true;
        private float volume = 1.0f;
        private float pitch = 1.0f;
        private boolean overwrite = true;
        private List<MusicTrack> tracks = Collections.emptyList();

        private Builder() {
        }

        public Builder policy(MusicPolicyMode policy){
            this.policy = policy;
            return this;
        }

        public Builder order(int order){
            this.order = order;
            return this;
        }

        public Builder random(boolean random){
            this.random = random;
            return this;
        }

        public Builder loop(boolean loop){
            this.loop = loop;
            return this;
        }

        public Builder volume(float volume){
            this.volume = volume;
            return this;
        }

        public Builder pitch(float pitch){
            this.pitch = pitch;
            return this;
        }

        public Builder overwrite(boolean overwrite){
            this.overwrite = overwrite;
            return this;
        }

        public Builder tracks(List<MusicTrack> tracks){
            this.tracks = tracks;
            return this;
        }

        public RegionMusicChannel build(){
            return new RegionMusicChannel(this);
        }
    }
}
