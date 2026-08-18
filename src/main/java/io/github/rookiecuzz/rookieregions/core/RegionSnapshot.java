package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import io.github.rookiecuzz.rookieregions.runtime.ModuleBindingValidator;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** One fully validated immutable publication. */
public record RegionSnapshot(
        long revision,
        RegionGraph graph,
        RegionIndex index,
        Map<RegionKey, RegionRecord> records
) {
    public RegionSnapshot {
        if(revision < 0L){
            throw new IllegalArgumentException("snapshot revision cannot be negative");
        }
        if(graph == null || index == null){
            throw new IllegalArgumentException("snapshot graph and index cannot be null");
        }
        records = records == null ? Map.of() : Map.copyOf(records);
        ModuleBindingValidator.validate(records.values());
        if(graph.regions().size() != records.size()){
            throw new IllegalArgumentException(
                    "snapshot graph and record keys do not match"
            );
        }
        for(Map.Entry<RegionKey, RegionRecord> entry : records.entrySet()){
            if(!entry.getKey().equals(entry.getValue().region().key())
                    || graph.region(entry.getKey()).orElse(null)
                    != entry.getValue().region()){
                throw new IllegalArgumentException(
                        "snapshot graph and record disagree for " + entry.getKey()
                );
            }
        }
        // Never trust a separately supplied broad-phase index.
        index = RegionIndex.build(graph.regions());
    }

    public static RegionSnapshot empty() {
        return of(0L, ListSupport.emptyRegions());
    }

    public static RegionSnapshot of(long revision, Collection<Region> regions) {
        Collection<RegionRecord> records = regions.stream()
                .map(RegionRecord::coreOnly)
                .toList();
        return ofRecords(revision, records);
    }

    public static RegionSnapshot ofRecords(long revision,
                                           Collection<RegionRecord> source) {
        if(source == null){
            throw new IllegalArgumentException("region records cannot be null");
        }
        LinkedHashMap<RegionKey, RegionRecord> records = new LinkedHashMap<>();
        for(RegionRecord record : source){
            if(record == null){
                throw new IllegalArgumentException("region records cannot contain null");
            }
            if(records.putIfAbsent(record.region().key(), record) != null){
                throw new IllegalArgumentException(
                        "duplicate region record: " + record.region().key()
                );
            }
        }
        RegionGraph graph = RegionGraph.of(
                records.values().stream().map(RegionRecord::region).toList()
        );
        return new RegionSnapshot(
                revision,
                graph,
                RegionIndex.build(graph.regions()),
                records
        );
    }

    private static final class ListSupport {
        private static Collection<Region> emptyRegions() {
            return java.util.List.of();
        }
    }
}
