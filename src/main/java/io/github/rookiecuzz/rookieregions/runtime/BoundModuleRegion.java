package io.github.rookiecuzz.rookieregions.runtime;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;

import java.util.Objects;
import java.util.Optional;

/** One module profile whose provider geometry contains the queried point. */
public record BoundModuleRegion(ModuleKind module,
                                String identity,
                                String providerId,
                                String externalRegionId,
                                RegionKey profileRegion,
                                Region geometryRegion,
                                int depth,
                                Optional<String> parentIdentity,
                                RegionRecord profile) {
    public BoundModuleRegion {
        Objects.requireNonNull(module, "bound module kind cannot be null");
        requireText(identity, "bound module identity");
        requireText(providerId, "bound module provider ID");
        requireText(externalRegionId, "bound external region ID");
        Objects.requireNonNull(profileRegion, "profile region cannot be null");
        Objects.requireNonNull(geometryRegion, "geometry region cannot be null");
        if(depth < 0) {
            throw new IllegalArgumentException("bound module depth cannot be negative");
        }
        parentIdentity = parentIdentity == null ? Optional.empty() : parentIdentity;
        Objects.requireNonNull(profile, "bound module profile cannot be null");
        if(!profile.region().key().equals(profileRegion)) {
            throw new IllegalArgumentException(
                    "bound module profile key does not match its record"
            );
        }
    }

    private static void requireText(String value, String label) {
        if(value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
    }
}
