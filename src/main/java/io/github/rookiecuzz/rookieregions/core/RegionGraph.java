package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Validated immutable forest rooted at one global region per represented world. */
public final class RegionGraph {
    private final Map<RegionKey, Region> regions;
    private final Map<RegionKey, RegionKey> parents;
    private final Map<RegionKey, List<RegionKey>> children;
    private final Map<WorldId, RegionKey> globals;

    private RegionGraph(Map<RegionKey, Region> regions,
                        Map<RegionKey, RegionKey> parents,
                        Map<RegionKey, List<RegionKey>> children,
                        Map<WorldId, RegionKey> globals) {
        this.regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
        this.parents = Collections.unmodifiableMap(new LinkedHashMap<>(parents));
        LinkedHashMap<RegionKey, List<RegionKey>> frozenChildren = new LinkedHashMap<>();
        for(Map.Entry<RegionKey, List<RegionKey>> entry : children.entrySet()){
            frozenChildren.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.children = Collections.unmodifiableMap(frozenChildren);
        this.globals = Collections.unmodifiableMap(new LinkedHashMap<>(globals));
    }

    public static RegionGraph of(Collection<Region> source) {
        if(source == null){
            throw new IllegalArgumentException("regions cannot be null");
        }
        LinkedHashMap<RegionKey, Region> regions = new LinkedHashMap<>();
        LinkedHashSet<WorldId> worlds = new LinkedHashSet<>();
        for(Region region : source){
            if(region == null){
                throw new IllegalArgumentException("regions cannot contain null");
            }
            if(regions.putIfAbsent(region.key(), region) != null){
                throw error(
                        RegionGraphValidationException.Reason.DUPLICATE_KEY,
                        "duplicate region: " + region.key(),
                        List.of(region.key())
                );
            }
            worlds.add(region.key().world());
        }

        LinkedHashMap<WorldId, RegionKey> globals = new LinkedHashMap<>();
        for(WorldId world : worlds){
            RegionKey globalKey = RegionKey.global(world);
            Region global = regions.get(globalKey);
            if(global == null){
                throw error(
                        RegionGraphValidationException.Reason.MISSING_GLOBAL,
                        "missing global region for " + world,
                        List.of(globalKey)
                );
            }
            if(global.parent().isPresent() || global.shape() != GlobalShape.INSTANCE){
                throw error(
                        RegionGraphValidationException.Reason.INVALID_GLOBAL,
                        "global region must use GlobalShape and have no parent: " + globalKey,
                        List.of(globalKey)
                );
            }
            globals.put(world, globalKey);
        }

        LinkedHashMap<RegionKey, RegionKey> parents = new LinkedHashMap<>();
        LinkedHashMap<RegionKey, List<RegionKey>> children = new LinkedHashMap<>();
        for(Region region : regions.values()){
            if(region.key().isGlobal()){
                continue;
            }
            RegionKey parent = region.parent().orElseThrow(() -> error(
                    RegionGraphValidationException.Reason.MISSING_PARENT,
                    "non-global region has no parent: " + region.key(),
                    List.of(region.key())
            ));
            if(!parent.world().equals(region.key().world())){
                throw error(
                        RegionGraphValidationException.Reason.CROSS_WORLD_PARENT,
                        "cross-world parent for " + region.key() + ": " + parent,
                        List.of(region.key(), parent)
                );
            }
            if(parent.equals(region.key())){
                throw error(
                        RegionGraphValidationException.Reason.SELF_PARENT,
                        "region cannot parent itself: " + region.key(),
                        List.of(region.key())
                );
            }
            if(!regions.containsKey(parent)){
                throw error(
                        RegionGraphValidationException.Reason.MISSING_PARENT,
                        "missing parent " + parent + " for " + region.key(),
                        List.of(region.key(), parent)
                );
            }
            parents.put(region.key(), parent);
            children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(region.key());
        }
        for(List<RegionKey> childKeys : children.values()){
            childKeys.sort(RegionKey::compareTo);
        }

        validateAcyclic(regions.keySet(), parents);
        validateContainment(regions, parents);
        return new RegionGraph(regions, parents, children, globals);
    }

    public Collection<Region> regions() {
        return regions.values();
    }

    public Optional<Region> region(RegionKey key) {
        return Optional.ofNullable(regions.get(key));
    }

    public Optional<Region> global(WorldId world) {
        RegionKey key = globals.get(world);
        return key == null ? Optional.empty() : Optional.of(regions.get(key));
    }

    public Optional<Region> parent(RegionKey child) {
        RegionKey key = parents.get(child);
        return key == null ? Optional.empty() : Optional.of(regions.get(key));
    }

    public List<Region> children(RegionKey parent) {
        List<RegionKey> keys = children.get(parent);
        if(keys == null){
            return List.of();
        }
        return keys.stream().map(regions::get).toList();
    }

    /** Closest parent first, including global as the final ancestor. */
    public List<Region> ancestors(RegionKey child) {
        ArrayList<Region> result = new ArrayList<>();
        RegionKey current = parents.get(child);
        while(current != null){
            result.add(regions.get(current));
            current = parents.get(current);
        }
        return List.copyOf(result);
    }

    public boolean isAncestor(RegionKey possibleAncestor, RegionKey child) {
        RegionKey current = parents.get(child);
        while(current != null){
            if(current.equals(possibleAncestor)){
                return true;
            }
            current = parents.get(current);
        }
        return false;
    }

    /** Tests owner management rights locally or through any parent, including global. */
    public boolean hasInheritedOwner(RegionKey key,
                                     UUID player,
                                     Set<String> groups) {
        Region region = regions.get(key);
        if(region == null){
            return false;
        }
        if(region.owners().contains(player, groups)){
            return true;
        }
        for(Region ancestor : ancestors(key)){
            if(ancestor.owners().contains(player, groups)){
                return true;
            }
        }
        return false;
    }

    /** Global is never returned as a leaf. */
    public List<Region> applicableLeaves(Collection<RegionKey> applicable) {
        if(applicable == null || applicable.isEmpty()){
            return List.of();
        }
        LinkedHashSet<RegionKey> known = new LinkedHashSet<>();
        for(RegionKey key : applicable){
            if(key != null && !key.isGlobal() && regions.containsKey(key)){
                known.add(key);
            }
        }
        ArrayList<RegionKey> ordered = new ArrayList<>(known);
        ordered.sort(RegionKey::compareTo);
        ArrayList<Region> leaves = new ArrayList<>();
        for(RegionKey candidate : ordered){
            boolean ancestorOfApplicable = false;
            for(RegionKey other : ordered){
                if(!candidate.equals(other) && isAncestor(candidate, other)){
                    ancestorOfApplicable = true;
                    break;
                }
            }
            if(!ancestorOfApplicable){
                leaves.add(regions.get(candidate));
            }
        }
        return List.copyOf(leaves);
    }

    private static void validateContainment(Map<RegionKey, Region> regions,
                                            Map<RegionKey, RegionKey> parents) {
        for(Map.Entry<RegionKey, RegionKey> entry : parents.entrySet()){
            Region child = regions.get(entry.getKey());
            Region parent = regions.get(entry.getValue());
            if(child.shape().relationTo(parent.shape()) != ShapeRelation.INSIDE){
                throw error(
                        RegionGraphValidationException.Reason.NOT_INSIDE_PARENT,
                        child.key() + " is not inside parent " + parent.key(),
                        List.of(child.key(), parent.key())
                );
            }
        }
    }

    private static void validateAcyclic(Set<RegionKey> keys,
                                        Map<RegionKey, RegionKey> parents) {
        Set<RegionKey> complete = new LinkedHashSet<>();
        for(RegionKey start : keys){
            if(complete.contains(start)){
                continue;
            }
            ArrayList<RegionKey> path = new ArrayList<>();
            Map<RegionKey, Integer> positions = new HashMap<>();
            RegionKey current = start;
            while(current != null && !complete.contains(current)){
                Integer repeatedAt = positions.putIfAbsent(current, path.size());
                if(repeatedAt != null){
                    ArrayList<RegionKey> cycle = new ArrayList<>(
                            path.subList(repeatedAt, path.size())
                    );
                    cycle.add(current);
                    throw error(
                            RegionGraphValidationException.Reason.CYCLE,
                            "region parent cycle: " + cycle,
                            cycle
                    );
                }
                path.add(current);
                current = parents.get(current);
            }
            complete.addAll(path);
        }
    }

    private static RegionGraphValidationException error(
            RegionGraphValidationException.Reason reason,
            String message,
            List<RegionKey> path) {
        return new RegionGraphValidationException(reason, message, path);
    }
}
