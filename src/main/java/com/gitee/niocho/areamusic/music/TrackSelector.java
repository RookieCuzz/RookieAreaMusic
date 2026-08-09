package com.gitee.niocho.areamusic.music;

import com.gitee.niocho.areamusic.config.AreaDto;

public interface TrackSelector {
    SelectedTrack select(AreaDto area);
}
