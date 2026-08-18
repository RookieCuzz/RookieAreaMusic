package io.github.rookiecuzz.rookieregions.config;

import io.github.rookiecuzz.rookieregions.module.music.ChannelPlaybackMode;
import io.github.rookiecuzz.rookieregions.module.music.MusicChannelDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RookieRegionsSettingsTest {
    @Test
    void yamlChannelNamesAreCanonicalizedBeforeRuntimePublication() {
        YamlConfiguration yaml = baseConfig();
        yaml.set("music.channels. bgm .mode", "EXCLUSIVE");
        yaml.set("music.channels. bgm .maxLayers", 1);

        RookieRegionsSettings settings = RookieRegionsSettings.load(yaml);

        assertEquals(Map.of("bgm", MusicChannelDefinition.exclusive("bgm")),
                settings.musicChannels());
    }

    @Test
    void duplicateNormalizedChannelNamesAreRejectedDuringStaging() {
        LinkedHashMap<String, MusicChannelDefinition> channels =
                new LinkedHashMap<>();
        channels.put("bgm", MusicChannelDefinition.exclusive("bgm"));
        channels.put(" bgm ", MusicChannelDefinition.exclusive("bgm"));

        assertThrows(IllegalArgumentException.class, () ->
                new RookieRegionsSettings(
                        Duration.ofSeconds(30),
                        20L,
                        true,
                        true,
                        channels
                )
        );
    }

    private static YamlConfiguration baseConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schemaVersion", 1);
        yaml.set("editor.confirmationSeconds", 30);
        yaml.set("playerCreation.enabled", true);
        yaml.set("protection.notifyDeniedActions", true);
        yaml.set("music.scanPeriodTicks", 20);
        return yaml;
    }
}
