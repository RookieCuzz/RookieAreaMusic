package io.github.rookiecuzz.rookieregions.api;

import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.provider.RegionProviderIds;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Registration service available during Bukkit {@code onLoad()}.
 * Registrations are frozen before RookieRegions stages any region document.
 */
public final class RookieRegionsBootstrap {
    private static final java.util.Set<String> RESERVED_PROVIDERS =
            java.util.Set.of(
                    RegionProviderIds.NATIVE,
                    RegionProviderIds.WORLDGUARD
            );

    private final Plugin host;
    private final LinkedHashMap<String, Flag<?>> flags = new LinkedHashMap<>();
    private final LinkedHashMap<String, RegionProvider> providers =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, String> contributors = new LinkedHashMap<>();
    private boolean acceptingRegistrations = true;

    public RookieRegionsBootstrap(Plugin host,
                                  Collection<? extends Flag<?>> builtInFlags) {
        this.host = Objects.requireNonNull(host, "bootstrap host cannot be null");
        if(builtInFlags != null) {
            for(Flag<?> flag : builtInFlags) {
                registerFlagDefinition(host.getName(), flag);
            }
        }
    }

    public synchronized void registerFlag(Plugin contributor, Flag<?> flag) {
        requireOpen(contributor);
        registerFlagDefinition(contributor.getName(), flag);
    }

    public synchronized void registerProvider(Plugin contributor,
                                              RegionProvider provider) {
        requireOpen(contributor);
        Objects.requireNonNull(provider, "region provider cannot be null");
        String id = normalizeProviderId(provider.id());
        if(RESERVED_PROVIDERS.contains(id)) {
            throw new IllegalArgumentException(
                    "region provider ID is reserved by RookieRegions: " + id
            );
        }
        RegionProvider previous = providers.putIfAbsent(id, provider);
        if(previous != null) {
            throw new IllegalArgumentException(
                    "duplicate region provider '" + id + "' from "
                            + contributor.getName()
            );
        }
        contributors.put("provider:" + id, contributor.getName());
    }

    public synchronized boolean acceptingRegistrations() {
        return acceptingRegistrations;
    }

    /** Host-only transition from registration into strict staging. */
    public synchronized Snapshot freeze(Plugin requester) {
        if(requester != host) {
            throw new SecurityException("only RookieRegions may freeze its bootstrap");
        }
        acceptingRegistrations = false;
        return new Snapshot(
                new FlagRegistry(flags.values()),
                Map.copyOf(providers),
                Map.copyOf(contributors)
        );
    }

    private void requireOpen(Plugin contributor) {
        Objects.requireNonNull(contributor, "contributing plugin cannot be null");
        if(!acceptingRegistrations) {
            throw new IllegalStateException(
                    "RookieRegions registration is closed; register during onLoad()"
            );
        }
    }

    private void registerFlagDefinition(String contributor, Flag<?> flag) {
        Objects.requireNonNull(flag, "flag cannot be null");
        Flag<?> previous = flags.putIfAbsent(flag.name(), flag);
        if(previous != null) {
            throw new IllegalArgumentException(
                    "duplicate flag '" + flag.name() + "' from " + contributor
            );
        }
        contributors.put("flag:" + flag.name(), contributor);
    }

    private static String normalizeProviderId(String requested) {
        if(requested == null || requested.trim().isEmpty()) {
            throw new IllegalArgumentException("region provider ID cannot be blank");
        }
        String normalized = requested.trim().toLowerCase(Locale.ROOT);
        if(!normalized.matches("[a-z][a-z0-9._-]*")) {
            throw new IllegalArgumentException(
                    "invalid region provider ID: " + requested
            );
        }
        return normalized;
    }

    public record Snapshot(FlagRegistry flags,
                           Map<String, RegionProvider> providers,
                           Map<String, String> contributors) {
        public Snapshot {
            Objects.requireNonNull(flags, "bootstrap flags cannot be null");
            providers = Map.copyOf(providers);
            contributors = Map.copyOf(contributors);
        }
    }
}
