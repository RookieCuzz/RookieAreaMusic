package io.github.rookiecuzz.rookieareamusic.source;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Test
    void failedPlaybackRetriesWithoutWaitingForTheFullTrackDuration(){
        SoundSourcePlaybackEngine engine = new SoundSourcePlaybackEngine();
        List<SoundSource> played = new ArrayList<>();
        AtomicBoolean available = new AtomicBoolean();
        SoundSource birds = source("birds", "ambient.birds", 60, 0, true);

        engine.tick(Collections.singletonList(birds), 0, source -> available.get());
        engine.tick(Collections.singletonList(birds), 999, source -> available.get());
        available.set(true);
        engine.tick(Collections.singletonList(birds), 1000, source -> {
            played.add(source);
            return true;
        });

        assertEquals(1, played.size());
    }

    @Test
    void removingOneSharedKeyStopsAndRestartsTheRemainingSource(){
        SoundSourcePlaybackEngine engine = new SoundSourcePlaybackEngine();
        RecordingSourceSink sink = new RecordingSourceSink();
        SoundSource first = source("first", "ambient.wind", 60, 0, true);
        SoundSource second = source("second", "ambient.wind", 60, 0, true);

        engine.tick(Arrays.asList(first, second), 0, sink);
        assertEquals(2, sink.played.size());

        engine.tick(Collections.singletonList(second), 1000, sink);

        assertEquals(1, sink.stopped.size());
        assertEquals(3, sink.played.size());
        assertEquals("second", sink.played.get(2).getSourceId());
    }

    @Test
    void removingExpiredShortSourceDoesNotInterruptLongSharedKeySource(){
        SoundSourcePlaybackEngine engine = new SoundSourcePlaybackEngine();
        RecordingSourceSink sink = new RecordingSourceSink();
        SoundSource shortSource = source("short", "ambient.wind", 1, 30, true);
        SoundSource longSource = source("long", "ambient.wind", 60, 0, true);

        engine.tick(Arrays.asList(shortSource, longSource), 0, sink);
        engine.tick(Collections.singletonList(longSource), 2000, sink);

        assertEquals(0, sink.stopped.size());
        assertEquals(2, sink.played.size());
    }

    @Test
    void interruptedSharedKeyDoesNotRestartSourceDuringItsSilentInterval(){
        SoundSourcePlaybackEngine engine = new SoundSourcePlaybackEngine();
        RecordingSourceSink sink = new RecordingSourceSink();
        SoundSource removed = source("removed", "ambient.wind", 60, 0, true);
        SoundSource interval = source("interval", "ambient.wind", 1, 9, true);

        engine.tick(Arrays.asList(removed, interval), 0, sink);
        engine.tick(Collections.singletonList(interval), 2000, sink);

        assertEquals(1, sink.stopped.size());
        assertEquals(2, sink.played.size());

        engine.tick(Collections.singletonList(interval), 9999, sink);
        assertEquals(2, sink.played.size());

        engine.tick(Collections.singletonList(interval), 10000, sink);
        assertEquals(3, sink.played.size());
        assertEquals("interval", sink.played.get(2).getSourceId());
    }

    @Test
    void clearStopsEachWorldAndSoundKeyOnce(){
        SoundSourcePlaybackEngine engine = new SoundSourcePlaybackEngine();
        RecordingSourceSink sink = new RecordingSourceSink();
        SoundSource first = source("first", "ambient.wind", 60, 0, true);
        SoundSource second = source("second", "ambient.wind", 60, 0, true);

        engine.tick(Arrays.asList(first, second), 0, sink);
        engine.clear(sink);

        assertEquals(1, sink.stopped.size());
        assertEquals(0, engine.size());
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

    private static final class RecordingSourceSink implements SoundSourceSink {
        private final List<SoundSource> played = new ArrayList<>();
        private final List<SoundSource> stopped = new ArrayList<>();

        @Override
        public boolean play(SoundSource source) {
            played.add(source);
            return true;
        }

        @Override
        public void stop(SoundSource source) {
            stopped.add(source);
        }
    }
}
