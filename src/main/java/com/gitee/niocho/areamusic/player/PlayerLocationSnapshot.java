package com.gitee.niocho.areamusic.player;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable location data captured by a Bukkit event before async processing.
 */
public final class PlayerLocationSnapshot {
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;

    private PlayerLocationSnapshot(String worldName,
                                   double x,
                                   double y,
                                   double z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static PlayerLocationSnapshot from(Location location){
        if(location == null){
            return null;
        }
        World world = location.getWorld();
        if(world == null){
            return null;
        }
        return new PlayerLocationSnapshot(
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

}
