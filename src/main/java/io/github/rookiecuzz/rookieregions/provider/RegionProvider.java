package io.github.rookiecuzz.rookieregions.provider;

import io.github.rookiecuzz.rookieregions.core.ApplicableRegionSet;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;

/** Read-only region source exposed to integrations. */
public interface RegionProvider {
    String id();

    boolean available();

    /** Returns one immutable, internally consistent provider view. */
    RegionSnapshot snapshot();

    /** Pins geometry and external-ID resolution to the same provider capture. */
    default RegionProviderView view() {
        return RegionProviderView.nativeIds(snapshot());
    }

    /** Returns a query pinned to the snapshot current at method invocation. */
    default RegionQuery query() {
        return new RegionQuery(snapshot());
    }

    default ApplicableRegionSet regionsAt(WorldId world,
                                           double x,
                                           double y,
                                           double z) {
        return query().at(world, x, y, z);
    }

    /**
     * Resolves a provider's external, case-insensitive region ID to the core
     * key used in its current cached snapshot.
     */
    default java.util.Optional<RegionKey> regionKey(WorldId world,
                                                    String externalRegionId) {
        return view().regionKey(world, externalRegionId);
    }
}
