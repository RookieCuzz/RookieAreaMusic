package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;

import java.util.Optional;

/** Immutable successful native-region publication notification. */
public record RegionMutationPublication(
        SaveMode mode,
        RegionSnapshot previousSnapshot,
        RegionSnapshot currentSnapshot,
        Optional<Region> previousRegion,
        Optional<Region> currentRegion,
        RegionMutationActor actor,
        String sessionId,
        SaveChoice choice
) {
    public RegionMutationPublication {
        if(mode == null || previousSnapshot == null || currentSnapshot == null
                || actor == null || choice == null
                || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "mutation publication fields cannot be null"
            );
        }
        sessionId = sessionId.trim();
        previousRegion = previousRegion == null ? Optional.empty() : previousRegion;
        currentRegion = currentRegion == null ? Optional.empty() : currentRegion;
        if(currentSnapshot.revision() <= previousSnapshot.revision()) {
            throw new IllegalArgumentException(
                    "mutation publication revision must increase"
            );
        }
        boolean validRegions = switch(mode) {
            case CREATE -> previousRegion.isEmpty() && currentRegion.isPresent();
            case EDIT -> previousRegion.isPresent() && currentRegion.isPresent();
            case DELETE -> previousRegion.isPresent() && currentRegion.isEmpty();
        };
        if(!validRegions) {
            throw new IllegalArgumentException(
                    "mutation publication regions do not match " + mode
            );
        }
    }
}
