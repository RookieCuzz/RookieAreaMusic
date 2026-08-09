package com.gitee.niocho.areamusic.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecordingPlaybackSink implements PlaybackSink {
    private final List<PlaybackOperation> operations = new ArrayList<>();

    @Override
    public void play(SelectedTrack track) {
        operations.add(PlaybackOperation.play(track));
    }

    @Override
    public void stop(String soundKey) {
        operations.add(PlaybackOperation.stop(soundKey));
    }

    public List<PlaybackOperation> snapshot(){
        return Collections.unmodifiableList(new ArrayList<>(operations));
    }
}
