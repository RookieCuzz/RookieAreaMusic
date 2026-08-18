package io.github.rookiecuzz.rookieregions.editor.model;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequest;
import io.github.rookiecuzz.rookieregions.mutation.SaveMode;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEditSessionTest {
    private static final WorldId WORLD = new WorldId(
            UUID.fromString("72000000-0000-0000-0000-000000000002"),
            "minecraft:overworld"
    );
    private static final UUID ACTOR = UUID.fromString(
            "73000000-0000-0000-0000-000000000003"
    );
    private static final RegionKey KEY = new RegionKey(WORLD, "spawn");

    @Test
    void editCandidateChangesOnlyShapeAndPreservesAllCoreMetadata() {
        Region base = baseRegion();
        RegionDraft draft = RegionDraft.cuboid(WORLD)
                .setPos1(new BlockPoint(20, 30, 40))
                .setPos2(new BlockPoint(22, 34, 46));
        RegionEditSession session = new RegionEditSession(
                " edit-1 ", ACTOR, SaveMode.EDIT, KEY, base, 19,
                Optional.of(" target-hash "), draft
        );

        Region candidate = session.candidate();

        assertEquals("edit-1", session.sessionId());
        assertEquals("target-hash", session.targetFingerprint().orElseThrow());
        assertEquals(base.key(), candidate.key());
        assertEquals(base.priority(), candidate.priority());
        assertEquals(base.parent(), candidate.parent());
        assertEquals(base.owners().players(), candidate.owners().players());
        assertEquals(base.owners().groups(), candidate.owners().groups());
        assertEquals(base.members().players(), candidate.members().players());
        assertEquals(base.members().groups(), candidate.members().groups());
        assertEquals(base.flags().keySet(), candidate.flags().keySet());
        assertEquals(
                State.DENY,
                candidate.flag(ProtectionFlags.BUILD).orElseThrow().value()
        );
        assertFalse(base.shape().relationTo(candidate.shape()) == ShapeRelation.EQUAL);
        assertEquals(new CuboidShape(0, 0, 0, 10, 10, 10).bounds(), base.shape().bounds());

        RegionSaveRequest request = session.saveRequest();
        assertEquals(session.sessionId(), request.sessionId());
        assertEquals(SaveMode.EDIT, request.mode());
        assertEquals(19, request.expectedSnapshotRevision());
        assertEquals(Optional.of("target-hash"), request.expectedTargetFingerprint());
        assertTrue(request.confirmationToken().isEmpty());
    }

    @Test
    void createCandidateUsesGlobalParentAndActorOwnership() {
        RegionEditSession session = new RegionEditSession(
                "create-1", ACTOR, SaveMode.CREATE, KEY, null, 0,
                Optional.empty(),
                RegionDraft.cuboid(WORLD)
                        .setPos1(new BlockPoint(0, 0, 0))
                        .setPos2(new BlockPoint(0, 0, 0))
        );

        Region candidate = session.candidate();
        assertEquals(0, candidate.priority());
        assertEquals(RegionKey.global(WORLD), candidate.parent().orElseThrow());
        assertEquals(List.of(ACTOR), candidate.owners().players().stream().toList());
        assertTrue(candidate.owners().groups().isEmpty());
        assertTrue(candidate.members().isEmpty());
        assertTrue(candidate.flags().isEmpty());
    }

    @Test
    void modeBaseAndFingerprintCombinationsAreValidated() {
        RegionDraft draft = validDraft(WORLD);
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                "x", ACTOR, SaveMode.CREATE, KEY, baseRegion(), 0,
                Optional.empty(), draft
        ));
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                "x", ACTOR, SaveMode.CREATE, KEY, null, 0,
                Optional.of("unexpected"), draft
        ));
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                "x", ACTOR, SaveMode.EDIT, KEY, null, 0,
                Optional.of("hash"), draft
        ));
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                "x", ACTOR, SaveMode.EDIT, KEY, baseRegion(), 0,
                Optional.empty(), draft
        ));
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                "x", ACTOR, SaveMode.CREATE, RegionKey.global(WORLD), null, 0,
                Optional.empty(), draft
        ));
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                "x", ACTOR, SaveMode.CREATE, KEY, null, -1,
                Optional.empty(), draft
        ));
    }

    @Test
    void draftAndBaseWorldMetadataMustExactlyMatchSessionWorld() {
        WorldId renamed = new WorldId(WORLD.uuid(), "custom:renamed");
        RegionDraft wrongDraft = validDraft(renamed);
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                "x", ACTOR, SaveMode.CREATE, KEY, null, 0,
                Optional.empty(), wrongDraft
        ));

        Region renamedBase = Region.builder(
                        new RegionKey(renamed, KEY.id()),
                        new CuboidShape(0, 0, 0, 2, 2, 2)
                )
                .parent(RegionKey.global(renamed))
                .build();
        assertThrows(IllegalArgumentException.class, () -> new RegionEditSession(
                "x", ACTOR, SaveMode.EDIT, KEY, renamedBase, 0,
                Optional.of("hash"), validDraft(WORLD)
        ));
    }

    private static Region baseRegion() {
        return Region.builder(KEY, new CuboidShape(0, 0, 0, 10, 10, 10))
                .priority(14)
                .parent(RegionKey.global(WORLD))
                .owners(new RegionDomain(
                        List.of(UUID.fromString("74000000-0000-0000-0000-000000000004")),
                        List.of("admins")
                ))
                .members(new RegionDomain(List.of(), List.of("builders")))
                .flag(ProtectionFlags.BUILD, State.DENY)
                .flag(ProtectionFlags.PVP, State.ALLOW)
                .build();
    }

    private static RegionDraft validDraft(WorldId world) {
        return RegionDraft.cuboid(world)
                .setPos1(new BlockPoint(0, 0, 0))
                .setPos2(new BlockPoint(1, 1, 1));
    }
}
