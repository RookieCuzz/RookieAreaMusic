package io.github.rookiecuzz.rookieareamusic.config;

import java.util.Locale;

public enum ChannelTrigger {
    CONTINUOUS,
    ENTER_ONCE;

    public static ChannelTrigger parse(String value){
        if(value == null){
            throw new IllegalArgumentException("频道 trigger 不能为空");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
