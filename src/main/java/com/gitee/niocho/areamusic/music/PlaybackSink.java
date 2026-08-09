package com.gitee.niocho.areamusic.music;

public interface PlaybackSink {
    void play(SelectedTrack track);

    void stop(String soundKey);
}
