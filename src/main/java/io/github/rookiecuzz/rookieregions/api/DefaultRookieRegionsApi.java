package io.github.rookiecuzz.rookieregions.api;

import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.provider.NativeRegionProvider;
import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;
import io.github.rookiecuzz.rookieregions.runtime.ModuleBindingResolver;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Runtime implementation kept separate from the public API contract. */
public final class DefaultRookieRegionsApi implements RookieRegionsApi {
    private static final Set<ApiCapability> CAPABILITIES = Set.of(
            ApiCapability.SNAPSHOT_QUERY,
            ApiCapability.TYPED_FLAGS,
            ApiCapability.PROTECTION_DECISIONS,
            ApiCapability.ATOMIC_MUTATIONS,
            ApiCapability.BUKKIT_EVENTS,
            ApiCapability.CUSTOM_FLAG_REGISTRATION,
            ApiCapability.CUSTOM_PROVIDER_REGISTRATION,
            ApiCapability.MODULE_BINDINGS
    );

    private final RegionContainer container;
    private final FlagRegistry flagRegistry;
    private final RegionProvider nativeProvider;
    private final Map<String, RegionProvider> providers;
    private final ModuleBindingResolver moduleBindings;

    public DefaultRookieRegionsApi(RegionContainer container,
                                   FlagRegistry flagRegistry,
                                   Map<String, ? extends RegionProvider> providers) {
        this.container = Objects.requireNonNull(
                container, "API region container cannot be null"
        );
        this.flagRegistry = Objects.requireNonNull(
                flagRegistry, "API flag registry cannot be null"
        );
        LinkedHashMap<String, RegionProvider> normalized = new LinkedHashMap<>();
        if(providers != null) {
            for(Map.Entry<String, ? extends RegionProvider> entry : providers.entrySet()) {
                String id = entry.getKey().trim().toLowerCase(Locale.ROOT);
                RegionProvider provider = Objects.requireNonNull(
                        entry.getValue(), "API provider cannot be null"
                );
                if(!id.equals(provider.id().trim().toLowerCase(Locale.ROOT))
                        || normalized.putIfAbsent(id, provider) != null) {
                    throw new IllegalArgumentException(
                            "invalid or duplicate API provider: " + entry.getKey()
                    );
                }
            }
        }
        this.nativeProvider = Optional.ofNullable(
                normalized.get(NativeRegionProvider.ID)
        ).orElseThrow(() -> new IllegalArgumentException(
                "API providers must include " + NativeRegionProvider.ID
        ));
        this.providers = Map.copyOf(normalized);
        this.moduleBindings = new ModuleBindingResolver(this.providers);
    }

    @Override
    public ApiVersion version() {
        return ApiVersion.CURRENT;
    }

    @Override
    public Set<ApiCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public RegionSnapshot snapshot() {
        return container.snapshot();
    }

    @Override
    public RegionQuery query() {
        return container.query();
    }

    @Override
    public ProtectionQuery protection() {
        return new ProtectionQuery(query());
    }

    @Override
    public FlagRegistry flagRegistry() {
        return flagRegistry;
    }

    @Override
    public RegionProvider nativeProvider() {
        return nativeProvider;
    }

    @Override
    public Map<String, RegionProvider> providers() {
        return providers;
    }

    @Override
    public Optional<RegionProvider> provider(String id) {
        return Optional.ofNullable(
                id == null ? null : providers.get(id.trim().toLowerCase(Locale.ROOT))
        );
    }

    @Override
    public ModuleBindingResolver moduleBindings() {
        return moduleBindings;
    }
}
