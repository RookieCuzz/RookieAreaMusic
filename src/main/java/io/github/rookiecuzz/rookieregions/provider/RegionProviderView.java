package io.github.rookiecuzz.rookieregions.provider;

import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;

import java.util.Objects;
import java.util.Optional;

/** One immutable provider snapshot bundled with its external-ID lookup. */
public final class RegionProviderView {
    private final RegionSnapshot snapshot;
    private final ExternalLookup lookup;

    public RegionProviderView(RegionSnapshot snapshot, ExternalLookup lookup) {
        this.snapshot = Objects.requireNonNull(
                snapshot,
                "provider snapshot cannot be null"
        );
        this.lookup = Objects.requireNonNull(
                lookup,
                "provider external lookup cannot be null"
        );
    }

    public static RegionProviderView nativeIds(RegionSnapshot snapshot) {
        return new RegionProviderView(snapshot, (world, externalRegionId) -> {
            if(world == null || externalRegionId == null) {
                return Optional.empty();
            }
            try {
                RegionKey key = new RegionKey(world, externalRegionId);
                return snapshot.graph().region(key).map(region -> region.key());
            } catch(IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    public RegionSnapshot snapshot() {
        return snapshot;
    }

    public Optional<RegionKey> regionKey(WorldId world,
                                         String externalRegionId) {
        Optional<RegionKey> result = lookup.find(world, externalRegionId);
        return result == null ? Optional.empty() : result;
    }

    @FunctionalInterface
    public interface ExternalLookup {
        Optional<RegionKey> find(WorldId world, String externalRegionId);
    }
}
