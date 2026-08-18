package io.github.rookiecuzz.rookieregions.rule;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionGraph;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Actor-aware flag resolver with global fallback and ownership defaults. */
public final class RuleResolver {
    private final RegionGraph graph;

    public RuleResolver(RegionGraph graph) {
        this.graph = Objects.requireNonNull(graph, "region graph cannot be null");
    }

    public <T> RuleResolution<T> resolve(Flag<T> flag,
                                         WorldId world,
                                         Collection<RegionKey> applicable,
                                         Subject subject) {
        Objects.requireNonNull(flag, "flag cannot be null");
        Objects.requireNonNull(world, "world cannot be null");
        Subject actor = subject == null ? Subject.none() : subject;
        if(!flag.actorScope().accepts(actor)){
            return new RuleResolution<>(
                    flag, ResolutionStatus.UNSET, null, List.of()
            );
        }
        Region global = graph.global(world).orElse(null);
        List<Region> leaves = graph.applicableLeaves(applicable);
        ArrayList<Candidate<T>> candidates = new ArrayList<>();
        if(leaves.isEmpty()){
            Candidate<T> wilderness = resolveBranch(flag, null, global, actor);
            if(wilderness != null){
                candidates.add(wilderness);
            }
        } else {
            for(Region leaf : leaves){
                Candidate<T> candidate = resolveBranch(flag, leaf, global, actor);
                if(candidate != null){
                    candidates.add(candidate);
                }
            }
        }
        return combine(flag, candidates);
    }

    /**
     * Resolves {@code primary} independently for every applicable leaf and
     * falls back to {@code fallback} only on branches where the primary flag
     * is unset. The resulting branch values then participate in the normal
     * leaf-priority and conflict calculation using the primary flag's
     * conflict strategy.
     */
    public <T> RuleResolution<T> resolveWithFallback(
            Flag<T> primary,
            Flag<T> fallback,
            WorldId world,
            Collection<RegionKey> applicable,
            Subject subject) {
        Objects.requireNonNull(primary, "primary flag cannot be null");
        Objects.requireNonNull(fallback, "fallback flag cannot be null");
        Objects.requireNonNull(world, "world cannot be null");
        Subject actor = subject == null ? Subject.none() : subject;
        if(!primary.actorScope().accepts(actor)){
            return new RuleResolution<>(
                    primary, ResolutionStatus.UNSET, null, List.of()
            );
        }
        Region global = graph.global(world).orElse(null);
        List<Region> leaves = graph.applicableLeaves(applicable);
        ArrayList<Candidate<T>> candidates = new ArrayList<>();
        if(leaves.isEmpty()){
            Candidate<T> wilderness = resolveBranch(primary, null, global, actor);
            if(wilderness == null && fallback.actorScope().accepts(actor)){
                wilderness = resolveBranch(fallback, null, global, actor);
            }
            if(wilderness != null){
                candidates.add(wilderness);
            }
        } else {
            for(Region leaf : leaves){
                Candidate<T> candidate = resolveBranch(primary, leaf, global, actor);
                if(candidate == null && fallback.actorScope().accepts(actor)){
                    candidate = resolveBranch(fallback, leaf, global, actor);
                }
                if(candidate != null){
                    candidates.add(candidate);
                }
            }
        }
        return combine(primary, candidates);
    }

    /** Resolves a rule as if the specified region were the sole applicable leaf. */
    public <T> RuleResolution<T> resolveForRegion(Flag<T> flag,
                                                  RegionKey key,
                                                  Subject subject) {
        Region region = graph.region(key).orElseThrow(() ->
                new IllegalArgumentException("unknown region: " + key));
        Subject actor = subject == null ? Subject.none() : subject;
        if(!flag.actorScope().accepts(actor)){
            return new RuleResolution<>(
                    flag, ResolutionStatus.UNSET, null, List.of()
            );
        }
        Region global = graph.global(key.world()).orElse(null);
        Candidate<T> candidate = resolveBranch(
                flag,
                region.key().isGlobal() ? null : region,
                region.key().isGlobal() ? region : global,
                actor
        );
        return combine(flag, candidate == null ? List.of() : List.of(candidate));
    }

    public Association association(Region leaf, Subject subject) {
        if(leaf == null || subject == null){
            return Association.NON_MEMBER;
        }
        if(matches(leaf.owners(), subject)){
            return Association.OWNER;
        }
        for(Region ancestor : graph.ancestors(leaf.key())){
            if(matches(ancestor.owners(), subject)){
                return Association.OWNER;
            }
        }
        return matches(leaf.members(), subject)
                ? Association.MEMBER
                : Association.NON_MEMBER;
    }

    private <T> Candidate<T> resolveBranch(Flag<T> flag,
                                           Region leaf,
                                           Region global,
                                           Subject subject) {
        Association association = association(
                leaf == null ? global : leaf,
                subject
        );
        if(leaf != null){
            Region source = firstExplicit(flag, leaf);
            if(source != null){
                return new Candidate<>(
                        leaf,
                        source,
                        source.flag(flag).orElseThrow().value(),
                        ValueOrigin.LOCAL_EXPLICIT,
                        association,
                        leaf.priority()
                );
            }
        }
        if(global != null
                && (leaf == null || flag.inheritance() != InheritanceMode.LOCAL_ONLY)
                && flag.scope().accepts(global)){
            Optional<FlagValue<T>> globalValue = global.flag(flag);
            if(globalValue.isPresent()){
                return new Candidate<>(
                        leaf,
                        global,
                        globalValue.get().value(),
                        ValueOrigin.GLOBAL_EXPLICIT,
                        association,
                        leaf == null ? Integer.MIN_VALUE : leaf.priority()
                );
            }
        }
        Optional<T> defaultValue = flag.defaultProvider().value(
                new DefaultContext(leaf, global, subject, association)
        );
        return defaultValue.map(value -> new Candidate<>(
                leaf,
                null,
                flag.value(value).value(),
                ValueOrigin.DEFAULT,
                association,
                leaf == null ? Integer.MIN_VALUE : leaf.priority()
        )).orElse(null);
    }

    private <T> Region firstExplicit(Flag<T> flag, Region leaf) {
        if(flag.scope().accepts(leaf) && leaf.flag(flag).isPresent()){
            return leaf;
        }
        if(flag.inheritance() == InheritanceMode.LOCAL_ONLY){
            return null;
        }
        for(Region ancestor : graph.ancestors(leaf.key())){
            if(ancestor.key().isGlobal()){
                break;
            }
            if(flag.scope().accepts(ancestor) && ancestor.flag(flag).isPresent()){
                return ancestor;
            }
        }
        return null;
    }

    private static boolean matches(io.github.rookiecuzz.rookieregions.core.RegionDomain domain,
                                   Subject subject) {
        return domain.contains(subject.playerId(), subject.groups());
    }

    private static <T> RuleResolution<T> combine(Flag<T> flag,
                                                  List<Candidate<T>> candidates) {
        if(candidates.isEmpty()){
            return new RuleResolution<>(flag, ResolutionStatus.UNSET, null, List.of());
        }
        int priority = candidates.stream()
                .mapToInt(Candidate::priority)
                .max()
                .orElse(Integer.MIN_VALUE);
        List<Candidate<T>> winners = candidates.stream()
                .filter(candidate -> candidate.priority() == priority)
                .sorted((first, second) -> compareLeaves(first.leaf(), second.leaf()))
                .toList();
        List<RuleContribution<T>> contributions = winners.stream()
                .map(Candidate::contribution)
                .toList();
        if(winners.size() == 1){
            return new RuleResolution<>(
                    flag,
                    ResolutionStatus.SINGLE,
                    winners.getFirst().value(),
                    contributions
            );
        }
        Set<T> distinct = new LinkedHashSet<>();
        winners.forEach(candidate -> distinct.add(candidate.value()));
        if(distinct.size() == 1){
            return new RuleResolution<>(
                    flag,
                    ResolutionStatus.AGREEMENT,
                    winners.getFirst().value(),
                    contributions
            );
        }
        Optional<T> resolved = flag.conflictStrategy().resolve(List.copyOf(distinct));
        return resolved
                .map(value -> new RuleResolution<>(
                        flag,
                        ResolutionStatus.RESOLVED_CONFLICT,
                        flag.value(value).value(),
                        contributions
                ))
                .orElseGet(() -> new RuleResolution<>(
                        flag,
                        ResolutionStatus.CONFLICT,
                        null,
                        contributions
                ));
    }

    private static int compareLeaves(Region first, Region second) {
        if(first == null){
            return second == null ? 0 : -1;
        }
        return second == null ? 1 : first.key().compareTo(second.key());
    }

    private record Candidate<T>(
            Region leaf,
            Region source,
            T value,
            ValueOrigin origin,
            Association association,
            int priority
    ) {
        private RuleContribution<T> contribution() {
            return new RuleContribution<>(
                    leaf == null ? null : leaf.key(),
                    source == null ? null : source.key(),
                    value,
                    origin,
                    association,
                    priority
            );
        }
    }
}
