package io.github.rookiecuzz.rookieregions.bukkit;

import io.github.rookiecuzz.rookieregions.core.WorldId;
import org.bukkit.World;

import java.util.Objects;

public final class BukkitWorlds {
    public static WorldId id(World world) {
        Objects.requireNonNull(world, "world cannot be null");
        return new WorldId(world.getUID(), world.getKey().toString());
    }

    private BukkitWorlds() {
    }
}
