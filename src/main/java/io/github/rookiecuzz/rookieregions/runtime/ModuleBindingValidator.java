package io.github.rookiecuzz.rookieregions.runtime;

import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.provider.RegionProviderIds;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Complete-publication validation for module profile geometry references. */
public final class ModuleBindingValidator {
    private ModuleBindingValidator() {
    }

    public static void validate(Collection<RegionRecord> source) {
        Objects.requireNonNull(source, "region records cannot be null");
        LinkedHashMap<RegionKey, RegionRecord> records = new LinkedHashMap<>();
        for(RegionRecord record : source) {
            Objects.requireNonNull(record, "region records cannot contain null");
            records.put(record.region().key(), record);
        }
        validateKind(records, ModuleKind.MUSIC);
        validateKind(records, ModuleKind.COMMANDS);
    }

    private static void validateKind(Map<RegionKey, RegionRecord> records,
                                     ModuleKind kind) {
        LinkedHashMap<Target, RegionKey> claimed = new LinkedHashMap<>();
        for(RegionRecord record : records.values()) {
            RegionKey profileKey = record.region().key();
            ModuleRegionBinding binding = kind == ModuleKind.MUSIC
                    ? record.music().getBinding()
                    : record.commands().getBinding();
            ProviderRegionReference reference = binding.resolve(profileKey);
            Target target = new Target(
                    profileKey.world().uuid(),
                    reference.providerId(),
                    reference.regionId()
            );
            RegionKey previous = claimed.putIfAbsent(target, profileKey);
            if(previous != null && !previous.equals(profileKey)) {
                throw new IllegalArgumentException(
                        "duplicate " + kind.name().toLowerCase(java.util.Locale.ROOT)
                                + " module binding to " + reference.providerId()
                                + ":" + reference.regionId() + " in world "
                                + profileKey.world().uuid() + " from " + previous
                                + " and " + profileKey
                );
            }
            if(reference.providerId().equals(RegionProviderIds.NATIVE)) {
                RegionKey nativeTarget;
                try {
                    nativeTarget = new RegionKey(
                            profileKey.world(),
                            reference.regionId()
                    );
                } catch(IllegalArgumentException exception) {
                    throw new IllegalArgumentException(
                            "invalid native " + kind.name().toLowerCase(java.util.Locale.ROOT)
                                    + " module target '" + reference.regionId()
                                    + "' for " + profileKey,
                            exception
                    );
                }
                if(!records.containsKey(nativeTarget)) {
                    throw new IllegalArgumentException(
                            "missing native " + kind.name().toLowerCase(java.util.Locale.ROOT)
                                    + " module target " + nativeTarget
                                    + " for profile " + profileKey
                    );
                }
            }
        }
    }

    private record Target(UUID world, String provider, String region) {
    }
}
