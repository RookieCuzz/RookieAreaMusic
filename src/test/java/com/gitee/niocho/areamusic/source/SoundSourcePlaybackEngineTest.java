package com.gitee.niocho.areamusic.source;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundSourcePlaybackEngineTest {
    @Test
    void playsImmediatelyThenWaitsForDurationAndInterval(){
        SoundSourcePlaybackEngine engine = new SoundSourcePlaybackEngine();
        List<SoundSource> played = new ArrayList<>();
        SoundSource birds = source("birds", "ambient.birds", 6, 4, true);

        engine.tick(Collections.singletonList(birds), 1000, played::add);
        engine.tick(Collections.singletonList(birds), 10999, played::add);
        assertEquals(1, played.size());

        engine.tick(Collections.singletonList(birds), 11000, played::add);
        assertEquals(2, played.size());
    }

    @Test
    void changingPlaybackSettingsTriggersAnImmediateFreshCycle(){
        SoundSourcePlaybackEngine engine = new SoundSourcePlaybackEngine();
        List<SoundSource> played = new ArrayList<>();
        SoundSource before = source("birds", "ambient.birds", 20, 0, true);
        SoundSource after = source("birds", "ambient.birds_night", 20, 0, true);

        engine.tick(Collections.singletonList(before), 0, played::add);
        engine.tick(Collections.singletonList(after), 1000, played::add);

        assertEquals(2, played.size());
        assertEquals("ambient.birds_night", played.get(1).getSoundKey());
    }

    @Test
    void disabledOrDeletedSourcesDropTheirSchedulerState(){
        SoundSourcePlaybackEngine engine = new SoundSourcePlaybackEngine();
        List<SoundSource> played = new ArrayList<>();
        SoundSource enabled = source("birds", "ambient.birds", 20, 0, true);
        SoundSource disabled = source("birds", "ambient.birds", 20, 0, false);

        engine.tick(Collections.singletonList(enabled), 0, played::add);
        assertEquals(1, engine.size());

        engine.tick(Arrays.asList(disabled), 1000, played::add);
        assertEquals(0, engine.size());

        engine.tick(Collections.singletonList(enabled), 2000, played::add);
        assertEquals(2, played.size());
    }

    private SoundSource source(String id,
                               String sound,
                               long duration,
                               long interval,
                               boolean enabled){
        return new SoundSource(
                id,
                "world",
                id,
                10.5,
                70.5,
                -4.5,
                sound,
                duration,
                interval,
                1.0f,
                1.0f,
                enabled
        );
    }
}
