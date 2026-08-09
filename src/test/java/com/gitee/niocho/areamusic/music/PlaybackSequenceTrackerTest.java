package com.gitee.niocho.areamusic.music;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaybackSequenceTrackerTest {
    @Test
    void advancesAndWrapsForEachPlayerAreaPair(){
        PlaybackSequenceTracker tracker = new PlaybackSequenceTracker();
        UUID playerUuid = UUID.randomUUID();

        assertEquals(0, tracker.next(playerUuid, "area-a", 3));
        assertEquals(1, tracker.next(playerUuid, "area-a", 3));
        assertEquals(2, tracker.next(playerUuid, "area-a", 3));
        assertEquals(0, tracker.next(playerUuid, "area-a", 3));
        assertEquals(0, tracker.next(playerUuid, "area-b", 3));
    }

    @Test
    void clearResetsPlayerSequence(){
        PlaybackSequenceTracker tracker = new PlaybackSequenceTracker();
        UUID playerUuid = UUID.randomUUID();

        tracker.next(playerUuid, "area-a", 2);
        assertEquals(1, tracker.next(playerUuid, "area-a", 2));
        tracker.clear(playerUuid);
        assertEquals(0, tracker.next(playerUuid, "area-a", 2));
    }

    @Test
    void rejectsEmptyPlaylists(){
        PlaybackSequenceTracker tracker = new PlaybackSequenceTracker();
        assertThrows(
                IllegalArgumentException.class,
                () -> tracker.next(UUID.randomUUID(), "area-a", 0)
        );
    }
}
