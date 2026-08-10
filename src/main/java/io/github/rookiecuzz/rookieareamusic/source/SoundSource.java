package io.github.rookiecuzz.rookieareamusic.source;

/** Immutable runtime sound source located at a fixed world position. */
public final class SoundSource {
    private final String uuid;
    private final String worldName;
    private final String sourceId;
    private final double x;
    private final double y;
    private final double z;
    private final String soundKey;
    private final long durationSeconds;
    private final long intervalSeconds;
    private final float volume;
    private final float pitch;
    private final boolean enabled;

    public SoundSource(String uuid,
                       String worldName,
                       String sourceId,
                       double x,
                       double y,
                       double z,
                       String soundKey,
                       long durationSeconds,
                       long intervalSeconds,
                       float volume,
                       float pitch,
                       boolean enabled) {
        this.uuid = uuid;
        this.worldName = worldName;
        this.sourceId = sourceId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.soundKey = soundKey;
        this.durationSeconds = durationSeconds;
        this.intervalSeconds = intervalSeconds;
        this.volume = volume;
        this.pitch = pitch;
        this.enabled = enabled;
    }

    public String getUuid() {
        return uuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getSourceId() {
        return sourceId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public String getSoundKey() {
        return soundKey;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasSamePlaybackSettings(SoundSource other){
        return other != null
                && worldName.equals(other.worldName)
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(other.z)
                && soundKey.equals(other.soundKey)
                && durationSeconds == other.durationSeconds
                && intervalSeconds == other.intervalSeconds
                && Float.floatToIntBits(volume) == Float.floatToIntBits(other.volume)
                && Float.floatToIntBits(pitch) == Float.floatToIntBits(other.pitch);
    }
}
