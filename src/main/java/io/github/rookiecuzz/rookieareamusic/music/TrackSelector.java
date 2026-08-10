package io.github.rookiecuzz.rookieareamusic.music;

import io.github.rookiecuzz.rookieareamusic.config.AreaDto;

public interface TrackSelector {
    SelectedTrack select(AreaDto area);
}
