package io.github.rookiecuzz.rookieregions.module.music;

import java.util.Locale;

/** Defines how one region changes the inherited music for one channel. */
public enum MusicPolicyMode {
    INHERIT,
    ADD,
    REPLACE,
    BLOCK;

    public static MusicPolicyMode parse(String value){
        if(value == null || value.trim().isEmpty()){
            throw new IllegalArgumentException("music policy must be explicit");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception){
            throw new IllegalArgumentException(
                    "unknown music policy: " + value
                            + " (expected inherit, add, replace, or block)",
                    exception
            );
        }
    }
}
