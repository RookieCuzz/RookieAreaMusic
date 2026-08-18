package io.github.rookiecuzz.rookieregions.api;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.runtime.ModuleBindingResolution;
import io.github.rookiecuzz.rookieregions.runtime.ModuleKind;
import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;

import java.util.Optional;

/** Read-only resolution of module profiles against provider geometry. */
public interface ModuleBindingQuery {
    ModuleBindingResolution resolveAt(
            RegionSnapshot nativeSnapshot,
            ModuleKind module,
            WorldId world,
            double x,
            double y,
            double z
    );

    Optional<Region> target(
            RegionSnapshot nativeSnapshot,
            ModuleKind module,
            RegionKey profileRegion
    );

    Optional<Region> target(
            RegionSnapshot nativeSnapshot,
            RegionKey profileRegion,
            ModuleRegionBinding binding
    );
}
