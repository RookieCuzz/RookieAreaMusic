package io.github.rookiecuzz.rookieregions.module.music;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionMusicModelTest {
    @Test
    void trackPoliciesEnforceTheirPlaylistContract(){
        assertThrows(IllegalArgumentException.class, () -> channel(
                MusicPolicyMode.ADD, Collections.<MusicTrack>emptyList()
        ));
        assertThrows(IllegalArgumentException.class, () -> channel(
                MusicPolicyMode.REPLACE, Collections.<MusicTrack>emptyList()
        ));
        assertThrows(IllegalArgumentException.class, () -> channel(
                MusicPolicyMode.INHERIT, Collections.singletonList(track("one"))
        ));
        assertThrows(IllegalArgumentException.class, () -> channel(
                MusicPolicyMode.BLOCK, Collections.singletonList(track("one"))
        ));

        assertTrue(channel(
                MusicPolicyMode.INHERIT, Collections.<MusicTrack>emptyList()
        ).getTracks().isEmpty());
        assertEquals(1, channel(
                MusicPolicyMode.ADD, Collections.singletonList(track("one"))
        ).getTracks().size());
    }

    @Test
    void profileAndPlaylistsAreDefensiveImmutableCopies(){
        List<MusicTrack> tracks = new ArrayList<>(
                Collections.singletonList(track("one"))
        );
        RegionMusicChannel policy = channel(MusicPolicyMode.ADD, tracks);
        Map<String, RegionMusicChannel> channels = new LinkedHashMap<>();
        channels.put("bgm", policy);
        RegionMusicProfile profile = new RegionMusicProfile(channels);

        tracks.add(track("two"));
        channels.clear();

        assertEquals(1, policy.getTracks().size());
        assertEquals(policy, profile.getChannel("bgm"));
        assertThrows(UnsupportedOperationException.class, () ->
                profile.getChannels().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                policy.getTracks().add(track("three")));
    }

    @Test
    void pureParserRequiresExplicitPolicyAndPreservesPlaybackSettings(){
        MusicModuleParser parser = new MusicModuleParser();
        Map<String, Object> rawPolicy = new LinkedHashMap<>();
        rawPolicy.put("policy", "replace");
        rawPolicy.put("order", 17);
        rawPolicy.put("random", true);
        rawPolicy.put("loop", false);
        rawPolicy.put("volume", 0.4d);
        rawPolicy.put("pitch", 1.5d);
        rawPolicy.put("overwrite", false);
        rawPolicy.put("tracks", Collections.singletonList(rawTrack()));
        Map<String, Object> channels = new LinkedHashMap<>();
        channels.put("bgm", rawPolicy);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("channels", channels);

        RegionMusicChannel parsed = parser.parseProfile(raw).getChannel("bgm");

        assertEquals(MusicPolicyMode.REPLACE, parsed.getPolicy());
        assertEquals(17, parsed.getOrder());
        assertTrue(parsed.isRandom());
        assertFalse(parsed.isLoop());
        assertEquals(0.4f, parsed.getVolume());
        assertEquals(1.5f, parsed.getPitch());
        assertFalse(parsed.isOverwrite());
        assertEquals("theme", parsed.getTracks().get(0).getId());

        rawPolicy.remove("policy");
        assertThrows(IllegalArgumentException.class, () -> parser.parseProfile(raw));
    }

    @Test
    void channelDefinitionParserSupportsExclusiveAndBoundedLayered(){
        MusicModuleParser parser = new MusicModuleParser();
        Map<String, Object> exclusive = new LinkedHashMap<>();
        exclusive.put("mode", "exclusive");
        Map<String, Object> layered = new LinkedHashMap<>();
        layered.put("mode", "layered");
        layered.put("maxLayers", 3);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("bgm", exclusive);
        raw.put("ambience", layered);

        Map<String, MusicChannelDefinition> parsed =
                parser.parseChannelDefinitions(raw);

        assertEquals(ChannelPlaybackMode.EXCLUSIVE,
                parsed.get("bgm").getPlaybackMode());
        assertEquals(1, parsed.get("bgm").getMaxLayers());
        assertEquals(ChannelPlaybackMode.LAYERED,
                parsed.get("ambience").getPlaybackMode());
        assertEquals(3, parsed.get("ambience").getMaxLayers());
        assertThrows(UnsupportedOperationException.class, parsed::clear);

        layered.remove("maxLayers");
        assertThrows(IllegalArgumentException.class, () ->
                parser.parseChannelDefinitions(raw));
    }

    @Test
    void duplicateTrackIdsAndInvalidPlaybackRangesAreRejected(){
        assertThrows(IllegalArgumentException.class, () ->
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.ADD)
                        .tracks(Arrays.asList(track("same"), track("same")))
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.ADD)
                        .volume(1.1f)
                        .tracks(Collections.singletonList(track("one")))
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.ADD)
                        .pitch(0.0f)
                        .tracks(Collections.singletonList(track("one")))
                        .build());
    }

    private RegionMusicChannel channel(MusicPolicyMode mode,
                                       List<MusicTrack> tracks){
        return RegionMusicChannel.builder()
                .policy(mode)
                .tracks(tracks)
                .build();
    }

    private MusicTrack track(String id){
        return new MusicTrack(id, "rookie." + id, 60L);
    }

    private Map<String, Object> rawTrack(){
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("id", "theme");
        track.put("sound", "rookie.theme");
        track.put("duration", 90L);
        return track;
    }
}
