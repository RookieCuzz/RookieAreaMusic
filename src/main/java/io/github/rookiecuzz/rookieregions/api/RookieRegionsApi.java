package io.github.rookiecuzz.rookieregions.api;

import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Stable, read-only facade published through Bukkit ServicesManager. */
public interface RookieRegionsApi {
    ApiVersion version();

    Set<ApiCapability> capabilities();

    default boolean supports(ApiCapability capability) {
        return capability != null && capabilities().contains(capability);
    }

    RegionSnapshot snapshot();

    /** Returns a query pinned to exactly one immutable snapshot revision. */
    RegionQuery query();

    /** Returns a protection facade pinned to exactly one snapshot revision. */
    ProtectionQuery protection();

    FlagRegistry flagRegistry();

    RegionProvider nativeProvider();

    Map<String, RegionProvider> providers();

    Optional<RegionProvider> provider(String id);

    ModuleBindingQuery moduleBindings();
}
