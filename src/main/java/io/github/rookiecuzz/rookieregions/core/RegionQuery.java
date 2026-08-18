package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.core.shape.Bounds3D;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.RuleResolution;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.BuildAction;
import io.github.rookiecuzz.rookieregions.rule.ProtectionRuleSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lock-free query facade bound to one immutable snapshot. */
public final class RegionQuery {
    private final RegionSnapshot snapshot;

    public RegionQuery(RegionSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "region snapshot cannot be null");
    }

    public RegionSnapshot snapshot() {
        return snapshot;
    }

    public ApplicableRegionSet at(WorldId world,
                                  double x,
                                  double y,
                                  double z) {
        Objects.requireNonNull(world, "query world cannot be null");
        ArrayList<Region> locals = new ArrayList<>();
        for(Region region : snapshot.index().pointCandidates(world, x, z)){
            if(region.shape().contains(x, y, z)){
                locals.add(region);
            }
        }
        return new ApplicableRegionSet(
                world,
                locals,
                snapshot.graph().global(world).orElse(null),
                snapshot.graph()
        );
    }

    public <T> RuleResolution<T> resolve(WorldId world,
                                         double x,
                                         double y,
                                         double z,
                                         Flag<T> flag,
                                         Subject subject) {
        return at(world, x, y, z).resolve(flag, subject);
    }

    /** Pure protection decision for listeners and non-Bukkit integrations. */
    public boolean allows(WorldId world,
                          double x,
                          double y,
                          double z,
                          Flag<State> flag,
                          Subject subject) {
        return resolve(world, x, y, z, flag, subject)
                .value().orElse(State.ALLOW) == State.ALLOW;
    }

    public boolean allowsBuild(WorldId world,
                               double x,
                               double y,
                               double z,
                               Subject subject,
                               BuildAction action) {
        return ProtectionRuleSet.resolveBuildAction(
                        at(world, x, y, z), subject, action
                )
                .value().orElse(State.ALLOW) == State.ALLOW;
    }

    public boolean allowsContainer(WorldId world,
                                   double x,
                                   double y,
                                   double z,
                                   Subject subject) {
        return ProtectionRuleSet.resolveContainer(
                        at(world, x, y, z), subject
                )
                .value().orElse(State.ALLOW) == State.ALLOW;
    }

    /** Returns non-disjoint relations; global is excluded from overlap checks. */
    public List<RegionRelation> relations(Region candidate, RegionKey excluded) {
        Objects.requireNonNull(candidate, "candidate region cannot be null");
        Bounds3D candidateBounds = candidate.shape().bounds();
        ArrayList<RegionRelation> result = new ArrayList<>();
        for(Region existing : snapshot.index().boundsCandidates(
                candidate.key().world(),
                candidateBounds
        )){
            if(existing.key().isGlobal()
                    || excluded != null && excluded.equals(existing.key())
                    || !candidateBounds.touchesOrIntersects(existing.shape().bounds())){
                continue;
            }
            ShapeRelation relation = candidate.shape().relationTo(existing.shape());
            if(relation != ShapeRelation.DISJOINT){
                result.add(new RegionRelation(existing, relation));
            }
        }
        result.sort((first, second) -> first.region().key()
                .compareTo(second.region().key()));
        return List.copyOf(result);
    }
}
