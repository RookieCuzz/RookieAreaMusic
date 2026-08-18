package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.rule.Association;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.RuleResolution;
import io.github.rookiecuzz.rookieregions.rule.RuleResolver;
import io.github.rookiecuzz.rookieregions.rule.Subject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Physical local matches plus the world's separate global fallback. */
public final class ApplicableRegionSet implements Iterable<Region> {
    private final WorldId world;
    private final List<Region> locals;
    private final Set<RegionKey> localKeys;
    private final Region global;
    private final RegionGraph graph;
    private final RuleResolver resolver;

    ApplicableRegionSet(WorldId world,
                        List<Region> locals,
                        Region global,
                        RegionGraph graph) {
        this.world = world;
        ArrayList<Region> sorted = new ArrayList<>(locals);
        sorted.sort((first, second) -> first.key().compareTo(second.key()));
        this.locals = List.copyOf(sorted);
        LinkedHashSet<RegionKey> keys = new LinkedHashSet<>();
        sorted.forEach(region -> keys.add(region.key()));
        this.localKeys = Set.copyOf(keys);
        this.global = global;
        this.graph = graph;
        this.resolver = new RuleResolver(graph);
    }

    public WorldId world() {
        return world;
    }

    public List<Region> localRegions() {
        return locals;
    }

    public Optional<Region> globalRegion() {
        return Optional.ofNullable(global);
    }

    public Set<RegionKey> localKeys() {
        return localKeys;
    }

    public List<Region> leaves() {
        return graph.applicableLeaves(localKeys);
    }

    public boolean containsLocal(RegionKey key) {
        return localKeys.contains(key);
    }

    public Association association(Region leaf, Subject subject) {
        return resolver.association(leaf, subject);
    }

    public <T> RuleResolution<T> resolve(Flag<T> flag, Subject subject) {
        return resolver.resolve(flag, world, localKeys, subject);
    }

    /** Resolves a primary flag with a per-leaf fallback before branch conflict. */
    public <T> RuleResolution<T> resolveWithFallback(Flag<T> primary,
                                                      Flag<T> fallback,
                                                      Subject subject) {
        return resolver.resolveWithFallback(
                primary, fallback, world, localKeys, subject
        );
    }

    /** Iteration intentionally covers physical local regions, never global. */
    @Override
    public Iterator<Region> iterator() {
        return locals.iterator();
    }
}
