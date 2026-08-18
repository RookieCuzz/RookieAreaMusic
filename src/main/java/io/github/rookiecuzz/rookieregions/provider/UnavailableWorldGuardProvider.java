package io.github.rookiecuzz.rookieregions.provider;

import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;

/** Dependency-free empty provider used when WorldGuard is not available. */
public final class UnavailableWorldGuardProvider implements WorldGuardProvider {
    public static final UnavailableWorldGuardProvider INSTANCE =
            new UnavailableWorldGuardProvider("WorldGuard is not available");

    private static final RegionSnapshot EMPTY = RegionSnapshot.empty();
    private final String reason;

    private UnavailableWorldGuardProvider(String reason) {
        this.reason = java.util.Objects.requireNonNull(
                reason,
                "WorldGuard failure reason cannot be null"
        );
    }

    public static UnavailableWorldGuardProvider because(String reason) {
        return new UnavailableWorldGuardProvider(reason);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public RegionSnapshot snapshot() {
        return EMPTY;
    }

    @Override
    public RegionSnapshot refresh() {
        return EMPTY;
    }

    @Override
    public java.util.Optional<String> failureReason() {
        return java.util.Optional.of(reason);
    }
}
