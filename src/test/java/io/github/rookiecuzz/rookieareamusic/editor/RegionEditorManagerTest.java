package io.github.rookiecuzz.rookieareamusic.editor;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEditorManagerTest {
    @Test
    void locksOneRegionToOneAdministrator(){
        RegionEditorManager manager = new RegionEditorManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        RegionEditSession session = session("roi");

        manager.begin(first, session);

        assertTrue(manager.isLocked("world", "roi"));
        assertThrows(IllegalStateException.class, () -> manager.begin(second, session("roi")));
        assertSame(session, manager.end(first));
        assertFalse(manager.isLocked("world", "roi"));
        manager.begin(second, session("roi"));
        assertEquals(1, manager.size());
    }

    @Test
    void onePlayerCannotOpenTwoSessionsButDifferentRegionsCanBeEdited(){
        RegionEditorManager manager = new RegionEditorManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        manager.begin(first, session("one"));
        assertThrows(IllegalStateException.class, () -> manager.begin(first, session("two")));
        manager.begin(second, session("two"));

        assertEquals(2, manager.size());
    }

    private RegionEditSession session(String areaId){
        return new RegionEditSession(
                RegionEditSession.Mode.CREATE,
                "world",
                areaId,
                0,
                100,
                10,
                null
        );
    }
}
