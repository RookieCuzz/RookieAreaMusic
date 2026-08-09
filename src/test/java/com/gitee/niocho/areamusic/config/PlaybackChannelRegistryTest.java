package com.gitee.niocho.areamusic.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackChannelRegistryTest {
    @Test
    void providesTheThreeBuiltInChannels(){
        PlaybackChannelRegistry registry = PlaybackChannelRegistry.defaults();

        assertEquals(ChannelMode.EXCLUSIVE, registry.require("bgm").getMode());
        assertEquals(3, registry.require("ambience").getMaxLayers());
        assertEquals(ChannelTrigger.ENTER_ONCE, registry.require("stinger").getTrigger());
    }

    @Test
    void acceptsValidCustomChannel(){
        Map<String, PlaybackChannelConfig> values = baseChannels();
        values.put("dungeon.fx-2", channel(
                ChannelMode.ADDITIVE,
                4,
                ChannelTrigger.CONTINUOUS
        ));

        PlaybackChannelRegistry registry = PlaybackChannelRegistry.of(values);

        assertTrue(registry.contains("dungeon.fx-2"));
        assertEquals(4, registry.require("dungeon.fx-2").getMaxLayers());
    }

    @Test
    void rejectsInvalidNameMissingBgmAndInvalidLayerRules(){
        Map<String, PlaybackChannelConfig> invalidName = baseChannels();
        invalidName.put("Bad Channel", channel(
                ChannelMode.ADDITIVE,
                1,
                ChannelTrigger.CONTINUOUS
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaybackChannelRegistry.of(invalidName)
        );

        Map<String, PlaybackChannelConfig> noBgm = new LinkedHashMap<>();
        noBgm.put("ambience", channel(
                ChannelMode.ADDITIVE,
                1,
                ChannelTrigger.CONTINUOUS
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaybackChannelRegistry.of(noBgm)
        );

        Map<String, PlaybackChannelConfig> invalidExclusive = baseChannels();
        invalidExclusive.put("bgm", channel(
                ChannelMode.EXCLUSIVE,
                2,
                ChannelTrigger.CONTINUOUS
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaybackChannelRegistry.of(invalidExclusive)
        );
    }

    @Test
    void rejectsUnknownChannelAndInvalidEnums(){
        PlaybackChannelRegistry registry = PlaybackChannelRegistry.defaults();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.require("missing")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChannelMode.parse("mixed")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ChannelTrigger.parse("on_exit")
        );
    }

    private Map<String, PlaybackChannelConfig> baseChannels(){
        Map<String, PlaybackChannelConfig> values = new LinkedHashMap<>();
        values.put("bgm", channel(
                ChannelMode.EXCLUSIVE,
                1,
                ChannelTrigger.CONTINUOUS
        ));
        return values;
    }

    private PlaybackChannelConfig channel(ChannelMode mode,
                                          int maxLayers,
                                          ChannelTrigger trigger){
        return PlaybackChannelConfig.builder()
                .mode(mode)
                .maxLayers(maxLayers)
                .trigger(trigger)
                .build();
    }
}
