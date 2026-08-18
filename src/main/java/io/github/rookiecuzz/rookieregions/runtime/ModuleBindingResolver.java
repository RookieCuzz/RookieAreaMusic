package io.github.rookiecuzz.rookieregions.runtime;

import io.github.rookiecuzz.rookieregions.api.ModuleBindingQuery;
import io.github.rookiecuzz.rookieregions.core.ApplicableRegionSet;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionGraph;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.provider.NativeRegionProvider;
import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.provider.RegionProviderView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Resolves immutable module profiles against native or external provider geometry. */
public final class ModuleBindingResolver implements ModuleBindingQuery {
    private static final Comparator<BoundModuleRegion> ORDER = Comparator
            .comparing(BoundModuleRegion::providerId)
            .thenComparingInt(BoundModuleRegion::depth)
            .thenComparing(BoundModuleRegion::identity);

    private final Map<String, RegionProvider> providers;
    private volatile Map<CatalogKey, Catalog> catalogs = Map.of();

    public ModuleBindingResolver(Map<String, ? extends RegionProvider> providers) {
        if(providers == null) {
            throw new IllegalArgumentException("region providers cannot be null");
        }
        LinkedHashMap<String, RegionProvider> normalized = new LinkedHashMap<>();
        for(Map.Entry<String, ? extends RegionProvider> entry : providers.entrySet()) {
            RegionProvider provider = Objects.requireNonNull(
                    entry.getValue(),
                    "region providers cannot contain null"
            );
            String key = ProviderRegionReference.normalizeProviderId(entry.getKey());
            String declared = ProviderRegionReference.normalizeProviderId(provider.id());
            if(!key.equals(declared)) {
                throw new IllegalArgumentException(
                        "provider map key '" + key
                                + "' does not match provider ID '" + declared + "'"
                );
            }
            if(normalized.putIfAbsent(key, provider) != null) {
                throw new IllegalArgumentException("duplicate region provider " + key);
            }
        }
        this.providers = Map.copyOf(normalized);
    }

    public Map<String, RegionProvider> providers() {
        return providers;
    }

    /**
     * Pins each participating provider once, then resolves all profile targets
     * and point membership against those views.
     */
    @Override
    public ModuleBindingResolution resolveAt(RegionSnapshot nativeSnapshot,
                                             ModuleKind module,
                                             WorldId world,
                                             double x,
                                             double y,
                                             double z) {
        Objects.requireNonNull(nativeSnapshot, "native snapshot cannot be null");
        Objects.requireNonNull(module, "module kind cannot be null");
        Objects.requireNonNull(world, "query world cannot be null");

        Catalog catalog = catalog(nativeSnapshot, module, world);
        Map<String, RegionProviderView> views = catalog.views();
        Map<TargetKey, ProfileBinding> attached = catalog.attached();
        Map<String, Set<RegionKey>> applicable = applicableKeys(
                views,
                world,
                x,
                y,
                z
        );

        ArrayList<BoundModuleRegion> result = new ArrayList<>();
        for(Map.Entry<TargetKey, ProfileBinding> entry : attached.entrySet()) {
            TargetKey target = entry.getKey();
            if(!applicable.getOrDefault(target.providerId(), Set.of())
                    .contains(target.regionKey())) {
                continue;
            }
            RegionProviderView view = views.get(target.providerId());
            RegionGraph graph = view.snapshot().graph();
            Region geometry = graph.region(target.regionKey()).orElseThrow();
            ProfileBinding profile = entry.getValue();
            String parentIdentity = nearestAttachedAncestor(
                    graph,
                    target,
                    attached
            ).map(parent -> attached.get(parent).identity()).orElse(null);
            result.add(new BoundModuleRegion(
                    module,
                    profile.identity(),
                    target.providerId(),
                    profile.reference().regionId(),
                    profile.record().region().key(),
                    geometry,
                    depth(graph, geometry),
                    Optional.ofNullable(parentIdentity),
                    profile.record()
            ));
        }
        result.sort(ORDER);
        return new ModuleBindingResolution(result, catalog.issues());
    }

    /** Resolves one stored profile target without requiring point membership. */
    @Override
    public Optional<Region> target(RegionSnapshot nativeSnapshot,
                                   ModuleKind module,
                                   RegionKey profileRegion) {
        Objects.requireNonNull(nativeSnapshot, "native snapshot cannot be null");
        Objects.requireNonNull(module, "module kind cannot be null");
        Objects.requireNonNull(profileRegion, "profile region cannot be null");
        RegionRecord record = nativeSnapshot.records().get(profileRegion);
        if(record == null) {
            return Optional.empty();
        }
        ModuleRegionBinding binding = binding(record, module);
        return target(nativeSnapshot, profileRegion, binding);
    }

    /** Resolves a proposed binding before a mutation is submitted. */
    @Override
    public Optional<Region> target(RegionSnapshot nativeSnapshot,
                                   RegionKey profileRegion,
                                   ModuleRegionBinding binding) {
        Objects.requireNonNull(nativeSnapshot, "native snapshot cannot be null");
        Objects.requireNonNull(profileRegion, "profile region cannot be null");
        Objects.requireNonNull(binding, "module binding cannot be null");
        if(!nativeSnapshot.records().containsKey(profileRegion)) {
            return Optional.empty();
        }
        ProviderRegionReference reference = binding.resolve(profileRegion);
        RegionProviderView view = reference.providerId().equals(NativeRegionProvider.ID)
                ? RegionProviderView.nativeIds(nativeSnapshot)
                : Optional.ofNullable(providers.get(reference.providerId()))
                        .map(RegionProvider::view)
                        .orElse(null);
        if(view == null) {
            return Optional.empty();
        }
        return view.regionKey(profileRegion.world(), reference.regionId())
                .flatMap(view.snapshot().graph()::region);
    }

    private List<ProfileBinding> profiles(RegionSnapshot snapshot,
                                          ModuleKind module,
                                          WorldId world) {
        ArrayList<ProfileBinding> result = new ArrayList<>();
        for(RegionRecord record : snapshot.records().values()) {
            RegionKey key = record.region().key();
            if(!key.world().equals(world) || empty(record, module)) {
                continue;
            }
            ProviderRegionReference reference = binding(record, module).resolve(key);
            result.add(new ProfileBinding(
                    identity(module, key, reference),
                    reference,
                    record
            ));
        }
        result.sort(Comparator.comparing(ProfileBinding::identity));
        return List.copyOf(result);
    }

    private Catalog catalog(RegionSnapshot nativeSnapshot,
                            ModuleKind module,
                            WorldId world) {
        CatalogKey catalogKey = new CatalogKey(module, world);
        Catalog current = catalogs.get(catalogKey);
        if(current != null
                && current.nativeSnapshot() == nativeSnapshot
                && current.world().equals(world)) {
            Map<String, RegionProviderView> refreshed = pinViews(
                    nativeSnapshot,
                    current.providerIds()
            );
            if(sameSnapshots(current.views(), refreshed)) {
                return current;
            }
        }
        synchronized(this) {
            current = catalogs.get(catalogKey);
            List<ProfileBinding> profiles;
            Set<String> providerIds;
            if(current != null
                    && current.nativeSnapshot() == nativeSnapshot
                    && current.world().equals(world)) {
                profiles = current.profiles();
                providerIds = current.providerIds();
            } else {
                profiles = profiles(nativeSnapshot, module, world);
                providerIds = providerIds(profiles);
            }
            Map<String, RegionProviderView> views = pinViews(
                    nativeSnapshot,
                    providerIds
            );
            if(current != null
                    && current.nativeSnapshot() == nativeSnapshot
                    && current.world().equals(world)
                    && sameSnapshots(current.views(), views)) {
                return current;
            }
            ArrayList<ModuleBindingIssue> issues = new ArrayList<>();
            Map<TargetKey, ProfileBinding> attached = Map.copyOf(attachTargets(
                    profiles,
                    views,
                    issues
            ));
            Catalog built = new Catalog(
                    nativeSnapshot,
                    module,
                    world,
                    profiles,
                    providerIds,
                    Map.copyOf(views),
                    attached,
                    List.copyOf(issues)
            );
            LinkedHashMap<CatalogKey, Catalog> changed = new LinkedHashMap<>(catalogs);
            changed.entrySet().removeIf(entry ->
                    entry.getValue().nativeSnapshot() != nativeSnapshot
            );
            changed.put(catalogKey, built);
            catalogs = Map.copyOf(changed);
            return built;
        }
    }

    private static boolean sameSnapshots(
            Map<String, RegionProviderView> first,
            Map<String, RegionProviderView> second) {
        if(!first.keySet().equals(second.keySet())) {
            return false;
        }
        for(String provider : first.keySet()) {
            if(first.get(provider).snapshot() != second.get(provider).snapshot()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, RegionProviderView> pinViews(
            RegionSnapshot nativeSnapshot,
            Collection<String> providerIds) {
        LinkedHashMap<String, RegionProviderView> result = new LinkedHashMap<>();
        for(String providerId : providerIds) {
            if(providerId.equals(NativeRegionProvider.ID)) {
                result.put(providerId, RegionProviderView.nativeIds(nativeSnapshot));
                continue;
            }
            RegionProvider provider = providers.get(providerId);
            if(provider != null) {
                result.put(providerId, provider.view());
            }
        }
        return result;
    }

    private static Set<String> providerIds(List<ProfileBinding> profiles) {
        java.util.TreeSet<String> result = new java.util.TreeSet<>();
        for(ProfileBinding profile : profiles) {
            result.add(profile.reference().providerId());
        }
        return Set.copyOf(result);
    }

    private Map<TargetKey, ProfileBinding> attachTargets(
            List<ProfileBinding> profiles,
            Map<String, RegionProviderView> views,
            List<ModuleBindingIssue> issues) {
        LinkedHashMap<TargetKey, ProfileBinding> result = new LinkedHashMap<>();
        for(ProfileBinding profile : profiles) {
            String providerId = profile.reference().providerId();
            RegionProvider provider = providers.get(providerId);
            RegionProviderView view = views.get(providerId);
            if(view == null) {
                issues.add(issue(
                        ModuleBindingIssue.Code.UNKNOWN_PROVIDER,
                        profile,
                        "unknown region provider '" + providerId + "'"
                ));
                continue;
            }
            Optional<RegionKey> key = view.regionKey(
                    profile.record().region().key().world(),
                    profile.reference().regionId()
            );
            if(key.isEmpty() || view.snapshot().graph()
                    .region(key.orElse(null)).isEmpty()) {
                ModuleBindingIssue.Code code = provider != null && !provider.available()
                        ? ModuleBindingIssue.Code.PROVIDER_UNAVAILABLE
                        : ModuleBindingIssue.Code.TARGET_NOT_FOUND;
                issues.add(issue(
                        code,
                        profile,
                        "provider '" + providerId + "' has no region '"
                                + profile.reference().regionId() + "' in world "
                                + profile.record().region().key().world().uuid()
                ));
                continue;
            }
            TargetKey target = new TargetKey(providerId, key.orElseThrow());
            ProfileBinding previous = result.putIfAbsent(target, profile);
            if(previous != null) {
                result.remove(target);
                issues.add(issue(
                        ModuleBindingIssue.Code.TARGET_ALIAS,
                        previous,
                        "provider lookup aliases profiles '" + previous.identity()
                                + "' and '" + profile.identity() + "' to " + target
                ));
                issues.add(issue(
                        ModuleBindingIssue.Code.TARGET_ALIAS,
                        profile,
                        "provider lookup aliases profiles '" + previous.identity()
                                + "' and '" + profile.identity() + "' to " + target
                ));
            }
        }
        return result;
    }

    private static Map<String, Set<RegionKey>> applicableKeys(
            Map<String, RegionProviderView> views,
            WorldId world,
            double x,
            double y,
            double z) {
        HashMap<String, Set<RegionKey>> result = new HashMap<>();
        for(Map.Entry<String, RegionProviderView> entry : views.entrySet()) {
            RegionProviderView view = entry.getValue();
            ApplicableRegionSet at = new RegionQuery(view.snapshot()).at(
                    world,
                    x,
                    y,
                    z
            );
            HashSet<RegionKey> included = new HashSet<>();
            at.globalRegion().ifPresent(region -> included.add(region.key()));
            for(Region local : at.localRegions()) {
                included.add(local.key());
                view.snapshot().graph().ancestors(local.key())
                        .forEach(region -> included.add(region.key()));
            }
            result.put(entry.getKey(), Set.copyOf(included));
        }
        return result;
    }

    private static Optional<TargetKey> nearestAttachedAncestor(
            RegionGraph graph,
            TargetKey target,
            Map<TargetKey, ProfileBinding> attached) {
        for(Region ancestor : graph.ancestors(target.regionKey())) {
            TargetKey candidate = new TargetKey(
                    target.providerId(),
                    ancestor.key()
            );
            if(attached.containsKey(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static int depth(RegionGraph graph, Region region) {
        int depth = 0;
        for(Region ancestor : graph.ancestors(region.key())) {
            if(!ancestor.key().isGlobal()) {
                depth++;
            }
        }
        return depth;
    }

    private static ModuleRegionBinding binding(RegionRecord record,
                                               ModuleKind module) {
        return module == ModuleKind.MUSIC
                ? record.music().getBinding()
                : record.commands().getBinding();
    }

    private static boolean empty(RegionRecord record, ModuleKind module) {
        return module == ModuleKind.MUSIC
                ? record.music().isEmpty()
                : record.commands().isEmpty();
    }

    private static String identity(ModuleKind module,
                                   RegionKey profile,
                                   ProviderRegionReference target) {
        return module.name().toLowerCase(Locale.ROOT) + ":" + profile + "@"
                + target.providerId() + ":" + target.regionId();
    }

    private static ModuleBindingIssue issue(ModuleBindingIssue.Code code,
                                            ProfileBinding profile,
                                            String message) {
        ModuleKind module = profile.identity().startsWith("music:")
                ? ModuleKind.MUSIC
                : ModuleKind.COMMANDS;
        return new ModuleBindingIssue(
                code,
                module,
                profile.record().region().key(),
                message
        );
    }

    private record ProfileBinding(String identity,
                                  ProviderRegionReference reference,
                                  RegionRecord record) {
    }

    private record TargetKey(String providerId, RegionKey regionKey) {
    }

    private record Catalog(RegionSnapshot nativeSnapshot,
                           ModuleKind module,
                           WorldId world,
                           List<ProfileBinding> profiles,
                           Set<String> providerIds,
                           Map<String, RegionProviderView> views,
                           Map<TargetKey, ProfileBinding> attached,
                           List<ModuleBindingIssue> issues) {
    }

    private record CatalogKey(ModuleKind module, WorldId world) {
    }
}
