package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExplosionProtectionListenerTest {
    private static final UUID WORLD_UUID = UUID.fromString(
            "00000000-0000-0000-0000-000000000701"
    );

    @Test
    void pureResponsibilityTraversalFindsAPlayerAndStopsOnCycles(){
        Node player = new Node(true);
        Node projectile = new Node(false);
        Node tnt = new Node(false);
        projectile.parent = player;
        tnt.parent = projectile;

        assertSame(player, ResponsiblePlayerResolver.resolve(
                tnt,
                node -> node.player ? node : null,
                node -> node.parent
        ));

        Node first = new Node(false);
        Node second = new Node(false);
        first.parent = second;
        second.parent = first;
        assertNull(ResponsiblePlayerResolver.resolve(
                first,
                node -> node.player ? node : null,
                node -> node.parent
        ));
    }

    @Test
    void entityExplosionRoutesTheResponsiblePlayersBypass(){
        World world = world();
        WorldId worldId = new WorldId(WORLD_UUID, "minecraft:overworld");
        Region global = Region.builder(
                        RegionKey.global(worldId), GlobalShape.INSTANCE
                )
                .flag(ProtectionFlags.EXPLOSION, State.DENY)
                .build();
        ProtectionService protection = new ProtectionService(
                () -> RegionSnapshot.of(1L, List.of(global)),
                () -> false
        );
        ExplosionProtectionListener listener = new ExplosionProtectionListener(
                protection
        );
        Block affected = block(world, 3, 4, 5);
        Player bypassingPlayer = proxy(Player.class, (method, arguments) -> {
            if(method.getName().equals("hasPermission")){
                return "rookieregions.bypass.explosion".equals(arguments[0]);
            }
            return defaultValue(method.getReturnType());
        });

        ArrayList<Block> bypassedBlocks = new ArrayList<>(List.of(affected));
        listener.onEntityExplosion(event(
                world,
                tnt(bypassingPlayer),
                bypassedBlocks
        ));
        assertEquals(List.of(affected), bypassedBlocks);

        ArrayList<Block> environmentalBlocks = new ArrayList<>(List.of(affected));
        listener.onEntityExplosion(event(world, tnt(null), environmentalBlocks));
        assertEquals(List.of(), environmentalBlocks);
    }

    private static EntityExplodeEvent event(World world,
                                            Entity source,
                                            List<Block> blocks){
        return new EntityExplodeEvent(
                source,
                new Location(world, 0, 0, 0),
                blocks,
                1.0f,
                ExplosionResult.DESTROY
        );
    }

    private static TNTPrimed tnt(Entity source){
        return proxy(TNTPrimed.class, (method, arguments) ->
                method.getName().equals("getSource")
                        ? source
                        : defaultValue(method.getReturnType())
        );
    }

    private static World world(){
        return proxy(World.class, (method, arguments) -> switch(method.getName()){
            case "getUID" -> WORLD_UUID;
            case "getKey" -> NamespacedKey.minecraft("overworld");
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Block block(World world, int x, int y, int z){
        return proxy(Block.class, (method, arguments) -> switch(method.getName()){
            case "getWorld" -> world;
            case "getX" -> x;
            case "getY" -> y;
            case "getZ" -> z;
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation){
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> {
                    if(method.getDeclaringClass() == Object.class){
                        return switch(method.getName()){
                            case "equals" -> instance == arguments[0];
                            case "hashCode" -> System.identityHashCode(instance);
                            case "toString" -> "proxy(" + type.getSimpleName() + ")";
                            default -> null;
                        };
                    }
                    return invocation.invoke(method, arguments);
                }
        );
    }

    private static Object defaultValue(Class<?> type){
        if(!type.isPrimitive()){
            return null;
        }
        if(type == boolean.class){
            return false;
        }
        if(type == char.class){
            return '\0';
        }
        if(type == byte.class){
            return (byte) 0;
        }
        if(type == short.class){
            return (short) 0;
        }
        if(type == int.class){
            return 0;
        }
        if(type == long.class){
            return 0L;
        }
        if(type == float.class){
            return 0.0f;
        }
        return 0.0d;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments);
    }

    private static final class Node {
        private final boolean player;
        private Node parent;

        private Node(boolean player){
            this.player = player;
        }
    }
}
