package com.gitee.niocho.areamusic.config;

import java.util.Locale;

public enum ChannelMode {
    EXCLUSIVE,
    ADDITIVE;

    public static ChannelMode parse(String value){
        if(value == null){
            throw new IllegalArgumentException("频道 mode 不能为空");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
