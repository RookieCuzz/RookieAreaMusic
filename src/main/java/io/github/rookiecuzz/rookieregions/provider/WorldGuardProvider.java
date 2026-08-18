package io.github.rookiecuzz.rookieregions.provider;

import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;

/**
 * Read-only seam for an optional WorldGuard adapter.
 *
 * <p>Implementations translate a WorldGuard view to the neutral core model;
 * this interface deliberately has no WorldGuard compile-time types.</p>
 */
public interface WorldGuardProvider extends RegionProvider {
    String ID = RegionProviderIds.WORLDGUARD;

    /** Empty only while the most recent complete capture succeeded. */
    java.util.Optional<String> failureReason();

    /** Non-fatal conversion notes from the latest successful capture. */
    default java.util.List<String> diagnostics() {
        return java.util.List.of();
    }

    /**
     * Explicitly captures WorldGuard state. Bukkit-backed implementations must
     * be refreshed from the server thread; {@link #snapshot()} is always a
     * cache-only, thread-safe read.
     */
    RegionSnapshot refresh();
}
