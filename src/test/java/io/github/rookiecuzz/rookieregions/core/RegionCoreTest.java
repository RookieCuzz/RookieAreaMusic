package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCoreTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "minecraft:overworld"
    );

    @Test
    void worldUuidIsIdentityAndRegionIdsAreCaseInsensitive(){
        WorldId renamed = new WorldId(world.uuid(), "custom:renamed");
        assertEquals(world, renamed);
        assertEquals(
                new RegionKey(world, "spawn.shop"),
                new RegionKey(renamed, "  SPAWN.Shop ")
        );
    }

    @Test
    void graphRequiresGlobalAndValidatesParentGeometry(){
        Region global = global();
        Region parent = box("parent", RegionKey.global(world), 0, 0, 100);
        Region child = box("child", parent.key(), 10, 10, 20);
        RegionGraph graph = RegionGraph.of(List.of(global, parent, child));

        assertEquals(global, graph.global(world).orElseThrow());
        assertEquals(parent, graph.parent(child.key()).orElseThrow());
        assertEquals(List.of(parent, global), graph.ancestors(child.key()));
        assertEquals(
                List.of(child),
                graph.applicableLeaves(List.of(global.key(), parent.key(), child.key()))
        );

        Region equalToParent = Region.builder(
                        new RegionKey(world, "equal"),
                        parent.shape()
                )
                .parent(parent.key())
                .build();
        RegionGraphValidationException equalError = assertThrows(
                RegionGraphValidationException.class,
                () -> RegionGraph.of(List.of(global, parent, equalToParent))
        );
        assertEquals(
                RegionGraphValidationException.Reason.NOT_INSIDE_PARENT,
                equalError.reason()
        );

        RegionGraphValidationException missingGlobal = assertThrows(
                RegionGraphValidationException.class,
                () -> RegionGraph.of(List.of(parent))
        );
        assertEquals(
                RegionGraphValidationException.Reason.MISSING_GLOBAL,
                missingGlobal.reason()
        );
    }

    @Test
    void graphRejectsCyclesBeforeImpossibleContainmentIsConsidered(){
        Region global = global();
        RegionKey aKey = new RegionKey(world, "a");
        RegionKey bKey = new RegionKey(world, "b");
        Region a = Region.builder(aKey, new CuboidShape(0, 0, 0, 10, 10, 10))
                .parent(bKey)
                .build();
        Region b = Region.builder(bKey, new CuboidShape(1, 1, 1, 9, 9, 9))
                .parent(aKey)
                .build();

        RegionGraphValidationException error = assertThrows(
                RegionGraphValidationException.class,
                () -> RegionGraph.of(List.of(global, a, b))
        );
        assertEquals(RegionGraphValidationException.Reason.CYCLE, error.reason());
    }

    @Test
    void queryKeepsGlobalSeparateAndReportsDeterministicRelations(){
        Region global = global();
        Region parent = box("parent", global.key(), 0, 0, 100);
        Region child = box("child", parent.key(), 10, 10, 20);
        RegionSnapshot snapshot = RegionSnapshot.of(1L, List.of(global, parent, child));
        RegionQuery query = new RegionQuery(snapshot);

        ApplicableRegionSet inside = query.at(world, 15, 15, 15);
        assertEquals(List.of(child, parent), inside.localRegions().stream()
                .sorted((first, second) -> first.key().compareTo(second.key()))
                .toList());
        assertEquals(global, inside.globalRegion().orElseThrow());
        assertEquals(List.of(child), inside.leaves());

        Region candidate = box("candidate", parent.key(), 0, 15, 25);
        List<RegionRelation> relations = query.relations(candidate, null);
        assertEquals(2, relations.size());
        assertTrue(relations.stream().anyMatch(relation ->
                relation.region().equals(parent)
                        && relation.relation() == ShapeRelation.INSIDE));
        assertTrue(relations.stream().anyMatch(relation ->
                relation.region().equals(child)
                        && relation.relation().hasPositiveVolumeIntersection()));

        ApplicableRegionSet wilderness = query.at(world, 500, 5, 500);
        assertTrue(wilderness.localRegions().isEmpty());
        assertFalse(wilderness.globalRegion().isEmpty());
    }

    private Region global(){
        return Region.builder(RegionKey.global(world), GlobalShape.INSTANCE).build();
    }

    private Region box(String id,
                       RegionKey parent,
                       int priority,
                       double min,
                       double max){
        return Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(min, min, min, max, max, max)
                )
                .parent(parent)
                .priority(priority)
                .build();
    }
}
