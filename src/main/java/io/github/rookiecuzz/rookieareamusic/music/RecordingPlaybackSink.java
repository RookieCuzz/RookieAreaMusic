package io.github.rookiecuzz.rookieareamusic.music;

import io.github.rookiecuzz.rookieareamusic.config.AreaDto;

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

    @Override
    public void enterCommands(AreaDto area,
                              boolean hasFrozenExitCommands,
                              long actionToken) {
        operations.add(PlaybackOperation.enterCommands(
                area,
                hasFrozenExitCommands,
                actionToken
        ));
    }

    @Override
    public void exitCommands(AreaDto area,
                             List<String> commandTemplates,
                             long actionToken) {
        operations.add(PlaybackOperation.exitCommands(
                area,
                commandTemplates,
                actionToken
        ));
    }

    public List<PlaybackOperation> snapshot(){
        return Collections.unmodifiableList(new ArrayList<>(operations));
    }
}
