package io.github.rookiecuzz.rookieregions.api.event;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.RuleResolution;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEventsTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000093"),
            "minecraft:event_test"
    );
    private final Subject subject = Subject.player(
            UUID.fromString("00000000-0000-0000-0000-000000000094")
    );

    @Test
    void regionEventsExposePayloadAndLegalIndependentHandlerLists(){
        Region region = global(State.ALLOW);
        RegionCreateEvent created = new RegionCreateEvent(region, subject);
        RegionDeleteEvent deleted = new RegionDeleteEvent(region, subject);
        RegionEnterEvent entered = new RegionEnterEvent(subject, region);
        RegionLeaveEvent left = new RegionLeaveEvent(subject, region);

        assertSame(region, created.region());
        assertSame(subject, created.actor());
        assertSame(RegionCreateEvent.getHandlerList(), created.getHandlers());
        assertSame(RegionDeleteEvent.getHandlerList(), deleted.getHandlers());
        assertSame(RegionEnterEvent.getHandlerList(), entered.getHandlers());
        assertSame(RegionLeaveEvent.getHandlerList(), left.getHandlers());
        assertNotSame(created.getHandlers(), deleted.getHandlers());

        RegionUpdateEvent updated = new RegionUpdateEvent(region, region, subject);
        assertSame(region, updated.previous());
        assertSame(region, updated.current());
        assertSame(RegionUpdateEvent.getHandlerList(), updated.getHandlers());
    }

    @Test
    void enterAttemptImplementsBukkitCancellationContract(){
        RegionEnterAttemptEvent event = new RegionEnterAttemptEvent(
                subject,
                global(State.ALLOW)
        );
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertSame(RegionEnterAttemptEvent.getHandlerList(), event.getHandlers());
    }

    @Test
    void effectiveFlagAndSnapshotEventsValidateTheirTransitions(){
        RegionSnapshot allowSnapshot = RegionSnapshot.of(
                3L,
                List.of(global(State.ALLOW))
        );
        RegionSnapshot denySnapshot = RegionSnapshot.of(
                4L,
                List.of(global(State.DENY))
        );
        RuleResolution<State> allow = new RegionQuery(allowSnapshot).resolve(
                world,
                0,
                0,
                0,
                ProtectionFlags.PVP,
                subject
        );
        RuleResolution<State> deny = new RegionQuery(denySnapshot).resolve(
                world,
                0,
                0,
                0,
                ProtectionFlags.PVP,
                subject
        );

        EffectiveFlagChangeEvent<State> flagEvent = new EffectiveFlagChangeEvent<>(
                subject,
                world,
                allow,
                deny
        );
        assertSame(ProtectionFlags.PVP, flagEvent.flag());
        assertSame(allow, flagEvent.previous());
        assertSame(deny, flagEvent.current());
        assertSame(EffectiveFlagChangeEvent.getHandlerList(), flagEvent.getHandlers());

        SnapshotPublishedEvent snapshotEvent = new SnapshotPublishedEvent(
                allowSnapshot,
                denySnapshot
        );
        assertSame(allowSnapshot, snapshotEvent.previous());
        assertSame(denySnapshot, snapshotEvent.current());
        assertSame(SnapshotPublishedEvent.getHandlerList(), snapshotEvent.getHandlers());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SnapshotPublishedEvent(denySnapshot, allowSnapshot)
        );
    }

    @Test
    void regionUpdateCannotChangeIdentity(){
        Region first = global(State.ALLOW);
        WorldId otherWorld = new WorldId(UUID.randomUUID(), "minecraft:other_event_world");
        Region other = Region.builder(
                        RegionKey.global(otherWorld),
                        GlobalShape.INSTANCE
                )
                .build();
        assertThrows(
                IllegalArgumentException.class,
                () -> new RegionUpdateEvent(first, other, subject)
        );
    }

    private Region global(State pvp){
        return Region.builder(RegionKey.global(world), GlobalShape.INSTANCE)
                .flag(ProtectionFlags.PVP, pvp)
                .build();
    }
}
