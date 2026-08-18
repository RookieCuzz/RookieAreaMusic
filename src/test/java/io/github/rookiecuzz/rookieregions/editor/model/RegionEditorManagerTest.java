package io.github.rookiecuzz.rookieregions.editor.model;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.mutation.SaveMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEditorManagerTest {
    private static final WorldId WORLD = new WorldId(
            UUID.fromString("75000000-0000-0000-0000-000000000005"),
            "minecraft:overworld"
    );
    private static final UUID ACTOR_ONE = UUID.fromString(
            "76000000-0000-0000-0000-000000000006"
    );
    private static final UUID ACTOR_TWO = UUID.fromString(
            "77000000-0000-0000-0000-000000000007"
    );

    @Test
    void enforcesOneSessionPerActorAndOneLockPerRegion() {
        RegionEditorManager manager = new RegionEditorManager();
        RegionEditSession first = session("s1", ACTOR_ONE, "spawn", validDraft());
        manager.begin(first);

        assertEquals(1, manager.size());
        assertSame(first, manager.session(ACTOR_ONE).orElseThrow());
        assertTrue(manager.isLocked(first.key()));
        assertEquals(ACTOR_ONE, manager.lockOwner(first.key()).orElseThrow());

        assertThrows(
                IllegalStateException.class,
                () -> manager.begin(session("s2", ACTOR_ONE, "other", validDraft()))
        );
        assertThrows(
                IllegalStateException.class,
                () -> manager.begin(session("s3", ACTOR_TWO, "spawn", validDraft()))
        );
        assertEquals(1, manager.size());
    }

    @Test
    void finishAndFailedSaveKeepSessionAndLockUntilMatchingSavedResult() {
        RegionEditorManager manager = new RegionEditorManager();
        RegionEditSession session = session("save-1", ACTOR_ONE, "spawn", validDraft());
        manager.begin(session);

        Region candidate = manager.finish(ACTOR_ONE);
        assertEquals(session.key(), candidate.key());
        assertTrue(manager.isLocked(session.key()));
        assertTrue(manager.session(ACTOR_ONE).isPresent());
        assertEquals(session.sessionId(), manager.finishRequest(ACTOR_ONE).sessionId());
        assertEquals(
                Optional.of("confirmation-token"),
                manager.finishRequest(ACTOR_ONE, "confirmation-token")
                        .confirmationToken()
        );
        assertTrue(manager.isLocked(session.key()));

        assertThrows(
                IllegalStateException.class,
                () -> manager.markSaved(ACTOR_ONE, "stale-session")
        );
        assertTrue(manager.isLocked(session.key()));
        assertTrue(manager.session(ACTOR_ONE).isPresent());

        assertSame(session, manager.markSaved(ACTOR_ONE, "save-1"));
        assertFalse(manager.isLocked(session.key()));
        assertTrue(manager.session(ACTOR_ONE).isEmpty());
    }

    @Test
    void validationFailureDuringFinishAlsoKeepsSessionAndLock() {
        RegionEditorManager manager = new RegionEditorManager();
        RegionDraft invalid = RegionDraft.polygon(WORLD).setPolygonHeights(0, 10);
        RegionEditSession session = session("bad", ACTOR_ONE, "spawn", invalid);
        manager.begin(session);

        assertThrows(IllegalStateException.class, () -> manager.finish(ACTOR_ONE));
        assertTrue(manager.isLocked(session.key()));
        assertSame(session, manager.get(ACTOR_ONE));
    }

    @Test
    void explicitCancelAbandonsSessionAndUnlocksRegion() {
        RegionEditorManager manager = new RegionEditorManager();
        RegionEditSession first = session("cancel", ACTOR_ONE, "spawn", validDraft());
        manager.begin(first);

        assertSame(first, manager.cancel(ACTOR_ONE));
        assertEquals(0, manager.size());
        assertFalse(manager.isLocked(first.key()));

        RegionEditSession second = session("next", ACTOR_TWO, "spawn", validDraft());
        manager.begin(second);
        assertSame(second, manager.get(ACTOR_TWO));
    }

    @Test
    void actorArgumentMustMatchSessionAndSnapshotsCannotMutateManager() {
        RegionEditorManager manager = new RegionEditorManager();
        RegionEditSession session = session("x", ACTOR_ONE, "spawn", validDraft());
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.begin(ACTOR_TWO, session)
        );
        manager.begin(ACTOR_ONE, session);
        assertThrows(
                UnsupportedOperationException.class,
                () -> manager.snapshot().clear()
        );
        assertEquals(1, manager.size());
    }

    private static RegionEditSession session(String sessionId,
                                             UUID actor,
                                             String id,
                                             RegionDraft draft) {
        return new RegionEditSession(
                sessionId,
                actor,
                SaveMode.CREATE,
                new RegionKey(WORLD, id),
                null,
                0,
                Optional.empty(),
                draft
        );
    }

    private static RegionDraft validDraft() {
        return RegionDraft.cuboid(WORLD)
                .setPos1(new BlockPoint(0, 0, 0))
                .setPos2(new BlockPoint(1, 1, 1));
    }
}
