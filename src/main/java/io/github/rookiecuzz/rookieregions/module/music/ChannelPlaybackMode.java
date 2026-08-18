package io.github.rookiecuzz.rookieregions.module.music;

import java.util.Locale;

/** Selects either one winning layer or several simultaneous layers. */
public enum ChannelPlaybackMode {
    EXCLUSIVE,
    LAYERED;

    public static ChannelPlaybackMode parse(String value){
        if(value == null || value.trim().isEmpty()){
            throw new IllegalArgumentException("channel playback mode must be explicit");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception){
            throw new IllegalArgumentException(
                    "unknown channel playback mode: " + value
                            + " (expected exclusive or layered)",
                    exception
            );
        }
    }
}
