package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;

import java.util.Optional;

/** Stable result of one asynchronous delete transaction. */
public record RegionDeleteOutcome(
        RegionDeleteStatus status,
        Optional<RegionSnapshot> snapshot,
        Optional<Region> deletedRegion,
        String message,
        Optional<Throwable> cause
) {
    public RegionDeleteOutcome {
        if(status == null) {
            throw new IllegalArgumentException("delete status cannot be null");
        }
        snapshot = snapshot == null ? Optional.empty() : snapshot;
        deletedRegion = deletedRegion == null ? Optional.empty() : deletedRegion;
        message = message == null ? "" : message;
        cause = cause == null ? Optional.empty() : cause;
    }
}
