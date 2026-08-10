package io.github.rookiecuzz.rookieareamusic.source;

public interface SoundSourceSink {
    /** @return true when the sound was handed to a loaded world successfully. */
    boolean play(SoundSource source);

    default void stop(SoundSource source){
        // Test and headless sinks may not need lifecycle stop events.
    }
}
