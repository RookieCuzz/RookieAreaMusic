package io.github.rookiecuzz.rookieregions.module.music;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MusicPlaybackPlannerTest {
    private final MusicPlaybackPlanner planner = new MusicPlaybackPlanner();

    @Test
    void keepsRandomSelectionStableUntilTheTrackIsDue() {
        MusicTrack firstTrack = new MusicTrack("first", "rookie.first", 60L);
        MusicTrack secondTrack = new MusicTrack("second", "rookie.second", 60L);
        ResolvedMusicLayer layer = layer(
                "region",
                true,
                true,
                true,
                firstTrack,
                secondTrack
        );
        AtomicInteger selections = new AtomicInteger();

        MusicPlaybackPlanner.Plan first = planner.plan(
                Map.of(),
                Map.of("bgm:0:region", layer),
                100L,
                bound -> {
                    assertEquals(2, bound);
                    selections.incrementAndGet();
                    return 1;
                }
        );
        MusicPlaybackPlanner.ActiveTrack selected = first.next().get(
                "bgm:0:region"
        );
        assertSame(secondTrack, selected.track());
        assertEquals(1, selections.get());

        MusicPlaybackPlanner.Plan held = planner.plan(
                first.next(),
                Map.of("bgm:0:region", layer),
                selected.nextPlayNanos() - 1L,
                bound -> {
                    fail("a valid random track must not be selected again");
                    return 0;
                }
        );
        assertSame(selected, held.next().get("bgm:0:region"));
        assertTrue(held.starts().isEmpty());

        MusicPlaybackPlanner.Plan replay = planner.plan(
                held.next(),
                Map.of("bgm:0:region", layer),
                selected.nextPlayNanos(),
                bound -> 0
        );
        assertSame(firstTrack, replay.next().get("bgm:0:region").track());
        assertEquals(1, replay.starts().size());
    }

    @Test
    void doesNotStopASoundStillUsedByAnotherContinuingLayer() {
        MusicTrack shared = new MusicTrack("shared", "rookie.shared", 60L);
        ResolvedMusicLayer continuingLayer = layer(
                "continuing", false, true, true, shared
        );
        long deadline = 1_000L;
        MusicPlaybackPlanner.ActiveTrack removed = active(shared, deadline);
        MusicPlaybackPlanner.ActiveTrack continuing = active(shared, deadline);
        Map<String, MusicPlaybackPlanner.ActiveTrack> previous = new LinkedHashMap<>();
        previous.put("removed", removed);
        previous.put("continuing", continuing);

        MusicPlaybackPlanner.Plan result = planner.plan(
                previous,
                Map.of("continuing", continuingLayer),
                deadline - 1L,
                bound -> 0
        );

        assertTrue(result.stopSounds().isEmpty());
        assertEquals(Map.of("continuing", continuing), result.next());
        assertTrue(result.starts().isEmpty());
    }

    @Test
    void nonOverwriteReplayDoesNotStopItsNaturalTail() {
        MusicTrack track = new MusicTrack("theme", "rookie.theme", 60L);
        ResolvedMusicLayer layer = layer(
                "region", false, true, false, track
        );
        MusicPlaybackPlanner.ActiveTrack current = new MusicPlaybackPlanner.ActiveTrack(
                track, 50L, true, 1.0f, 1.0f, false
        );

        MusicPlaybackPlanner.Plan result = planner.plan(
                Map.of("layer", current),
                Map.of("layer", layer),
                50L,
                bound -> 0
        );

        assertTrue(result.stopSounds().isEmpty());
        assertEquals(1, result.starts().size());
    }

    private static MusicPlaybackPlanner.ActiveTrack active(MusicTrack track,
                                                            long deadline) {
        return new MusicPlaybackPlanner.ActiveTrack(
                track, deadline, true, 1.0f, 1.0f, true
        );
    }

    private static ResolvedMusicLayer layer(String region,
                                            boolean random,
                                            boolean loop,
                                            boolean overwrite,
                                            MusicTrack... tracks) {
        RegionMusicChannel source = RegionMusicChannel.builder()
                .policy(MusicPolicyMode.ADD)
                .random(random)
                .loop(loop)
                .overwrite(overwrite)
                .tracks(List.of(tracks))
                .build();
        return new ResolvedMusicLayer(region, "bgm", 0, source);
    }
}
