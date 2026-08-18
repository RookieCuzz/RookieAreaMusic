package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectiveFlagTrackerTest {
    private static final WorldId WORLD = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000102"),
            "minecraft:overworld"
    );

    private final EffectiveFlagTracker tracker = new EffectiveFlagTracker();

    @Test
    void capturesEveryProtectionFlagAndSuppressesInitialOrDuplicateEvents() {
        EffectiveFlagTracker.Observation observation = observationAt(5.0);

        assertEquals(
                ProtectionFlags.REGISTRY.values().size(),
                observation.resolutions().size()
        );
        assertTrue(tracker.changes(null, observation).isEmpty());
        assertTrue(tracker.changes(observation, observation).isEmpty());
    }

    @Test
    void contributionSourceChangeIsMaterialEvenWhenStatusAndValueMatch() {
        EffectiveFlagTracker.Observation first = observationAt(5.0);
        EffectiveFlagTracker.Observation second = observationAt(25.0);

        EffectiveFlagTracker.Change<?> pvp = tracker.changes(first, second).stream()
                .filter(change -> change.current().flag().equals(ProtectionFlags.PVP))
                .findFirst()
                .orElseThrow();

        assertEquals(pvp.previous().status(), pvp.current().status());
        assertEquals(pvp.previous().value(), pvp.current().value());
        assertEquals(State.ALLOW, pvp.current().value().orElseThrow());
        assertFalse(pvp.previous().contributions().equals(
                pvp.current().contributions()
        ));
    }

    private static EffectiveFlagTracker.Observation observationAt(double x) {
        Region global = Region.builder(
                RegionKey.global(WORLD), GlobalShape.INSTANCE
        ).build();
        Region first = explicitAllow("first", 0, 10);
        Region second = explicitAllow("second", 20, 30);
        RegionSnapshot snapshot = RegionSnapshot.of(
                1L, List.of(global, first, second)
        );
        return new EffectiveFlagTracker().capture(
                new RegionQuery(snapshot).at(WORLD, x, 5, 5),
                Subject.none(),
                ProtectionFlags.REGISTRY.values()
        );
    }

    private static Region explicitAllow(String id, double min, double max) {
        return Region.builder(
                        new RegionKey(WORLD, id),
                        new CuboidShape(min, 0, 0, max, 10, 10)
                )
                .parent(RegionKey.global(WORLD))
                .flag(ProtectionFlags.PVP, State.ALLOW)
                .build();
    }
}
