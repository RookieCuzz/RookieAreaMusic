package io.github.rookiecuzz.rookieregions.module.music;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionMusicResolverTest {
    private final RegionMusicResolver resolver = new RegionMusicResolver();

    @Test
    void appliesAddAndReplaceFromGlobalThroughLeaf(){
        List<RegionMusicRecord> records = Arrays.asList(
                record("global", null, MusicPolicyMode.ADD, 0, "global"),
                record("parent", "global", MusicPolicyMode.ADD, 10, "parent"),
                record("leaf", "parent", MusicPolicyMode.REPLACE, 20, "leaf")
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(Collections.singletonList("leaf"), layerKeys(resolved));
        assertEquals("leaf", resolved.getLayers().get(0).getTracks().get(0).getId());
    }

    @Test
    void inheritIsAnExplicitNoOpThatKeepsAncestorLayers(){
        RegionMusicRecord inherit = policyRecord(
                "leaf", "global",
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.INHERIT)
                        .order(100)
                        .build()
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                Arrays.asList(
                        record("global", null, MusicPolicyMode.ADD, 0, "global"),
                        inherit
                ),
                definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(Collections.singletonList("global"), layerKeys(resolved));
    }

    @Test
    void unrelatedBranchesUseOrderBeforeExclusiveOrLayeredSelection(){
        List<RegionMusicRecord> records = Arrays.asList(
                record("global", null, MusicPolicyMode.ADD, 0, "global"),
                record("low", "global", MusicPolicyMode.ADD, 10, "low"),
                record("high", "global", MusicPolicyMode.ADD, 30, "high")
        );

        ResolvedMusicChannel exclusive = resolver.resolve(
                records, definitions(MusicChannelDefinition.exclusive("bgm"))
        ).getChannel("bgm");
        ResolvedMusicChannel layered = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 2))
        ).getChannel("bgm");

        assertEquals(Collections.singletonList("high"), layerKeys(exclusive));
        assertEquals(Arrays.asList("high", "low"), layerKeys(layered));
    }

    @Test
    void unrelatedReplaceOnlyClearsItsOwnParentChain(){
        List<RegionMusicRecord> records = Arrays.asList(
                record("low", null, MusicPolicyMode.ADD, 5, "low"),
                record("high", null, MusicPolicyMode.REPLACE, 50, "high")
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(Arrays.asList("high", "low"), layerKeys(resolved));
    }

    @Test
    void descendantCanResumeItsChainBelowAncestorBlockOrder(){
        RegionMusicRecord blocker = policyRecord(
                "blocker", "global",
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.BLOCK)
                        .order(50)
                        .build()
        );
        List<RegionMusicRecord> records = Arrays.asList(
                record("global", null, MusicPolicyMode.ADD, 0, "global"),
                blocker,
                record("low-child", "blocker", MusicPolicyMode.ADD, 10, "low")
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(Collections.singletonList("low-child"), layerKeys(resolved));
        assertFalse(resolved.isBlocked());
        assertNull(resolved.getBlockingOrder());
    }

    @Test
    void blockSuppressesLowerOrderResultsFromUnrelatedBranches(){
        RegionMusicRecord blocker = policyRecord(
                "blocker", null,
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.BLOCK)
                        .order(50)
                        .build()
        );
        List<RegionMusicRecord> records = Arrays.asList(
                record("low", null, MusicPolicyMode.ADD, 10, "low"),
                blocker
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertTrue(resolved.getLayers().isEmpty());
        assertTrue(resolved.isBlocked());
        assertEquals(Integer.valueOf(50), resolved.getBlockingOrder());
    }

    @Test
    void lowerOrderReplaceDoesNotClearHigherOrderUnrelatedLayer(){
        List<RegionMusicRecord> records = Arrays.asList(
                record("parent", null, MusicPolicyMode.INHERIT, 100, null),
                record("late-low", "parent", MusicPolicyMode.REPLACE, 10, "low"),
                record("unrelated-high", null, MusicPolicyMode.ADD, 50, "high")
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(Arrays.asList("unrelated-high", "late-low"), layerKeys(resolved));
    }

    @Test
    void descendantContributionClearsEffectiveBlockMarker(){
        RegionMusicRecord blocker = policyRecord(
                "blocker", null,
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.BLOCK)
                        .order(50)
                        .build()
        );
        List<RegionMusicRecord> records = Arrays.asList(
                blocker,
                record("high-child", "blocker", MusicPolicyMode.ADD, 60, "high")
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(Collections.singletonList("high-child"), layerKeys(resolved));
        assertFalse(resolved.isBlocked());
        assertNull(resolved.getBlockingOrder());
    }

    @Test
    void siblingBlockDoesNotDestroyHigherOrderSharedAncestorLayer(){
        RegionMusicRecord blocker = policyRecord(
                "blocked-child", "parent",
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.BLOCK)
                        .order(10)
                        .build()
        );
        List<RegionMusicRecord> records = Arrays.asList(
                record("parent", null, MusicPolicyMode.ADD, 100, "parent"),
                blocker,
                record("audible-child", "parent", MusicPolicyMode.ADD, 20, "child")
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(Arrays.asList("parent", "audible-child"), layerKeys(resolved));
        assertFalse(resolved.isBlocked());
        assertEquals(Integer.valueOf(10), resolved.getBlockingOrder());
    }

    @Test
    void siblingReplaceDoesNotDestroySharedAncestorLayer(){
        List<RegionMusicRecord> records = Arrays.asList(
                record("parent", null, MusicPolicyMode.ADD, 100, "parent"),
                record("replacement", "parent", MusicPolicyMode.REPLACE, 10, "replacement"),
                record("audible-child", "parent", MusicPolicyMode.ADD, 20, "child")
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(
                Arrays.asList("parent", "audible-child", "replacement"),
                layerKeys(resolved)
        );
        assertEquals(1L, layerKeys(resolved).stream()
                .filter("parent"::equals)
                .count());
    }

    @Test
    void unrelatedReplaceIsIndependentOfParentTopologyTiming(){
        List<RegionMusicRecord> records = Arrays.asList(
                record("high-replace", null, MusicPolicyMode.REPLACE, 50, "high"),
                record("gate", null, MusicPolicyMode.ADD, 100, "gate"),
                record("late-low", "gate", MusicPolicyMode.ADD, 10, "low")
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                records, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(
                Arrays.asList("gate", "high-replace", "late-low"),
                layerKeys(resolved)
        );
    }

    @Test
    void explicitInheritIsExactlyEquivalentToMissingConfiguration(){
        RegionMusicRecord explicitGate = policyRecord(
                "gate", null,
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.INHERIT)
                        .order(100)
                        .build()
        );
        List<RegionMusicRecord> explicit = Arrays.asList(
                record("high-replace", null, MusicPolicyMode.REPLACE, 50, "high"),
                explicitGate,
                record("late-low", "gate", MusicPolicyMode.ADD, 10, "low")
        );
        List<RegionMusicRecord> missing = Arrays.asList(
                record("high-replace", null, MusicPolicyMode.REPLACE, 50, "high"),
                emptyRecord("gate", null),
                record("late-low", "gate", MusicPolicyMode.ADD, 10, "low")
        );

        ResolvedMusicChannel explicitResult = resolver.resolve(
                explicit, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");
        ResolvedMusicChannel missingResult = resolver.resolve(
                missing, definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertEquals(layerKeys(missingResult), layerKeys(explicitResult));
        assertEquals(missingResult.isBlocked(), explicitResult.isBlocked());
        assertEquals(
                missingResult.getBlockingOrder(), explicitResult.getBlockingOrder()
        );
        assertEquals(Arrays.asList("high-replace", "late-low"), layerKeys(explicitResult));
    }

    @Test
    void innerBlockSilencesOuterBirdsong(){
        RegionMusicRecord quietInner = policyRecord(
                "quiet-inner", "bird-outer",
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.BLOCK)
                        .order(50)
                        .build()
        );

        ResolvedMusicChannel resolved = resolver.resolve(
                Arrays.asList(
                        record("bird-outer", null, MusicPolicyMode.ADD, 0, "birds"),
                        quietInner
                ),
                definitions(MusicChannelDefinition.layered("bgm", 8))
        ).getChannel("bgm");

        assertTrue(resolved.getLayers().isEmpty());
        assertTrue(resolved.isBlocked());
        assertEquals(Integer.valueOf(50), resolved.getBlockingOrder());
    }

    @Test
    void preservesEveryPlaybackSettingOnResolvedLayer(){
        RegionMusicChannel policy = RegionMusicChannel.builder()
                .policy(MusicPolicyMode.ADD)
                .order(7)
                .random(true)
                .loop(false)
                .volume(0.25f)
                .pitch(1.25f)
                .overwrite(false)
                .tracks(Collections.singletonList(track("theme")))
                .build();

        ResolvedMusicLayer layer = resolver.resolve(
                Collections.singletonList(policyRecord("region", null, policy)),
                definitions(MusicChannelDefinition.exclusive("bgm"))
        ).getChannel("bgm").getLayers().get(0);

        assertEquals(7, layer.getOrder());
        assertTrue(layer.isRandom());
        assertFalse(layer.isLoop());
        assertEquals(0.25f, layer.getVolume());
        assertEquals(1.25f, layer.getPitch());
        assertFalse(layer.isOverwrite());
    }

    @Test
    void rejectsIncompleteAncestryCyclesAndUnknownChannels(){
        RegionMusicRecord missing = record(
                "leaf", "missing", MusicPolicyMode.ADD, 1, "leaf"
        );
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                Collections.singletonList(missing),
                definitions(MusicChannelDefinition.exclusive("bgm"))
        ));

        List<RegionMusicRecord> cycle = Arrays.asList(
                record("one", "two", MusicPolicyMode.ADD, 1, "one"),
                record("two", "one", MusicPolicyMode.ADD, 2, "two")
        );
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                cycle, definitions(MusicChannelDefinition.exclusive("bgm"))
        ));

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                Collections.singletonList(record(
                        "one", null, MusicPolicyMode.ADD, 1, "one"
                )),
                definitions(MusicChannelDefinition.exclusive("other"))
        ));
    }

    private RegionMusicRecord record(String key,
                                     String parent,
                                     MusicPolicyMode mode,
                                     int order,
                                     String track){
        RegionMusicChannel.Builder builder = RegionMusicChannel.builder()
                .policy(mode)
                .order(order);
        if(mode == MusicPolicyMode.ADD || mode == MusicPolicyMode.REPLACE){
            builder.tracks(Collections.singletonList(track(track)));
        }
        return policyRecord(key, parent, builder.build());
    }

    private RegionMusicRecord policyRecord(String key,
                                           String parent,
                                           RegionMusicChannel policy){
        Map<String, RegionMusicChannel> channels = new LinkedHashMap<>();
        channels.put("bgm", policy);
        return new RegionMusicRecord(
                key, parent, new RegionMusicProfile(channels)
        );
    }

    private RegionMusicRecord emptyRecord(String key, String parent){
        return new RegionMusicRecord(key, parent, RegionMusicProfile.empty());
    }

    private MusicTrack track(String id){
        return new MusicTrack(id, "rookie." + id, 60L);
    }

    private Map<String, MusicChannelDefinition> definitions(
            MusicChannelDefinition definition){
        Map<String, MusicChannelDefinition> result = new LinkedHashMap<>();
        result.put(definition.getName(), definition);
        return result;
    }

    private List<String> layerKeys(ResolvedMusicChannel channel){
        return channel.getLayers().stream()
                .map(ResolvedMusicLayer::getRegionKey)
                .collect(Collectors.toList());
    }
}
