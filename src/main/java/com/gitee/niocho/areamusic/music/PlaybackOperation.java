package com.gitee.niocho.areamusic.music;

public final class PlaybackOperation {
    public enum Type {
        PLAY,
        STOP
    }

    private final Type type;
    private final SelectedTrack track;
    private final String soundKey;

    private PlaybackOperation(Type type,
                              SelectedTrack track,
                              String soundKey) {
        this.type = type;
        this.track = track;
        this.soundKey = soundKey;
    }

    public static PlaybackOperation play(SelectedTrack track){
        return new PlaybackOperation(Type.PLAY, track, track.getSoundKey());
    }

    public static PlaybackOperation stop(String soundKey){
        return new PlaybackOperation(Type.STOP, null, soundKey);
    }

    public Type getType() {
        return type;
    }

    public SelectedTrack getTrack() {
        return track;
    }

    public String getSoundKey() {
        return soundKey;
    }
}
