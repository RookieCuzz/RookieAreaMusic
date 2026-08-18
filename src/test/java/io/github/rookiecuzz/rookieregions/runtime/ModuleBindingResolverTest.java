package io.github.rookiecuzz.rookieregions.runtime;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.MusicPolicyMode;
import io.github.rookiecuzz.rookieregions.module.music.MusicTrack;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicChannel;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.provider.NativeRegionProvider;
import io.github.rookiecuzz.rookieregions.provider.ReflectiveWorldGuardProvider;
import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.provider.RegionProviderView;
import io.github.rookiecuzz.rookieregions.provider.WorldGuardProvider;
import io.github.rookiecuzz.rookieregions.provider.WorldGuardReflectionFacade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleBindingResolverTest {
    private static final WorldId WORLD = new WorldId(
            UUID.fromString("91000000-0000-0000-0000-000000000001"),
            "minecraft:bindings"
    );

    @Test
    void worldGuardGeometryDrivesMusicProfilesAndPreservesBoundParentChain() {
        WorldGuardProvider worldGuard = worldGuard();
        RegionRecord global = RegionRecord.coreOnly(global());
        RegionRecord outer = record(
                nativeBox("outer-profile", 200, 210),
                music("birds", ModuleRegionBinding.toProvider("worldguard", "forest")),
                RegionCommandProfile.empty()
        );
        RegionRecord inner = record(
                nativeBox("inner-profile", 220, 230),
                blocked(ModuleRegionBinding.toProvider("worldguard", "quiet")),
                RegionCommandProfile.empty()
        );
        RegionSnapshot nativeSnapshot = RegionSnapshot.ofRecords(
                4,
                List.of(global, outer, inner)
        );
        ModuleBindingResolver resolver = new ModuleBindingResolver(Map.of(
                NativeRegionProvider.ID,
                new SnapshotProvider(nativeSnapshot),
                worldGuard.id(),
                worldGuard
        ));

        ModuleBindingResolution inside = resolver.resolveAt(
                nativeSnapshot,
                ModuleKind.MUSIC,
                WORLD,
                25,
                25,
                25
        );

        assertTrue(inside.issues().isEmpty());
        assertEquals(2, inside.regions().size());
        BoundModuleRegion outerBound = inside.regions().stream()
                .filter(bound -> bound.profileRegion().id().equals("outer-profile"))
                .findFirst().orElseThrow();
        BoundModuleRegion innerBound = inside.regions().stream()
                .filter(bound -> bound.profileRegion().id().equals("inner-profile"))
                .findFirst().orElseThrow();
        assertEquals("forest", outerBound.externalRegionId());
        assertEquals("quiet", innerBound.externalRegionId());
        assertEquals(outerBound.identity(), innerBound.parentIdentity().orElseThrow());
        assertTrue(resolver.target(
                nativeSnapshot,
                ModuleKind.MUSIC,
                inner.region().key()
        ).isPresent());

        ModuleBindingResolution nativeGeometry = resolver.resolveAt(
                nativeSnapshot,
                ModuleKind.MUSIC,
                WORLD,
                205,
                5,
                205
        );
        assertTrue(nativeGeometry.regions().isEmpty());
    }

    @Test
    void unresolvedExternalTargetIsDiagnosedAndNeverFallsBackToNativeGeometry() {
        RegionRecord profile = record(
                nativeBox("profile", 0, 10),
                music(
                        "theme",
                        ModuleRegionBinding.toProvider("missing-provider", "town")
                ),
                RegionCommandProfile.empty()
        );
        RegionSnapshot snapshot = RegionSnapshot.ofRecords(
                1,
                List.of(RegionRecord.coreOnly(global()), profile)
        );
        ModuleBindingResolution resolution = new ModuleBindingResolver(Map.of())
                .resolveAt(snapshot, ModuleKind.MUSIC, WORLD, 5, 5, 5);

        assertTrue(resolution.regions().isEmpty());
        assertEquals(1, resolution.issues().size());
        assertEquals(
                ModuleBindingIssue.Code.UNKNOWN_PROVIDER,
                resolution.issues().getFirst().code()
        );
    }

    @Test
    void commandsUseTheSameProviderGeometryWithoutDependingOnMusicSelection() {
        RegionRecord profile = record(
                nativeBox("command-profile", 200, 210),
                RegionMusicProfile.empty(),
                new RegionCommandProfile(
                        List.of("say enter"),
                        List.of("say leave"),
                        ModuleRegionBinding.toProvider("worldguard", "forest")
                )
        );
        RegionSnapshot snapshot = RegionSnapshot.ofRecords(
                1,
                List.of(RegionRecord.coreOnly(global()), profile)
        );
        WorldGuardProvider worldGuard = worldGuard();
        ModuleBindingResolution resolution = new ModuleBindingResolver(Map.of(
                worldGuard.id(),
                worldGuard
        )).resolveAt(snapshot, ModuleKind.COMMANDS, WORLD, 5, 5, 5);

        assertEquals(1, resolution.regions().size());
        assertEquals(
                "command-profile",
                resolution.regions().getFirst().profileRegion().id()
        );
        assertTrue(resolution.regions().getFirst().profile().music().isEmpty());
    }

    @Test
    void completeSnapshotRejectsDuplicateTargetsAndMissingNativeTargets() {
        ModuleRegionBinding shared = ModuleRegionBinding.toProvider(
                "worldguard",
                "forest"
        );
        RegionRecord first = record(
                nativeBox("first", 0, 10),
                music("one", shared),
                RegionCommandProfile.empty()
        );
        RegionRecord second = record(
                nativeBox("second", 20, 30),
                music("two", shared),
                RegionCommandProfile.empty()
        );

        assertThrows(IllegalArgumentException.class, () -> RegionSnapshot.ofRecords(
                1,
                List.of(RegionRecord.coreOnly(global()), first, second)
        ));

        RegionRecord missingNative = record(
                nativeBox("third", 40, 50),
                music(
                        "three",
                        ModuleRegionBinding.toProvider("rookieregions", "absent")
                ),
                RegionCommandProfile.empty()
        );
        assertThrows(IllegalArgumentException.class, () -> RegionSnapshot.ofRecords(
                1,
                List.of(RegionRecord.coreOnly(global()), missingNative)
        ));
    }

    @Test
    void immutableCatalogIsReusedUntilAProviderSnapshotChanges() {
        RegionRecord profile = record(
                nativeBox("cached-profile", 200, 210),
                music(
                        "cached",
                        ModuleRegionBinding.toProvider("counting", "forest")
                ),
                RegionCommandProfile.empty()
        );
        RegionSnapshot nativeSnapshot = RegionSnapshot.ofRecords(
                7,
                List.of(RegionRecord.coreOnly(global()), profile)
        );
        Region external = Region.builder(
                        new RegionKey(WORLD, "forest"),
                        new CuboidShape(0, 0, 0, 100, 100, 100)
                )
                .parent(RegionKey.global(WORLD))
                .build();
        RegionSnapshot providerSnapshot = RegionSnapshot.of(
                3,
                List.of(global(), external)
        );
        AtomicInteger lookups = new AtomicInteger();
        RegionProvider provider = new RegionProvider() {
            @Override
            public String id() {
                return "counting";
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public RegionSnapshot snapshot() {
                return providerSnapshot;
            }

            @Override
            public RegionProviderView view() {
                return new RegionProviderView(providerSnapshot, (world, id) -> {
                    lookups.incrementAndGet();
                    return id.equals("forest")
                            ? java.util.Optional.of(external.key())
                            : java.util.Optional.empty();
                });
            }
        };
        ModuleBindingResolver resolver = new ModuleBindingResolver(Map.of(
                provider.id(), provider
        ));

        resolver.resolveAt(nativeSnapshot, ModuleKind.MUSIC, WORLD, 5, 5, 5);
        resolver.resolveAt(nativeSnapshot, ModuleKind.MUSIC, WORLD, 6, 6, 6);

        assertEquals(1, lookups.get());
    }

    private static ReflectiveWorldGuardProvider worldGuard() {
        return new ReflectiveWorldGuardProvider(() ->
                new WorldGuardReflectionFacade.Capture(List.of(
                        new WorldGuardReflectionFacade.WorldView(
                                WORLD.uuid(),
                                WORLD.namespacedKey(),
                                List.of(
                                        view("forest", null, 0, 100),
                                        view("quiet", "forest", 20, 40)
                                )
                        )
                ))
        );
    }

    private static WorldGuardReflectionFacade.RegionView view(
            String id,
            String parent,
            int min,
            int max) {
        return new WorldGuardReflectionFacade.RegionView(
                id,
                0,
                parent,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                new WorldGuardReflectionFacade.CuboidView(
                        min, min, min, max, max, max
                )
        );
    }

    private static RegionRecord record(Region region,
                                       RegionMusicProfile music,
                                       RegionCommandProfile commands) {
        return new RegionRecord(region, music, commands);
    }

    private static RegionMusicProfile music(String track,
                                            ModuleRegionBinding binding) {
        RegionMusicChannel channel = RegionMusicChannel.builder()
                .policy(MusicPolicyMode.ADD)
                .tracks(List.of(new MusicTrack(track, "test:" + track, 10)))
                .build();
        return new RegionMusicProfile(Map.of("ambience", channel), binding);
    }

    private static RegionMusicProfile blocked(ModuleRegionBinding binding) {
        return new RegionMusicProfile(
                Map.of(
                        "ambience",
                        RegionMusicChannel.builder()
                                .policy(MusicPolicyMode.BLOCK)
                                .build()
                ),
                binding
        );
    }

    private static Region nativeBox(String id, int min, int max) {
        return Region.builder(
                        new RegionKey(WORLD, id),
                        new CuboidShape(min, 0, min, max, 20, max)
                )
                .parent(RegionKey.global(WORLD))
                .build();
    }

    private static Region global() {
        return Region.builder(RegionKey.global(WORLD), GlobalShape.INSTANCE).build();
    }

    private record SnapshotProvider(RegionSnapshot snapshot)
            implements io.github.rookiecuzz.rookieregions.provider.RegionProvider {
        @Override
        public String id() {
            return NativeRegionProvider.ID;
        }

        @Override
        public boolean available() {
            return true;
        }
    }
}
