package io.github.rookiecuzz.rookieregions.editor.bukkit;

import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import io.github.rookiecuzz.rookieregions.editor.model.BlockPoint;
import io.github.rookiecuzz.rookieregions.editor.model.RegionDraft;
import io.github.rookiecuzz.rookieregions.editor.model.RegionEditSession;
import io.github.rookiecuzz.rookieregions.editor.model.RegionEditorManager;
import io.github.rookiecuzz.rookieregions.mutation.SaveMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BukkitRegionEditorTest {
    private final UUID actor = UUID.fromString(
            "00000000-0000-0000-0000-000000000401"
    );
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000402"),
            "minecraft:overworld"
    );

    @Test
    void cuboidPrimaryAndSecondaryClicksSetBothCorners(){
        RegionEditorManager manager = new RegionEditorManager();
        BukkitRegionEditor editor = new BukkitRegionEditor(manager);
        manager.begin(session("cuboid", RegionDraft.cuboid(world)));

        editor.select(actor, world, new BlockPoint(1, 2, 3),
                SelectionClick.PRIMARY);
        editor.select(actor, world, new BlockPoint(4, 5, 6),
                SelectionClick.SECONDARY);

        CuboidShape shape = assertInstanceOf(
                CuboidShape.class,
                manager.finish(actor).shape()
        );
        assertEquals(1.0d, shape.bounds().minX());
        assertEquals(7.0d, shape.bounds().maxZ());
    }

    @Test
    void polygonClicksAddPointsAndUndoClearStaySessionBound(){
        RegionEditorManager manager = new RegionEditorManager();
        BukkitRegionEditor editor = new BukkitRegionEditor(manager);
        manager.begin(session(
                "polygon",
                RegionDraft.polygon(world).setPolygonHeights(0, 10)
        ));

        editor.select(actor, world, new BlockPoint(0, 99, 0),
                SelectionClick.PRIMARY);
        editor.select(actor, world, new BlockPoint(10, 99, 0),
                SelectionClick.SECONDARY);
        editor.select(actor, world, new BlockPoint(0, 99, 10),
                SelectionClick.SECONDARY);

        PolygonPrismShape shape = assertInstanceOf(
                PolygonPrismShape.class,
                manager.finish(actor).shape()
        );
        assertEquals(3, shape.vertices().size());
        editor.undo(actor);
        assertThrows(IllegalStateException.class, () -> manager.finish(actor));
        assertEquals(2, editor.clear(actor));
    }

    @Test
    void selectionRejectsAnotherWorld(){
        RegionEditorManager manager = new RegionEditorManager();
        BukkitRegionEditor editor = new BukkitRegionEditor(manager);
        manager.begin(session("cuboid", RegionDraft.cuboid(world)));
        WorldId other = new WorldId(
                UUID.fromString("00000000-0000-0000-0000-000000000403"),
                "minecraft:the_nether"
        );

        assertThrows(IllegalArgumentException.class, () -> editor.select(
                actor, other, new BlockPoint(0, 0, 0), SelectionClick.PRIMARY
        ));
    }

    @Test
    void changingDraftInvalidatesAnyOutstandingConfirmation(){
        RegionEditorManager manager = new RegionEditorManager();
        List<String> invalidated = new ArrayList<>();
        BukkitRegionEditor editor = new BukkitRegionEditor(
                manager,
                (actorId, sessionId) -> invalidated.add(
                        actorId + "/" + sessionId
                )
        );
        RegionEditSession session = session("invalidate", RegionDraft.cuboid(world));
        manager.begin(session);

        editor.select(actor, world, new BlockPoint(1, 2, 3),
                SelectionClick.PRIMARY);

        assertEquals(List.of(actor + "/" + session.sessionId()), invalidated);
    }

    @Test
    void changingExplicitMinimumHeightInvalidatesConfirmation(){
        RegionEditorManager manager = new RegionEditorManager();
        List<String> invalidated = new ArrayList<>();
        BukkitRegionEditor editor = new BukkitRegionEditor(
                manager,
                (actorId, sessionId) -> invalidated.add(
                        actorId + "/" + sessionId
                )
        );
        RegionEditSession session = session(
                "min-height",
                RegionDraft.polygon(world).setPolygonHeights(0, 10)
        );
        manager.begin(session);

        editor.setMinY(actor, -5);

        assertEquals(-5, session.draft().minY().orElseThrow());
        assertEquals(List.of(actor + "/" + session.sessionId()), invalidated);
    }

    private RegionEditSession session(String id, RegionDraft draft){
        return new RegionEditSession(
                "session-" + id,
                actor,
                SaveMode.CREATE,
                new RegionKey(world, id),
                null,
                1L,
                Optional.empty(),
                draft
        );
    }
}
