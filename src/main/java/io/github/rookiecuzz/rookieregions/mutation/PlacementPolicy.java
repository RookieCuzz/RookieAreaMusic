package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionGraph;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionRelation;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelations;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Complete pure placement matrix for player and overlap-admin saves. */
public final class PlacementPolicy {
    public PlacementPlan evaluate(SaveMode mode,
                                  Region candidate,
                                  RegionSnapshot snapshot,
                                  List<RegionRelation> relations,
                                  boolean canConfirmOverlap){
        Objects.requireNonNull(mode, "save mode cannot be null");
        Objects.requireNonNull(candidate, "candidate cannot be null");
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Objects.requireNonNull(relations, "relations cannot be null");
        if(candidate.key().isGlobal()){
            return PlacementPlan.rejected(
                    RegionSaveRejection.INVALID_CANDIDATE,
                    "global regions are not editor-created regions",
                    List.of()
            );
        }
        return mode == SaveMode.CREATE
                ? evaluateCreate(candidate, snapshot.graph(), relations,
                        canConfirmOverlap)
                : evaluateEdit(candidate, snapshot.graph(), relations,
                        canConfirmOverlap);
    }

    private PlacementPlan evaluateCreate(Region candidate,
                                         RegionGraph graph,
                                         List<RegionRelation> relations,
                                         boolean canConfirmOverlap){
        List<RegionRelation> positive = positive(relations);
        if(positive.isEmpty()){
            return PlacementPlan.direct();
        }

        List<RegionRelation> containers = positive.stream()
                .filter(relation -> relation.relation() == ShapeRelation.INSIDE)
                .sorted(Comparator.comparing(relation -> relation.region().key()))
                .toList();

        if(canConfirmOverlap){
            ArrayList<PlacementOption> options = new ArrayList<>();
            for(RegionRelation container : containers){
                options.add(PlacementOption.setParent(container.region().key()));
            }
            options.add(PlacementOption.keepOverlap());
            return PlacementPlan.confirmation(options, positive);
        }

        if(containers.size() != positive.size()){
            return PlacementPlan.rejected(
                    RegionSaveRejection.PEER_OVERLAP,
                    "players cannot create partial, equal, or containing peer overlaps",
                    positive
            );
        }
        Region parent = mostSpecificContainer(containers, graph);
        if(parent == null){
            return PlacementPlan.rejected(
                    RegionSaveRejection.AMBIGUOUS_PARENT,
                    "the candidate is inside unrelated possible parents",
                    positive
            );
        }
        if(!locallyAllowsPlayerRegions(parent)){
            return PlacementPlan.rejected(
                    RegionSaveRejection.PARENT_NOT_ALLOWED,
                    "the containing region does not allow player child regions",
                    positive
            );
        }
        return PlacementPlan.confirmation(
                List.of(PlacementOption.setParent(parent.key())),
                positive
        );
    }

    private PlacementPlan evaluateEdit(Region candidate,
                                       RegionGraph graph,
                                       List<RegionRelation> relations,
                                       boolean canConfirmOverlap){
        Region current = graph.region(candidate.key()).orElse(null);
        if(current == null){
            return PlacementPlan.rejected(
                    RegionSaveRejection.REGION_NOT_FOUND,
                    "the edited region no longer exists",
                    List.of()
            );
        }
        if(!current.parent().equals(candidate.parent())){
            return PlacementPlan.rejected(
                    RegionSaveRejection.PARENT_CHANGED,
                    "editing cannot silently change the region parent",
                    List.of()
            );
        }
        Region parent = candidate.parent()
                .flatMap(graph::region)
                .orElse(null);
        if(parent == null
                || candidate.shape().relationTo(parent.shape()) != ShapeRelation.INSIDE){
            return PlacementPlan.rejected(
                    RegionSaveRejection.PARENT_NOT_CONTAINING,
                    "the edited region must remain strictly inside its parent",
                    List.of()
            );
        }
        for(Region child : graph.children(candidate.key())){
            if(child.shape().relationTo(candidate.shape()) != ShapeRelation.INSIDE){
                return PlacementPlan.rejected(
                        RegionSaveRejection.CHILD_WOULD_ESCAPE,
                        "editing would leave child " + child.key()
                                + " outside its parent",
                        List.of()
                );
            }
        }

        ArrayList<RegionRelation> newPeerConflicts = new ArrayList<>();
        for(RegionRelation relation : relations){
            if(!relation.relation().hasPositiveVolumeIntersection()){
                continue;
            }
            RegionKey other = relation.region().key();
            boolean family = graph.isAncestor(other, candidate.key())
                    || graph.isAncestor(candidate.key(), other);
            if(family){
                continue;
            }
            ShapeRelation previous = current.shape().relationTo(
                    relation.region().shape()
            );
            if(!previous.hasPositiveVolumeIntersection()
                    || ShapeRelations.positiveIntersectionVolume(
                            candidate.shape(), relation.region().shape()
                    ).compareTo(ShapeRelations.positiveIntersectionVolume(
                            current.shape(), relation.region().shape()
                    )) > 0){
                newPeerConflicts.add(relation);
            }
        }
        if(newPeerConflicts.isEmpty()){
            return PlacementPlan.direct();
        }
        if(canConfirmOverlap){
            return PlacementPlan.confirmation(
                    List.of(PlacementOption.keepOverlap()),
                    newPeerConflicts
            );
        }
        return PlacementPlan.rejected(
                RegionSaveRejection.PEER_OVERLAP,
                "editing introduces a new positive-volume peer overlap",
                newPeerConflicts
        );
    }

    private Region mostSpecificContainer(List<RegionRelation> containers,
                                         RegionGraph graph){
        Region result = null;
        for(RegionRelation candidate : containers){
            boolean belowEveryOther = true;
            for(RegionRelation other : containers){
                if(candidate == other){
                    continue;
                }
                if(!graph.isAncestor(
                        other.region().key(), candidate.region().key()
                )){
                    belowEveryOther = false;
                    break;
                }
            }
            if(belowEveryOther){
                if(result != null){
                    return null;
                }
                result = candidate.region();
            }
        }
        return result;
    }

    private boolean locallyAllowsPlayerRegions(Region parent){
        return parent.flag(ProtectionFlags.ALLOW_PLAYER_REGIONS)
                .map(value -> value.value() == State.ALLOW)
                .orElse(false);
    }

    private List<RegionRelation> positive(List<RegionRelation> relations){
        return relations.stream()
                .filter(relation -> relation.relation()
                        .hasPositiveVolumeIntersection())
                .sorted(Comparator.comparing(relation -> relation.region().key()))
                .toList();
    }
}
