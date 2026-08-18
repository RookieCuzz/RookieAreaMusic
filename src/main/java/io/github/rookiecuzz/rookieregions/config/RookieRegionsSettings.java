package io.github.rookiecuzz.rookieregions.config;

import io.github.rookiecuzz.rookieregions.module.music.ChannelPlaybackMode;
import io.github.rookiecuzz.rookieregions.module.music.MusicChannelDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record RookieRegionsSettings(
        Duration confirmationLifetime,
        long musicScanPeriodTicks,
        boolean playerCreationEnabled,
        boolean notifyDeniedActions,
        Map<String, MusicChannelDefinition> musicChannels
) {
    public RookieRegionsSettings {
        if (confirmationLifetime == null || confirmationLifetime.isZero()
                || confirmationLifetime.isNegative()) {
            throw new IllegalArgumentException("confirmation lifetime must be positive");
        }
        if (musicScanPeriodTicks <= 0L) {
            throw new IllegalArgumentException("music scan period must be positive");
        }
        if(musicChannels == null) {
            throw new IllegalArgumentException("music channels cannot be null");
        }
        LinkedHashMap<String, MusicChannelDefinition> canonicalChannels =
                new LinkedHashMap<>();
        for(Map.Entry<String, MusicChannelDefinition> entry
                : musicChannels.entrySet()) {
            if(entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "music channel name must not be blank"
                );
            }
            String name = entry.getKey().trim();
            MusicChannelDefinition definition = Objects.requireNonNull(
                    entry.getValue(), "music channel definition cannot be null"
            );
            if(!name.equals(definition.getName())) {
                throw new IllegalArgumentException(
                        "music channel key does not match its definition: " + name
                );
            }
            if(canonicalChannels.putIfAbsent(name, definition) != null) {
                throw new IllegalArgumentException(
                        "duplicate normalized music channel: " + name
                );
            }
        }
        musicChannels = Map.copyOf(canonicalChannels);
    }

    public static RookieRegionsSettings load(FileConfiguration config) {
        if (config.getInt("schemaVersion", -1) != 1) {
            throw new IllegalArgumentException("config schemaVersion must be 1");
        }
        int confirmationSeconds = config.getInt("editor.confirmationSeconds", 30);
        long scanPeriod = config.getLong("music.scanPeriodTicks", 20L);
        ConfigurationSection channels = config.getConfigurationSection("music.channels");
        if (channels == null || channels.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("music.channels must define at least one channel");
        }
        LinkedHashMap<String, MusicChannelDefinition> definitions = new LinkedHashMap<>();
        for (String rawName : channels.getKeys(false)) {
            String name = rawName.trim();
            if(name.isEmpty()) {
                throw new IllegalArgumentException(
                        "music channel name must not be blank"
                );
            }
            String path = "music.channels." + rawName;
            ChannelPlaybackMode mode;
            try {
                mode = ChannelPlaybackMode.valueOf(
                        config.getString(path + ".mode", "").trim().toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(path + ".mode is invalid", exception);
            }
            int maxLayers = config.getInt(path + ".maxLayers", mode == ChannelPlaybackMode.EXCLUSIVE ? 1 : -1);
            MusicChannelDefinition definition = new MusicChannelDefinition(
                    name, mode, maxLayers
            );
            if(definitions.putIfAbsent(name, definition) != null) {
                throw new IllegalArgumentException(
                        "duplicate normalized music channel: " + name
                );
            }
        }
        return new RookieRegionsSettings(
                Duration.ofSeconds(confirmationSeconds),
                scanPeriod,
                config.getBoolean("playerCreation.enabled", true),
                config.getBoolean("protection.notifyDeniedActions", true),
                definitions
        );
    }
}
