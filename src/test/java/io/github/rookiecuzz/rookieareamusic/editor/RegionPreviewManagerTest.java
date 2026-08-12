package io.github.rookiecuzz.rookieareamusic.editor;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionPreviewManagerTest {
    @Test
    void togglingSameTargetHidesPreview(){
        RegionPreviewManager manager = new RegionPreviewManager();
        UUID player = UUID.randomUUID();

        assertEquals(
                RegionPreviewManager.ToggleResult.SHOWN,
                manager.toggle(player, "world", "spawn")
        );
        assertEquals(
                RegionPreviewManager.ToggleResult.HIDDEN,
                manager.toggle(player, "world", "spawn")
        );
        assertNull(manager.get(player));
    }

    @Test
    void togglingDifferentTargetReplacesPreview(){
        RegionPreviewManager manager = new RegionPreviewManager();
        UUID player = UUID.randomUUID();
        manager.toggle(player, "world", "spawn");

        assertEquals(
                RegionPreviewManager.ToggleResult.SHOWN,
                manager.toggle(player, "world_nether", "fortress")
        );
        assertEquals("world_nether", manager.get(player).getWorldName());
        assertEquals("fortress", manager.get(player).getAreaId());
        assertEquals(1, manager.snapshot().size());
    }

    @Test
    void playersHaveIndependentPreviews(){
        RegionPreviewManager manager = new RegionPreviewManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        manager.toggle(first, "world", "one");
        manager.toggle(second, "world", "two");
        manager.toggle(first, "world", "one");

        assertNull(manager.get(first));
        assertEquals("two", manager.get(second).getAreaId());
    }

    @Test
    void removeOnlyAffectsRequestedPlayer(){
        RegionPreviewManager manager = new RegionPreviewManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        manager.toggle(first, "world", "one");
        manager.toggle(second, "world", "two");

        assertTrue(manager.remove(first));
        assertFalse(manager.remove(first));
        assertNull(manager.get(first));
        assertEquals("two", manager.get(second).getAreaId());
    }

    @Test
    void clearRemovesEveryPreview(){
        RegionPreviewManager manager = new RegionPreviewManager();
        manager.toggle(UUID.randomUUID(), "world", "one");
        manager.toggle(UUID.randomUUID(), "world", "two");

        manager.clear();

        assertTrue(manager.snapshot().isEmpty());
    }

    @Test
    void snapshotIsStableAndReadOnly(){
        RegionPreviewManager manager = new RegionPreviewManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        manager.toggle(first, "world", "one");
        Map<UUID, RegionPreviewManager.Target> snapshot = manager.snapshot();

        manager.toggle(second, "world", "two");

        assertEquals(1, snapshot.size());
        assertEquals("one", snapshot.get(first).getAreaId());
        assertFalse(snapshot.containsKey(second));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void rejectsIncompleteTargetsWithoutMutatingState(){
        RegionPreviewManager manager = new RegionPreviewManager();
        UUID player = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> manager.toggle(null, "world", "one"));
        assertThrows(IllegalArgumentException.class, () -> manager.toggle(player, " ", "one"));
        assertThrows(IllegalArgumentException.class, () -> manager.toggle(player, "world", null));
        assertTrue(manager.snapshot().isEmpty());
    }
}
