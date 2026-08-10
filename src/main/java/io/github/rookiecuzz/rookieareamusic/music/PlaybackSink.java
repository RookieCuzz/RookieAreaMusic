package io.github.rookiecuzz.rookieareamusic.music;

import io.github.rookiecuzz.rookieareamusic.config.AreaDto;

import java.util.List;

public interface PlaybackSink {
    void play(SelectedTrack track);

    void stop(String soundKey);

    void enterCommands(AreaDto area,
                       boolean hasFrozenExitCommands,
                       long actionToken);

    void exitCommands(AreaDto area,
                      List<String> commandTemplates,
                      long actionToken);
}
