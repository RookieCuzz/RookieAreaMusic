package io.github.rookiecuzz.rookieregions.module.music;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Pure music resolver for the complete applicable region ancestry.
 *
 * <p>Every applicable leaf is folded independently from its root to the leaf.
 * ADD appends to that chain, REPLACE clears only that chain, and BLOCK clears
 * only that chain. Missing policies and explicit INHERIT policies are identical
 * no-ops. The independently folded chains are then merged, shared ancestor
 * layers are de-duplicated, and only a BLOCK that remains effective at the end
 * of a chain suppresses lower-order contributions from unrelated branches.</p>
 */
public final class RegionMusicResolver {
    private static final Comparator<ResolvedMusicLayer> LAYER_ORDER =
            new Comparator<ResolvedMusicLayer>() {
                @Override
                public int compare(ResolvedMusicLayer first,
                                   ResolvedMusicLayer second) {
                    int order = Integer.compare(second.getOrder(), first.getOrder());
                    if(order != 0){
                        return order;
                    }
                    int depth = Integer.compare(second.getDepth(), first.getDepth());
                    if(depth != 0){
                        return depth;
                    }
                    return first.getRegionKey().compareTo(second.getRegionKey());
                }
            };

    public MusicResolution resolve(Collection<RegionMusicRecord> records,
                                   Map<String, MusicChannelDefinition> definitions){
        Map<String, MusicChannelDefinition> normalizedDefinitions =
                normalizeDefinitions(definitions);
        Map<String, RegionMusicRecord> nodes = normalizeRecords(records);
        validateKnownChannels(nodes, normalizedDefinitions);
        validateParents(nodes);
        Ancestry ancestry = buildAncestry(nodes);

        List<String> channelNames = new ArrayList<>(normalizedDefinitions.keySet());
        Collections.sort(channelNames);
        Map<String, ResolvedMusicChannel> resolved = new LinkedHashMap<>();
        for(String channel : channelNames){
            resolved.put(channel, resolveChannel(
                    channel,
                    normalizedDefinitions.get(channel),
                    nodes,
                    ancestry
            ));
        }
        return new MusicResolution(resolved);
    }

    private ResolvedMusicChannel resolveChannel(
            String channel,
            MusicChannelDefinition definition,
            Map<String, RegionMusicRecord> nodes,
            Ancestry ancestry) {
        Map<String, ResolvedMusicLayer> mergedLayers = new LinkedHashMap<>();
        Map<String, BlockMarker> activeBlockers = new LinkedHashMap<>();

        for(List<RegionMusicRecord> chain : ancestry.chains()){
            ChainResolution folded = foldChain(channel, chain, ancestry.depths());
            for(ResolvedMusicLayer layer : folded.layers()){
                // The same ancestor can survive through several leaf chains. Its
                // immutable region policy is identical, so retain one stable copy.
                mergedLayers.putIfAbsent(layer.getRegionKey(), layer);
            }
            if(folded.activeBlocker() != null){
                BlockMarker blocker = folded.activeBlocker();
                activeBlockers.putIfAbsent(blocker.regionKey(), blocker);
            }
        }

        List<ResolvedMusicLayer> accumulated = new ArrayList<>(mergedLayers.values());
        accumulated.removeIf(layer -> isSuppressedByActiveBlock(
                layer, activeBlockers.values(), nodes
        ));
        accumulated.sort(LAYER_ORDER);

        int selectedCount = definition.getPlaybackMode() == ChannelPlaybackMode.EXCLUSIVE
                ? Math.min(1, accumulated.size())
                : Math.min(definition.getMaxLayers(), accumulated.size());
        List<ResolvedMusicLayer> selected = new ArrayList<>(
                accumulated.subList(0, selectedCount)
        );
        Integer blockingOrder = activeBlockers.values().stream()
                .map(BlockMarker::order)
                .max(Integer::compareTo)
                .orElse(null);
        return new ResolvedMusicChannel(
                definition,
                selected,
                !activeBlockers.isEmpty() && selected.isEmpty(),
                blockingOrder
        );
    }

    private static ChainResolution foldChain(
            String channel,
            List<RegionMusicRecord> chain,
            Map<String, Integer> depths) {
        List<ResolvedMusicLayer> layers = new ArrayList<>();
        BlockMarker activeBlocker = null;

        for(RegionMusicRecord record : chain){
            RegionMusicChannel policy = record.getProfile().getChannel(channel);
            if(policy == null || policy.getPolicy() == MusicPolicyMode.INHERIT){
                continue;
            }

            switch(policy.getPolicy()){
                case ADD -> {
                    layers.add(layer(record, channel, policy, depths));
                    activeBlocker = null;
                }
                case REPLACE -> {
                    layers.clear();
                    layers.add(layer(record, channel, policy, depths));
                    activeBlocker = null;
                }
                case BLOCK -> {
                    layers.clear();
                    activeBlocker = new BlockMarker(
                            record.getRegionKey(), policy.getOrder()
                    );
                }
                case INHERIT -> throw new IllegalStateException(
                        "INHERIT must have been handled as a no-op"
                );
            }
        }
        return new ChainResolution(List.copyOf(layers), activeBlocker);
    }

    private static ResolvedMusicLayer layer(
            RegionMusicRecord record,
            String channel,
            RegionMusicChannel policy,
            Map<String, Integer> depths) {
        Integer depth = depths.get(record.getRegionKey());
        if(depth == null){
            throw new IllegalStateException(
                    "missing ancestry depth for " + record.getRegionKey()
            );
        }
        return new ResolvedMusicLayer(
                record.getRegionKey(), channel, depth, policy
        );
    }

    private static boolean isSuppressedByActiveBlock(
            ResolvedMusicLayer layer,
            Collection<BlockMarker> blockers,
            Map<String, RegionMusicRecord> nodes) {
        for(BlockMarker blocker : blockers){
            if(layer.getOrder() < blocker.order()
                    && !isAncestor(
                            blocker.regionKey(), layer.getRegionKey(), nodes
                    )){
                return true;
            }
        }
        return false;
    }

    private static Ancestry buildAncestry(
            Map<String, RegionMusicRecord> nodes) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for(RegionMusicRecord record : nodes.values()){
            indegree.put(record.getRegionKey(), 0);
            children.put(record.getRegionKey(), new ArrayList<>());
        }
        for(RegionMusicRecord record : nodes.values()){
            if(record.getParentKey() != null){
                indegree.put(record.getRegionKey(), 1);
                children.get(record.getParentKey()).add(record.getRegionKey());
            }
        }
        for(List<String> childKeys : children.values()){
            Collections.sort(childKeys);
        }

        PriorityQueue<String> ready = new PriorityQueue<>();
        for(Map.Entry<String, Integer> entry : indegree.entrySet()){
            if(entry.getValue() == 0){
                ready.add(entry.getKey());
            }
        }
        Map<String, Integer> depths = new HashMap<>();
        int visited = 0;
        while(!ready.isEmpty()){
            String key = ready.remove();
            RegionMusicRecord record = nodes.get(key);
            int depth = record.getParentKey() == null
                    ? 0
                    : depths.get(record.getParentKey()) + 1;
            depths.put(key, depth);
            visited++;
            for(String child : children.get(key)){
                int remaining = indegree.get(child) - 1;
                indegree.put(child, remaining);
                if(remaining == 0){
                    ready.add(child);
                }
            }
        }
        if(visited != nodes.size()){
            throw new IllegalArgumentException(
                    "applicable music ancestry contains a cycle"
            );
        }

        List<String> leaves = nodes.keySet().stream()
                .filter(key -> children.get(key).isEmpty())
                .sorted()
                .toList();
        List<List<RegionMusicRecord>> chains = new ArrayList<>();
        for(String leaf : leaves){
            List<RegionMusicRecord> reversed = new ArrayList<>();
            RegionMusicRecord current = nodes.get(leaf);
            while(current != null){
                reversed.add(current);
                current = current.getParentKey() == null
                        ? null
                        : nodes.get(current.getParentKey());
            }
            Collections.reverse(reversed);
            chains.add(List.copyOf(reversed));
        }
        return new Ancestry(List.copyOf(chains), Map.copyOf(depths));
    }

    private static boolean isAncestor(String possibleAncestor,
                                      String descendant,
                                      Map<String, RegionMusicRecord> nodes) {
        String current = descendant;
        while(current != null){
            if(current.equals(possibleAncestor)){
                return true;
            }
            RegionMusicRecord record = nodes.get(current);
            current = record == null ? null : record.getParentKey();
        }
        return false;
    }

    private record BlockMarker(String regionKey, int order) {
    }

    private record ChainResolution(List<ResolvedMusicLayer> layers,
                                   BlockMarker activeBlocker) {
    }

    private record Ancestry(List<List<RegionMusicRecord>> chains,
                            Map<String, Integer> depths) {
    }

    private Map<String, RegionMusicRecord> normalizeRecords(
            Collection<RegionMusicRecord> records){
        Map<String, RegionMusicRecord> result = new LinkedHashMap<>();
        if(records == null){
            return result;
        }
        for(RegionMusicRecord record : records){
            if(record == null){
                throw new IllegalArgumentException("music records must not contain null");
            }
            if(result.put(record.getRegionKey(), record) != null){
                throw new IllegalArgumentException(
                        "duplicate applicable region key: " + record.getRegionKey()
                );
            }
        }
        return result;
    }

    private Map<String, MusicChannelDefinition> normalizeDefinitions(
            Map<String, MusicChannelDefinition> definitions){
        Map<String, MusicChannelDefinition> result = new LinkedHashMap<>();
        if(definitions == null){
            return result;
        }
        for(Map.Entry<String, MusicChannelDefinition> entry : definitions.entrySet()){
            String channel = RegionMusicProfile.requireKey(entry.getKey(), "channel name");
            MusicChannelDefinition definition = entry.getValue();
            if(definition == null){
                throw new IllegalArgumentException(
                        "channel definition must not be null: " + channel
                );
            }
            if(!channel.equals(definition.getName())){
                throw new IllegalArgumentException(
                        "channel definition key does not match its name: " + channel
                );
            }
            if(result.put(channel, definition) != null){
                throw new IllegalArgumentException(
                        "duplicate normalized channel definition: " + channel
                );
            }
        }
        return result;
    }

    private void validateKnownChannels(
            Map<String, RegionMusicRecord> nodes,
            Map<String, MusicChannelDefinition> definitions){
        for(RegionMusicRecord record : nodes.values()){
            for(String channel : record.getProfile().getChannels().keySet()){
                if(!definitions.containsKey(channel)){
                    throw new IllegalArgumentException(
                            "region " + record.getRegionKey()
                                    + " references unknown music channel " + channel
                    );
                }
            }
        }
    }

    private void validateParents(Map<String, RegionMusicRecord> nodes){
        for(RegionMusicRecord record : nodes.values()){
            String parent = record.getParentKey();
            if(parent != null && !nodes.containsKey(parent)){
                throw new IllegalArgumentException(
                        "applicable music ancestry is missing parent " + parent
                                + " for " + record.getRegionKey()
                );
            }
        }
    }
}
