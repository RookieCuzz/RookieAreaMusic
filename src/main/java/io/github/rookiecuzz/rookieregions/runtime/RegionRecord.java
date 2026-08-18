package io.github.rookiecuzz.rookieregions.runtime;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;

import java.util.Objects;

/** Atomic persistence/runtime aggregate. Core Region remains module-neutral. */
public record RegionRecord(
        Region region,
        RegionMusicProfile music,
        RegionCommandProfile commands
) {
    public RegionRecord {
        Objects.requireNonNull(region, "region cannot be null");
        music = music == null ? RegionMusicProfile.empty() : music;
        commands = commands == null ? RegionCommandProfile.empty() : commands;
    }

    public static RegionRecord coreOnly(Region region) {
        return new RegionRecord(
                region,
                RegionMusicProfile.empty(),
                RegionCommandProfile.empty()
        );
    }
}
