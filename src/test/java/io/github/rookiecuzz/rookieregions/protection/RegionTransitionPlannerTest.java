package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.module.commands.CommandPhase;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTransitionPlannerTest {
    private static final WorldId WORLD = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            "minecraft:overworld"
    );
    private static final Region GLOBAL = Region.builder(
            RegionKey.global(WORLD), GlobalShape.INSTANCE
    ).build();

    private final RegionTransitionPlanner planner = new RegionTransitionPlanner();

    @Test
    void unchangedPhysicalKeysDoNotBecomeFreshEntersAfterReload() {
        Region original = region("claim", 0, 0, 10);
        RegionTransitionPlanner.Plan baseline = planner.plan(
                null,
                List.of(presence(original, 0, RegionCommandProfile.empty()))
        );
        Region reloaded = region("claim", 0, 0, 10);

        RegionTransitionPlanner.Plan reconcile = planner.plan(
                baseline.next(),
                List.of(presence(reloaded, 0, RegionCommandProfile.empty()))
        );

        assertTrue(reconcile.left().isEmpty());
        assertTrue(reconcile.entered().isEmpty());
        assertTrue(reconcile.commands().getActions().isEmpty());
    }

    @Test
    void removedRegionsLeaveDeepestFirstUsingTheirOldRecords() {
        Region parent = region("parent", 0, 0, 100);
        Region child = Region.builder(
                        new RegionKey(WORLD, "child"),
                        new CuboidShape(10, 10, 10, 20, 20, 20)
                )
                .parent(parent.key())
                .build();
        RegionCommandProfile parentCommands = new RegionCommandProfile(
                List.of(), List.of("parent-left")
        );
        RegionCommandProfile childCommands = new RegionCommandProfile(
                List.of(), List.of("child-left")
        );
        RegionTransitionPlanner.Plan inside = planner.plan(
                null,
                List.of(
                        presence(parent, 0, parentCommands),
                        presence(child, 1, childCommands)
                )
        );

        RegionTransitionPlanner.Plan removed = planner.plan(
                inside.next(), List.of()
        );

        assertEquals(List.of(child.key(), parent.key()), removed.left().stream()
                .map(value -> value.region().key())
                .toList());
        assertSame(child, removed.left().getFirst().region());
        assertEquals(List.of("child-left", "parent-left"),
                removed.commands().getActions().stream()
                        .map(action -> action.getCommand())
                        .toList());
        assertTrue(removed.commands().getActions().stream()
                .allMatch(action -> action.getPhase() == CommandPhase.LEAVE));
    }

    private static Region region(String id,
                                 double min,
                                 double minY,
                                 double max) {
        return Region.builder(
                        new RegionKey(WORLD, id),
                        new CuboidShape(min, minY, min, max, max, max)
                )
                .parent(GLOBAL.key())
                .build();
    }

    private static RegionTransitionPlanner.Presence presence(
            Region region,
            int depth,
            RegionCommandProfile commands) {
        return new RegionTransitionPlanner.Presence(region, depth, commands);
    }
}
