package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitProtectionEventRoutingTest {
    @Test
    void moveTeleportAndPortalHaveIndependentNonDuplicatingHandlers() {
        assertEquals(
                RegionTransitionListener.MovementRoute.MOVE,
                RegionTransitionListener.movementRoute(PlayerMoveEvent.class)
        );
        assertEquals(
                RegionTransitionListener.MovementRoute.TELEPORT,
                RegionTransitionListener.movementRoute(PlayerTeleportEvent.class)
        );
        assertEquals(
                RegionTransitionListener.MovementRoute.PORTAL,
                RegionTransitionListener.movementRoute(PlayerPortalEvent.class)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RegionTransitionListener.movementRoute(String.class)
        );

        assertMovementHandlers(PlayerMoveEvent.class);
        assertMovementHandlers(PlayerTeleportEvent.class);
        assertMovementHandlers(PlayerPortalEvent.class);
    }

    @Test
    void preciseEntityInteractionHasItsOwnHandlerAndSkipsTheBaseRoute() {
        assertFalse(UseProtectionListener.isPreciseEntityInteraction(
                PlayerInteractEntityEvent.class
        ));
        assertTrue(UseProtectionListener.isPreciseEntityInteraction(
                PlayerInteractAtEntityEvent.class
        ));

        Method base = soleHandler(
                UseProtectionListener.class,
                PlayerInteractEntityEvent.class,
                EventPriority.HIGHEST
        );
        Method precise = soleHandler(
                UseProtectionListener.class,
                PlayerInteractAtEntityEvent.class,
                EventPriority.HIGHEST
        );
        assertTrue(base.getAnnotation(EventHandler.class).ignoreCancelled());
        assertTrue(precise.getAnnotation(EventHandler.class).ignoreCancelled());
        assertTrue(UseProtectionListener.usesContainerRule(new TestHolder()));
        assertFalse(UseProtectionListener.usesContainerRule(new Object()));
    }

    @Test
    void entryRuleRunsOnlyWhenMovementAddsAPhysicalRegion() {
        WorldId world = new WorldId(
                UUID.fromString("00000000-0000-0000-0000-000000000771"),
                "minecraft:test"
        );
        Region global = Region.builder(
                RegionKey.global(world), GlobalShape.INSTANCE
        ).build();
        Region local = Region.builder(
                        new RegionKey(world, "local"),
                        new CuboidShape(0, 0, 0, 10, 10, 10)
                )
                .parent(global.key())
                .build();
        RegionQuery query = new RegionQuery(RegionSnapshot.of(
                1L, List.of(global, local)
        ));
        var outside = query.at(world, 20, 5, 20);
        var inside = query.at(world, 5, 5, 5);

        assertTrue(RegionTransitionListener.entersNewLocal(outside, inside));
        assertFalse(RegionTransitionListener.entersNewLocal(inside, inside));
        assertFalse(RegionTransitionListener.entersNewLocal(inside, outside));
    }

    private static void assertMovementHandlers(Class<?> eventType) {
        Method enforcement = soleHandler(
                RegionTransitionListener.class,
                eventType,
                EventPriority.HIGHEST
        );
        Method observation = soleHandler(
                RegionTransitionListener.class,
                eventType,
                EventPriority.MONITOR
        );
        assertTrue(enforcement.getAnnotation(EventHandler.class).ignoreCancelled());
        assertTrue(observation.getAnnotation(EventHandler.class).ignoreCancelled());
    }

    private static Method soleHandler(Class<?> listener,
                                      Class<?> eventType,
                                      EventPriority priority) {
        Method[] matches = Arrays.stream(listener.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventHandler.class))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0] == eventType)
                .filter(method -> method.getAnnotation(EventHandler.class).priority()
                        == priority)
                .toArray(Method[]::new);
        assertEquals(
                1,
                matches.length,
                () -> "expected one " + priority + " handler for "
                        + eventType.getSimpleName()
        );
        return matches[0];
    }

    private static final class TestHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
