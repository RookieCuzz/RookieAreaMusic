package io.github.rookiecuzz.rookieregions.provider;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Production reflection bridge. No WorldGuard or WorldEdit type is linked. */
final class BukkitWorldGuardReflectionFacade implements WorldGuardReflectionFacade {
    private static final String WORLD_GUARD_PLUGIN = "WorldGuard";
    private static final String WORLD_EDIT_PLUGIN = "WorldEdit";

    private final PluginManager pluginManager;
    private final Supplier<List<World>> loadedWorlds;

    BukkitWorldGuardReflectionFacade(PluginManager pluginManager,
                                     Supplier<List<World>> loadedWorlds) {
        this.pluginManager = Objects.requireNonNull(
                pluginManager,
                "Bukkit plugin manager cannot be null"
        );
        this.loadedWorlds = Objects.requireNonNull(
                loadedWorlds,
                "loaded-world supplier cannot be null"
        );
    }

    @Override
    public Capture capture() throws Exception {
        Plugin worldGuardPlugin = pluginManager.getPlugin(WORLD_GUARD_PLUGIN);
        if(worldGuardPlugin == null){
            throw new IllegalStateException("WorldGuard plugin is not installed");
        }
        if(!worldGuardPlugin.isEnabled()){
            throw new IllegalStateException("WorldGuard plugin is disabled");
        }

        Plugin worldEditPlugin = pluginManager.getPlugin(WORLD_EDIT_PLUGIN);
        ClassLoader worldGuardLoader = worldGuardPlugin.getClass().getClassLoader();
        ClassLoader worldEditLoader = worldEditPlugin == null
                ? worldGuardLoader
                : worldEditPlugin.getClass().getClassLoader();

        Class<?> worldGuardClass = loadClass(
                "com.sk89q.worldguard.WorldGuard",
                worldGuardLoader
        );
        Class<?> bukkitAdapterClass = loadClass(
                "com.sk89q.worldedit.bukkit.BukkitAdapter",
                worldEditLoader,
                worldGuardLoader
        );
        Class<?> globalRegionClass = loadClass(
                "com.sk89q.worldguard.protection.regions.GlobalProtectedRegion",
                worldGuardLoader
        );
        Class<?> cuboidRegionClass = loadClass(
                "com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion",
                worldGuardLoader
        );
        Class<?> polygonalRegionClass = loadClass(
                "com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion",
                worldGuardLoader
        );

        Object worldGuard = invokeStaticNoArgs(worldGuardClass, "getInstance");
        Object platform = invokeNoArgs(worldGuard, "getPlatform");
        Object regionContainer = invokeNoArgs(platform, "getRegionContainer");
        Method adaptWorld = bukkitAdapterClass.getMethod("adapt", World.class);

        List<World> worlds = immutableWorlds(loadedWorlds.get());
        ArrayList<WorldView> capturedWorlds = new ArrayList<>(worlds.size());
        for(World world : worlds){
            Object adaptedWorld = invoke(adaptWorld, null, world);
            Object regionManager = invokeCompatible(
                    regionContainer,
                    List.of("get", "getWorld"),
                    adaptedWorld
            );
            if(regionManager == null){
                throw new IllegalStateException(
                        "WorldGuard has no loaded region manager for " + world.getKey()
                );
            }
            Object rawRegions = invokeNoArgs(regionManager, "getRegions");
            if(!(rawRegions instanceof Map<?, ?> regionMap)){
                throw new IllegalStateException(
                        "WorldGuard RegionManager#getRegions did not return a map"
                );
            }

            ArrayList<RegionView> regions = new ArrayList<>(regionMap.size());
            for(Object protectedRegion : List.copyOf(regionMap.values())){
                regions.add(captureRegion(
                        protectedRegion,
                        globalRegionClass,
                        cuboidRegionClass,
                        polygonalRegionClass
                ));
            }
            capturedWorlds.add(new WorldView(
                    world.getUID(),
                    world.getKey().toString(),
                    regions
            ));
        }
        return new Capture(capturedWorlds);
    }

    private static RegionView captureRegion(Object region,
                                             Class<?> globalRegionClass,
                                             Class<?> cuboidRegionClass,
                                             Class<?> polygonalRegionClass) throws Exception {
        if(region == null){
            throw new IllegalStateException("WorldGuard region map contains null");
        }
        String id = requireString(invokeNoArgs(region, "getId"), "region ID");
        int priority = requireInt(invokeNoArgs(region, "getPriority"), "region priority");
        Object parent = invokeNoArgs(region, "getParent");
        String parentId = parent == null
                ? null
                : requireString(invokeNoArgs(parent, "getId"), "parent region ID");
        Domain owners = captureDomain(invokeNoArgs(region, "getOwners"));
        Domain members = captureDomain(invokeNoArgs(region, "getMembers"));

        ShapeView shape;
        if(globalRegionClass.isInstance(region)){
            shape = GlobalView.INSTANCE;
        } else if(cuboidRegionClass.isInstance(region)){
            Object minimum = invokeNoArgs(region, "getMinimumPoint");
            Object maximum = invokeNoArgs(region, "getMaximumPoint");
            shape = new CuboidView(
                    coordinate(minimum, "X"),
                    coordinate(minimum, "Y"),
                    coordinate(minimum, "Z"),
                    coordinate(maximum, "X"),
                    coordinate(maximum, "Y"),
                    coordinate(maximum, "Z")
            );
        } else if(polygonalRegionClass.isInstance(region)){
            Object minimum = invokeNoArgs(region, "getMinimumPoint");
            Object maximum = invokeNoArgs(region, "getMaximumPoint");
            Object rawPoints = invokeNoArgs(region, "getPoints");
            if(!(rawPoints instanceof Collection<?> points)){
                throw new IllegalStateException(
                        "WorldGuard polygon#getPoints did not return a collection"
                );
            }
            ArrayList<PointView> capturedPoints = new ArrayList<>(points.size());
            for(Object point : points){
                capturedPoints.add(new PointView(
                        coordinate(point, "X"),
                        coordinate(point, "Z")
                ));
            }
            shape = new PolygonalView(
                    coordinate(minimum, "Y"),
                    coordinate(maximum, "Y"),
                    capturedPoints
            );
        } else {
            throw new IllegalStateException(
                    "unsupported WorldGuard region type " + region.getClass().getName()
            );
        }

        return new RegionView(
                id,
                priority,
                parentId,
                owners.players(),
                owners.groups(),
                members.players(),
                members.groups(),
                shape
        );
    }

    private static Domain captureDomain(Object domain) throws Exception {
        if(domain == null){
            throw new IllegalStateException("WorldGuard domain cannot be null");
        }
        Object rawPlayers = invokeNoArgs(domain, "getUniqueIds");
        Object rawGroups = invokeNoArgs(domain, "getGroups");
        if(!(rawPlayers instanceof Collection<?> players)
                || !(rawGroups instanceof Collection<?> groups)){
            throw new IllegalStateException(
                    "WorldGuard domain accessors did not return collections"
            );
        }

        LinkedHashSet<UUID> playerIds = new LinkedHashSet<>();
        for(Object player : players){
            if(!(player instanceof UUID uuid)){
                throw new IllegalStateException(
                        "WorldGuard owner/member UUID collection contains " + player
                );
            }
            playerIds.add(uuid);
        }
        LinkedHashSet<String> groupIds = new LinkedHashSet<>();
        for(Object group : groups){
            groupIds.add(requireString(group, "domain group"));
        }
        return new Domain(Set.copyOf(playerIds), Set.copyOf(groupIds));
    }

    private static int coordinate(Object vector, String axis) throws Exception {
        if(vector == null){
            throw new IllegalStateException("WorldEdit vector cannot be null");
        }
        Object value = invokeFirstNoArgs(
                vector,
                List.of("getBlock" + axis, axis.toLowerCase(), "get" + axis)
        );
        return requireInt(value, "vector " + axis);
    }

    private static List<World> immutableWorlds(List<World> worlds) {
        if(worlds == null){
            throw new IllegalStateException("Bukkit returned a null loaded-world list");
        }
        try {
            return List.copyOf(worlds);
        } catch(NullPointerException exception){
            throw new IllegalStateException(
                    "Bukkit loaded-world list contains null",
                    exception
            );
        }
    }

    private static Class<?> loadClass(String name,
                                      ClassLoader... candidates) throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        for(ClassLoader candidate : candidates){
            if(candidate == null){
                continue;
            }
            try {
                return Class.forName(name, false, candidate);
            } catch(ClassNotFoundException exception){
                failure = exception;
            }
        }
        throw failure == null ? new ClassNotFoundException(name) : failure;
    }

    private static Object invokeStaticNoArgs(Class<?> type, String name) throws Exception {
        Method method = type.getMethod(name);
        if(!Modifier.isStatic(method.getModifiers())){
            throw new NoSuchMethodException(type.getName() + "#" + name + " is not static");
        }
        return invoke(method, null);
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        return invoke(target.getClass().getMethod(name), target);
    }

    private static Object invokeFirstNoArgs(Object target,
                                            List<String> names) throws Exception {
        NoSuchMethodException failure = null;
        for(String name : names){
            try {
                return invokeNoArgs(target, name);
            } catch(NoSuchMethodException exception){
                failure = exception;
            }
        }
        throw failure == null
                ? new NoSuchMethodException("no compatible no-arg method on " + target.getClass())
                : failure;
    }

    private static Object invokeCompatible(Object target,
                                           List<String> names,
                                           Object argument) throws Exception {
        for(String name : names){
            for(Method method : target.getClass().getMethods()){
                if(method.getName().equals(name)
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(argument)){
                    return invoke(method, target, argument);
                }
            }
        }
        throw new NoSuchMethodException(
                "no compatible " + names + " method on " + target.getClass().getName()
        );
    }

    private static Object invoke(Method method,
                                 Object target,
                                 Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch(InvocationTargetException exception){
            Throwable cause = exception.getCause();
            if(cause instanceof Exception checked){
                throw checked;
            }
            if(cause instanceof Error error){
                throw error;
            }
            throw new ReflectiveOperationException(
                    "reflection target failed without an Exception",
                    cause
            );
        }
    }

    private static String requireString(Object value, String name) {
        if(!(value instanceof String string) || string.isBlank()){
            throw new IllegalStateException(name + " is not a non-blank string");
        }
        return string;
    }

    private static int requireInt(Object value, String name) {
        if(!(value instanceof Number number)){
            throw new IllegalStateException(name + " is not numeric");
        }
        long result = number.longValue();
        if(result < Integer.MIN_VALUE || result > Integer.MAX_VALUE){
            throw new IllegalStateException(name + " is outside the integer range");
        }
        return (int) result;
    }

    private record Domain(Set<UUID> players, Set<String> groups) {
    }
}
