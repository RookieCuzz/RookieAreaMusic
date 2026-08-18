package io.github.rookiecuzz.rookieregions.config;

import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;

import java.util.Objects;
import java.util.TreeSet;

/** Cross-validates a fully staged snapshot against its staged runtime config. */
public final class RegionRuntimeValidator {
    public static void validate(RegionSnapshot snapshot,
                                RookieRegionsSettings settings) {
        Objects.requireNonNull(snapshot, "staged snapshot cannot be null");
        Objects.requireNonNull(settings, "staged settings cannot be null");
        for(RegionRecord record : snapshot.records().values()) {
            TreeSet<String> unknown = new TreeSet<>(
                    record.music().getChannels().keySet()
            );
            unknown.removeAll(settings.musicChannels().keySet());
            if(!unknown.isEmpty()) {
                throw new IllegalArgumentException(
                        "region " + record.region().key()
                                + " references unknown music channel(s): "
                                + unknown
                );
            }
        }
    }

    private RegionRuntimeValidator() {
    }
}
