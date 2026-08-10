package io.github.rookiecuzz.rookieareamusic.music;

public final class SelectedTrack {
    private final String musicId;
    private final String musicUuid;
    private final String soundKey;
    private final long durationMillis;
    private final float volume;
    private final float pitch;

    public SelectedTrack(String musicId,
                         String musicUuid,
                         String soundKey,
                         long durationMillis,
                         float volume,
                         float pitch) {
        this.musicId = musicId;
        this.musicUuid = musicUuid;
        this.soundKey = soundKey;
        this.durationMillis = durationMillis;
        this.volume = volume;
        this.pitch = pitch;
    }

    public String getMusicId() {
        return musicId;
    }

    public String getMusicUuid() {
        return musicUuid;
    }

    public String getSoundKey() {
        return soundKey;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }
}
