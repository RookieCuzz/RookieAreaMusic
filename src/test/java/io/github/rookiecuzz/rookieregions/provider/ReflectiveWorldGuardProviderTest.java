package io.github.rookiecuzz.rookieregions.provider;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveWorldGuardProviderTest {
    private static final UUID WORLD_UUID = UUID.fromString(
            "00000000-0000-0000-0000-0000000000a1"
    );
    private static final UUID OWNER_UUID = UUID.fromString(
            "00000000-0000-0000-0000-0000000000a2"
    );
    private static final UUID MEMBER_UUID = UUID.fromString(
            "00000000-0000-0000-0000-0000000000a3"
    );

    @Test
    void convertsGlobalCuboidPolygonParentsAndDomainsAsOneSnapshot(){
        ReflectiveWorldGuardProvider provider = new ReflectiveWorldGuardProvider(
                () -> validCapture(List.of(
                        region(
                                "__global__",
                                1,
                                null,
                                Set.of(OWNER_UUID),
                                Set.of("Administrators"),
                                Set.of(),
                                Set.of(),
                                WorldGuardReflectionFacade.GlobalView.INSTANCE
                        ),
                        region(
                                "Town",
                                8,
                                null,
                                Set.of(OWNER_UUID),
                                Set.of("Mayors"),
                                Set.of(),
                                Set.of(),
                                new WorldGuardReflectionFacade.CuboidView(
                                        0, 0, 0, 99, 99, 99
                                )
                        ),
                        region(
                                "Plot/One",
                                12,
                                "TOWN",
                                Set.of(),
                                Set.of(),
                                Set.of(MEMBER_UUID),
                                Set.of("Builders"),
                                new WorldGuardReflectionFacade.PolygonalView(
                                        10,
                                        20,
                                        List.of(
                                                new WorldGuardReflectionFacade.PointView(10, 10),
                                                new WorldGuardReflectionFacade.PointView(20, 10),
                                                new WorldGuardReflectionFacade.PointView(20, 20),
                                                new WorldGuardReflectionFacade.PointView(10, 20)
                                        )
                                )
                        )
                ))
        );

        RegionSnapshot snapshot = provider.snapshot();
        WorldId world = new WorldId(WORLD_UUID, "minecraft:worldguard_test");
        Region global = snapshot.graph().global(world).orElseThrow();
        Region town = snapshot.graph().region(new RegionKey(world, "town")).orElseThrow();
        Region plot = snapshot.graph().regions().stream()
                .filter(region -> region.shape() instanceof PolygonPrismShape)
                .findFirst()
                .orElseThrow();

        assertTrue(provider.available());
        assertTrue(provider.failureReason().isEmpty());
        assertEquals(3, snapshot.graph().regions().size());
        assertEquals(1, global.priority());
        assertTrue(global.owners().players().contains(OWNER_UUID));
        assertTrue(global.owners().groups().contains("administrators"));

        CuboidShape townShape = assertInstanceOf(CuboidShape.class, town.shape());
        assertEquals(100.0d, townShape.bounds().maxX());
        assertEquals(100.0d, townShape.bounds().maxY());
        assertEquals(100.0d, townShape.bounds().maxZ());
        assertEquals(global.key(), town.parent().orElseThrow());
        assertEquals(8, town.priority());
        assertTrue(town.owners().groups().contains("mayors"));

        assertTrue(plot.key().id().startsWith("wg-encoded-"));
        assertEquals(town.key(), plot.parent().orElseThrow());
        assertEquals(12, plot.priority());
        assertTrue(plot.members().players().contains(MEMBER_UUID));
        assertTrue(plot.members().groups().contains("builders"));
        PolygonPrismShape polygon = (PolygonPrismShape) plot.shape();
        assertEquals(10.0d, polygon.minY());
        assertEquals(21.0d, polygon.maxY());
        assertTrue(plot.flags().isEmpty());
    }

    @Test
    void failedRefreshRetainsLastCompleteSnapshotAndCanRecover(){
        AtomicInteger calls = new AtomicInteger();
        WorldGuardReflectionFacade facade = () -> switch(calls.incrementAndGet()){
            case 1 -> validCapture(List.of(simpleCuboid("first", null, 0, 10)));
            case 2 -> throw new IllegalStateException("region manager reload failed");
            case 3 -> validCapture(List.of(
                    simpleCuboid("orphan", "missing-parent", 0, 10)
            ));
            default -> validCapture(List.of(simpleCuboid("recovered", null, 20, 30)));
        };
        ReflectiveWorldGuardProvider provider = new ReflectiveWorldGuardProvider(facade);

        RegionSnapshot successful = provider.refresh();
        // The constructor captured call 1; explicit refresh call 2 fails and returns that view.
        assertEquals(1L, successful.revision());
        assertFalse(provider.available());
        assertTrue(provider.failureReason().orElseThrow().contains("reload failed"));
        WorldId world = new WorldId(WORLD_UUID, "minecraft:worldguard_test");
        assertTrue(provider.regionKey(world, "FIRST").isPresent());

        RegionSnapshot invalidGraph = provider.refresh();
        assertSame(successful, invalidGraph);
        assertFalse(provider.available());
        assertTrue(provider.failureReason().orElseThrow().contains("missing"));
        assertTrue(provider.regionKey(world, "first").isPresent());

        RegionSnapshot recovered = provider.refresh();
        assertEquals(2L, recovered.revision());
        assertTrue(provider.available());
        assertTrue(provider.failureReason().isEmpty());
        assertTrue(recovered.graph().regions().stream()
                .anyMatch(region -> region.key().id().equals("recovered")));
    }

    @Test
    void duplicateWorldFailureNeverPublishesTheFirstWorldAsAPartialView(){
        AtomicInteger calls = new AtomicInteger();
        WorldGuardReflectionFacade facade = () -> {
            if(calls.incrementAndGet() == 1){
                return validCapture(List.of(simpleCuboid("stable", null, 0, 10)));
            }
            WorldGuardReflectionFacade.WorldView first = worldView(
                    "minecraft:first",
                    List.of(simpleCuboid("partial", null, 0, 10))
            );
            WorldGuardReflectionFacade.WorldView duplicate = worldView(
                    "minecraft:renamed",
                    List.of(simpleCuboid("other", null, 20, 30))
            );
            return new WorldGuardReflectionFacade.Capture(List.of(first, duplicate));
        };
        ReflectiveWorldGuardProvider provider = new ReflectiveWorldGuardProvider(facade);
        RegionSnapshot previous = provider.snapshot();

        RegionSnapshot failed = provider.refresh();
        assertSame(previous, failed);
        assertFalse(provider.available());
        assertTrue(provider.failureReason().orElseThrow().contains("duplicate world UUID"));
        assertTrue(failed.graph().regions().stream()
                .anyMatch(region -> region.key().id().equals("stable")));
        assertFalse(failed.graph().regions().stream()
                .anyMatch(region -> region.key().id().equals("partial")));
    }

    @Test
    void factorySupportsInjectedFacadeAndPluginManagerAbsence(){
        ReflectiveWorldGuardProvider injected = WorldGuardProviderFactory.create(
                () -> validCapture(List.of())
        );
        assertTrue(injected.available());

        PluginManager missing = proxy(PluginManager.class, (method, arguments) -> null);
        WorldGuardProvider unavailable = WorldGuardProviderFactory.create(missing);
        assertFalse(unavailable.available());
        assertTrue(unavailable.failureReason().orElseThrow().contains("not installed"));

        Plugin disabledPlugin = proxy(Plugin.class, (method, arguments) ->
                method.getName().equals("isEnabled") ? false : defaultValue(method.getReturnType())
        );
        PluginManager disabled = proxy(PluginManager.class, (method, arguments) ->
                method.getName().equals("getPlugin")
                        ? disabledPlugin
                        : defaultValue(method.getReturnType())
        );
        WorldGuardProvider disabledProvider = WorldGuardProviderFactory.create(disabled);
        assertFalse(disabledProvider.available());
        assertTrue(disabledProvider.failureReason().orElseThrow().contains("disabled"));
    }

    @Test
    void rawIdsResolveFromTheSamePinnedViewAndNonContainingParentsFlatten(){
        ReflectiveWorldGuardProvider provider = new ReflectiveWorldGuardProvider(
                () -> validCapture(List.of(
                        simpleCuboid("Parent", null, 0, 10),
                        simpleCuboid("Plot/One", "Parent", 20, 30)
                ))
        );
        WorldId world = new WorldId(WORLD_UUID, "minecraft:worldguard_test");

        var view = provider.view();
        RegionKey encoded = view.regionKey(world, "PLOT/ONE").orElseThrow();
        Region child = view.snapshot().graph().region(encoded).orElseThrow();

        assertTrue(encoded.id().startsWith("wg-encoded-"));
        assertEquals(RegionKey.global(world), child.parent().orElseThrow());
        assertTrue(provider.diagnostics().stream()
                .anyMatch(message -> message.contains("true containment")));
    }

    private static WorldGuardReflectionFacade.Capture validCapture(
            List<WorldGuardReflectionFacade.RegionView> regions) {
        return new WorldGuardReflectionFacade.Capture(List.of(
                worldView("minecraft:worldguard_test", regions)
        ));
    }

    private static WorldGuardReflectionFacade.WorldView worldView(
            String key,
            List<WorldGuardReflectionFacade.RegionView> regions) {
        return new WorldGuardReflectionFacade.WorldView(WORLD_UUID, key, regions);
    }

    private static WorldGuardReflectionFacade.RegionView simpleCuboid(
            String id,
            String parent,
            int min,
            int max) {
        return region(
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

    private static WorldGuardReflectionFacade.RegionView region(
            String id,
            int priority,
            String parent,
            Set<UUID> owners,
            Set<String> ownerGroups,
            Set<UUID> members,
            Set<String> memberGroups,
            WorldGuardReflectionFacade.ShapeView shape) {
        return new WorldGuardReflectionFacade.RegionView(
                id,
                priority,
                parent,
                owners,
                ownerGroups,
                members,
                memberGroups,
                shape
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> {
                    if(method.getDeclaringClass() == Object.class){
                        return switch(method.getName()){
                            case "toString" -> "test-" + type.getSimpleName();
                            case "hashCode" -> System.identityHashCode(instance);
                            case "equals" -> instance == arguments[0];
                            default -> null;
                        };
                    }
                    Object result = invocation.invoke(method, arguments);
                    return result != null ? result : defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if(!type.isPrimitive()){
            return null;
        }
        if(type == boolean.class){
            return false;
        }
        if(type == char.class){
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments);
    }
}
