package io.github.rookiecuzz.rookieareamusic.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public final class PlaybackChannelConfig {
    private final ChannelMode mode;
    private final Integer maxLayers;
    private final ChannelTrigger trigger;
}
