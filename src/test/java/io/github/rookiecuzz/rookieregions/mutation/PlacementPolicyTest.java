package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.Point2D;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.core.shape.SlicedPolygonShape;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementPolicyTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            "minecraft:overworld"
    );
    private final PlacementPolicy policy = new PlacementPolicy();

    @Test
    void ordinaryPlayerCanCreateDisjointOrTouchingRegionDirectly(){
        Region existing = box("existing", globalKey(), 0, 10);
        RegionSnapshot snapshot = snapshot(existing);

        assertEquals(PlanDisposition.DIRECT, createPlan(
                box("disjoint", globalKey(), 20, 30), snapshot, false
        ).disposition());
        assertEquals(PlanDisposition.DIRECT, createPlan(
                box("touch", globalKey(), 10, 20), snapshot, false
        ).disposition());
    }

    @Test
    void ordinaryPlayerCannotCreatePartialEqualOrContainingPeer(){
        Region existing = box("existing", globalKey(), 0, 10);
        RegionSnapshot snapshot = snapshot(existing);

        for(Region candidate : Arrays.asList(
                box("partial", globalKey(), 5, 15),
                box("equal", globalKey(), 0, 10),
                box("contains", globalKey(), -5, 15)
        )){
            PlacementPlan result = createPlan(candidate, snapshot, false);
            assertEquals(PlanDisposition.REJECTED, result.disposition());
            assertEquals(RegionSaveRejection.PEER_OVERLAP,
                    result.rejection().orElseThrow());
        }
    }

    @Test
    void allowedStrictContainerRequiresExplicitSetParentConfirmation(){
        Region parent = allowedBox("parent", globalKey(), 0, 100);
        RegionSnapshot snapshot = snapshot(parent);

        PlacementPlan result = createPlan(
                box("child", globalKey(), 10, 20), snapshot, false
        );

        assertEquals(PlanDisposition.CONFIRMATION_REQUIRED,
                result.disposition());
        assertEquals(List.of(PlacementOption.setParent(parent.key())),
                result.options());
    }

    @Test
    void localAllowFlagIsRequiredAndIsNotInherited(){
        Region parent = box("parent", globalKey(), 0, 100);
        RegionSnapshot snapshot = snapshot(parent);

        PlacementPlan result = createPlan(
                box("child", globalKey(), 10, 20), snapshot, false
        );

        assertEquals(PlanDisposition.REJECTED, result.disposition());
        assertEquals(RegionSaveRejection.PARENT_NOT_ALLOWED,
                result.rejection().orElseThrow());
    }

    @Test
    void nestedChainChoosesDeepestAllowedParent(){
        Region outer = allowedBox("outer", globalKey(), 0, 100);
        Region inner = allowedBox("inner", outer.key(), 10, 90);
        RegionSnapshot snapshot = snapshot(outer, inner);

        PlacementPlan result = createPlan(
                box("child", globalKey(), 20, 30), snapshot, false
        );

        assertEquals(List.of(PlacementOption.setParent(inner.key())),
                result.options());
    }

    @Test
    void unrelatedContainingPeersAreAmbiguousForOrdinaryPlayer(){
        Region first = allowedBox("first", globalKey(), 0, 100);
        Region second = allowedBox("second", globalKey(), 0, 90);
        RegionSnapshot snapshot = snapshot(first, second);

        PlacementPlan result = createPlan(
                box("child", globalKey(), 10, 20), snapshot, false
        );

        assertEquals(PlanDisposition.REJECTED, result.disposition());
        assertEquals(RegionSaveRejection.AMBIGUOUS_PARENT,
                result.rejection().orElseThrow());
    }

    @Test
    void overlapAdministratorStillNeedsExplicitPositiveVolumeConfirmation(){
        Region existing = box("existing", globalKey(), 0, 10);
        RegionSnapshot snapshot = snapshot(existing);

        PlacementPlan result = createPlan(
                box("partial", globalKey(), 5, 15), snapshot, true
        );

        assertEquals(PlanDisposition.CONFIRMATION_REQUIRED,
                result.disposition());
        assertEquals(List.of(PlacementOption.keepOverlap()), result.options());
    }

    @Test
    void editRejectsNewPeerOverlapUnlessAdministratorConfirms(){
        Region parent = box("parent", globalKey(), 0, 100);
        Region target = box("target", parent.key(), 10, 40);
        Region child = box("child", target.key(), 15, 20);
        Region peer = box("peer", parent.key(), 50, 60);
        RegionSnapshot snapshot = snapshot(parent, target, child, peer);
        Region expanded = box("target", parent.key(), 10, 55);

        PlacementPlan ordinary = editPlan(expanded, snapshot, false);
        PlacementPlan administrator = editPlan(expanded, snapshot, true);

        assertEquals(RegionSaveRejection.PEER_OVERLAP,
                ordinary.rejection().orElseThrow());
        assertEquals(List.of(PlacementOption.keepOverlap()),
                administrator.options());
    }

    @Test
    void editCannotShrinkPastChildOrEscapeParent(){
        Region parent = box("parent", globalKey(), 0, 100);
        Region target = box("target", parent.key(), 10, 50);
        Region child = box("child", target.key(), 30, 40);
        RegionSnapshot snapshot = snapshot(parent, target, child);

        PlacementPlan losesChild = editPlan(
                box("target", parent.key(), 10, 35), snapshot, true
        );
        PlacementPlan escapesParent = editPlan(
                box("target", parent.key(), -5, 50), snapshot, true
        );

        assertEquals(RegionSaveRejection.CHILD_WOULD_ESCAPE,
                losesChild.rejection().orElseThrow());
        assertEquals(RegionSaveRejection.PARENT_NOT_CONTAINING,
                escapesParent.rejection().orElseThrow());
    }

    @Test
    void editAllowsAnExistingPeerOverlapWhenItsVolumeIsReduced(){
        Region parent = box("parent", globalKey(), 0, 100);
        Region target = box("target", parent.key(), 10, 40);
        Region peer = box("peer", parent.key(), 30, 50);
        RegionSnapshot snapshot = snapshot(parent, target, peer);

        PlacementPlan result = editPlan(
                box("target", parent.key(), 10, 35), snapshot, false
        );

        assertEquals(PlanDisposition.DIRECT, result.disposition());
    }

    @Test
    void editRequiresConfirmationWhenAnExistingPeerOverlapGetsLarger(){
        Region parent = box("parent", globalKey(), 0, 100);
        Region target = box("target", parent.key(), 10, 40);
        Region peer = box("peer", parent.key(), 30, 50);
        RegionSnapshot snapshot = snapshot(parent, target, peer);
        Region expanded = box("target", parent.key(), 10, 45);

        PlacementPlan ordinary = editPlan(expanded, snapshot, false);
        PlacementPlan administrator = editPlan(expanded, snapshot, true);

        assertEquals(RegionSaveRejection.PEER_OVERLAP,
                ordinary.rejection().orElseThrow());
        assertEquals(PlanDisposition.CONFIRMATION_REQUIRED,
                administrator.disposition());
    }

    @Test
    void worseningDetectionComparesCuboidPolygonAndSlicedShapes(){
        Region parent = box("parent", globalKey(), -10, 100);
        Region target = region(
                "target",
                parent.key(),
                new CuboidShape(0, 0, 0, 4, 10, 4)
        );
        Region peer = region(
                "peer",
                parent.key(),
                new SlicedPolygonShape(0, 10, List.of(
                        new SlicedPolygonShape.Slice(0, square(0, 0, 2, 2)),
                        new SlicedPolygonShape.Slice(5, square(3, 0, 5, 4))
                ))
        );
        RegionSnapshot snapshot = snapshot(parent, target, peer);
        Region expanded = region(
                "target",
                parent.key(),
                new PolygonPrismShape(0, 10, square(0, 0, 5, 5))
        );

        assertEquals(
                RegionSaveRejection.PEER_OVERLAP,
                editPlan(expanded, snapshot, false).rejection().orElseThrow()
        );
        assertEquals(
                PlanDisposition.CONFIRMATION_REQUIRED,
                editPlan(expanded, snapshot, true).disposition()
        );
    }

    private PlacementPlan createPlan(Region candidate,
                                     RegionSnapshot snapshot,
                                     boolean administrator){
        return policy.evaluate(
                SaveMode.CREATE,
                candidate,
                snapshot,
                new RegionQuery(snapshot).relations(candidate, null),
                administrator
        );
    }

    private PlacementPlan editPlan(Region candidate,
                                   RegionSnapshot snapshot,
                                   boolean administrator){
        return policy.evaluate(
                SaveMode.EDIT,
                candidate,
                snapshot,
                new RegionQuery(snapshot).relations(candidate, candidate.key()),
                administrator
        );
    }

    private RegionSnapshot snapshot(Region... locals){
        ArrayList<Region> regions = new ArrayList<>();
        regions.add(global());
        regions.addAll(Arrays.asList(locals));
        return RegionSnapshot.of(4L, regions);
    }

    private Region global(){
        return Region.builder(globalKey(), GlobalShape.INSTANCE).build();
    }

    private RegionKey globalKey(){
        return RegionKey.global(world);
    }

    private Region box(String id, RegionKey parent, double min, double max){
        return region(
                id,
                parent,
                new CuboidShape(min, min, min, max, max, max)
        );
    }

    private Region region(String id, RegionKey parent, RegionShape shape){
        return Region.builder(new RegionKey(world, id), shape)
                .parent(parent)
                .build();
    }

    private static List<Point2D> square(double minX,
                                        double minZ,
                                        double maxX,
                                        double maxZ){
        return List.of(
                new Point2D(minX, minZ),
                new Point2D(maxX, minZ),
                new Point2D(maxX, maxZ),
                new Point2D(minX, maxZ)
        );
    }

    private Region allowedBox(String id,
                              RegionKey parent,
                              double min,
                              double max){
        return Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(min, min, min, max, max, max)
                )
                .parent(parent)
                .flag(ProtectionFlags.ALLOW_PLAYER_REGIONS, State.ALLOW)
                .build();
    }
}
