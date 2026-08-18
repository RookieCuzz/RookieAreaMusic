package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.core.ApplicableRegionSet;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.RuleResolution;
import io.github.rookiecuzz.rookieregions.rule.Subject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure capture and comparison of one subject's effective flag state. */
final class EffectiveFlagTracker {
    Observation capture(ApplicableRegionSet regions,
                        Subject subject,
                        Collection<Flag<?>> flags) {
        Objects.requireNonNull(regions, "applicable regions cannot be null");
        Objects.requireNonNull(subject, "flag subject cannot be null");
        Objects.requireNonNull(flags, "flag definitions cannot be null");

        LinkedHashMap<String, RuleResolution<?>> resolutions = new LinkedHashMap<>();
        for(Flag<?> flag : flags){
            if(flag == null){
                throw new IllegalArgumentException("flag definitions cannot contain null");
            }
            RuleResolution<?> resolution = resolve(regions, subject, flag);
            if(resolutions.putIfAbsent(flag.name(), resolution) != null){
                throw new IllegalArgumentException("duplicate flag definition " + flag.name());
            }
        }
        return new Observation(regions.world(), resolutions);
    }

    List<Change<?>> changes(Observation previous, Observation current) {
        Objects.requireNonNull(current, "current flag observation cannot be null");
        if(previous == null){
            return List.of();
        }
        ArrayList<Change<?>> changes = new ArrayList<>();
        for(Map.Entry<String, RuleResolution<?>> entry
                : current.resolutions().entrySet()){
            RuleResolution<?> before = previous.resolutions().get(entry.getKey());
            RuleResolution<?> after = entry.getValue();
            if(before != null && materiallyDifferent(before, after)){
                changes.add(change(before, after));
            }
        }
        return List.copyOf(changes);
    }

    private static boolean materiallyDifferent(RuleResolution<?> previous,
                                               RuleResolution<?> current) {
        return previous.status() != current.status()
                || !previous.value().equals(current.value())
                || !previous.contributions().equals(current.contributions());
    }

    private static <T> RuleResolution<T> resolve(ApplicableRegionSet regions,
                                                 Subject subject,
                                                 Flag<T> flag) {
        return regions.resolve(flag, subject);
    }

    @SuppressWarnings("unchecked")
    private static <T> Change<T> change(RuleResolution<?> previous,
                                        RuleResolution<?> current) {
        RuleResolution<T> typedPrevious = (RuleResolution<T>) previous;
        RuleResolution<T> typedCurrent = (RuleResolution<T>) current;
        if(!typedPrevious.flag().equals(typedCurrent.flag())){
            throw new IllegalArgumentException("effective flag definition changed");
        }
        return new Change<>(typedPrevious, typedCurrent);
    }

    record Observation(WorldId world,
                       Map<String, RuleResolution<?>> resolutions) {
        Observation {
            Objects.requireNonNull(world, "flag observation world cannot be null");
            Objects.requireNonNull(resolutions, "flag resolutions cannot be null");
            LinkedHashMap<String, RuleResolution<?>> copy = new LinkedHashMap<>();
            for(Map.Entry<String, RuleResolution<?>> entry : resolutions.entrySet()){
                String name = Objects.requireNonNull(
                        entry.getKey(), "flag resolution name cannot be null"
                );
                RuleResolution<?> resolution = Objects.requireNonNull(
                        entry.getValue(), "flag resolution cannot be null"
                );
                if(!name.equals(resolution.flag().name())){
                    throw new IllegalArgumentException(
                            "flag resolution key does not match its definition"
                    );
                }
                copy.put(name, resolution);
            }
            resolutions = Collections.unmodifiableMap(copy);
        }
    }

    record Change<T>(RuleResolution<T> previous,
                     RuleResolution<T> current) {
        Change {
            Objects.requireNonNull(previous, "previous flag resolution cannot be null");
            Objects.requireNonNull(current, "current flag resolution cannot be null");
            if(!previous.flag().equals(current.flag())){
                throw new IllegalArgumentException("effective flag definition changed");
            }
        }
    }
}
