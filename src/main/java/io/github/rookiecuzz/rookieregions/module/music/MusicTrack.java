package io.github.rookiecuzz.rookieregions.module.music;

import java.util.Objects;

/** One immutable sound entry in a region playlist. */
public final class MusicTrack {
    private final String id;
    private final String sound;
    private final long durationSeconds;

    public MusicTrack(String id, String sound, long durationSeconds) {
        this.id = requireText(id, "track id");
        this.sound = requireText(sound, "track sound");
        if(durationSeconds <= 0L){
            throw new IllegalArgumentException("track duration must be positive");
        }
        this.durationSeconds = durationSeconds;
    }

    public String getId() {
        return id;
    }

    public String getSound() {
        return sound;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    @Override
    public boolean equals(Object value) {
        if(this == value){
            return true;
        }
        if(!(value instanceof MusicTrack)){
            return false;
        }
        MusicTrack other = (MusicTrack) value;
        return durationSeconds == other.durationSeconds
                && id.equals(other.id)
                && sound.equals(other.sound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sound, durationSeconds);
    }

    private static String requireText(String value, String label){
        if(value == null || value.trim().isEmpty()){
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
