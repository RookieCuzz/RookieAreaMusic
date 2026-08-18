package io.github.rookiecuzz.rookieregions.runtime;

import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.provider.RegionProviderIds;

import java.util.Objects;
import java.util.Optional;

/**
 * Geometry source for one module profile. An empty target means the native
 * region holding the profile, which is the normal RookieRegions behavior.
 */
public final class ModuleRegionBinding {
    private static final ModuleRegionBinding NATIVE_SELF =
            new ModuleRegionBinding(null);

    private final ProviderRegionReference target;

    private ModuleRegionBinding(ProviderRegionReference target) {
        this.target = target;
    }

    public static ModuleRegionBinding nativeSelf() {
        return NATIVE_SELF;
    }

    public static ModuleRegionBinding toProvider(String providerId,
                                                  String regionId) {
        return new ModuleRegionBinding(
                new ProviderRegionReference(providerId, regionId)
        );
    }

    public boolean isNativeSelf() {
        return target == null;
    }

    public Optional<ProviderRegionReference> explicitTarget() {
        return Optional.ofNullable(target);
    }

    /** Resolves the default binding without consulting mutable provider state. */
    public ProviderRegionReference resolve(RegionKey profileRegion) {
        Objects.requireNonNull(profileRegion, "profile region cannot be null");
        return target == null
                ? new ProviderRegionReference(
                        RegionProviderIds.NATIVE,
                        profileRegion.id()
                )
                : target;
    }

    @Override
    public boolean equals(Object value) {
        return this == value
                || value instanceof ModuleRegionBinding other
                && Objects.equals(target, other.target);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(target);
    }

    @Override
    public String toString() {
        return target == null
                ? "rookieregions:self"
                : target.providerId() + ":" + target.regionId();
    }
}
