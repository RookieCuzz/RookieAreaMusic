package io.github.rookiecuzz.rookieareamusic.spatial;

import io.github.rookiecuzz.rookieareamusic.config.AreaDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerRegionCache {
    private final ConcurrentMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    public List<AreaDto> get(UUID playerUuid,
                             String worldName,
                             double x,
                             double y,
                             double z){
        Entry entry = entries.get(playerUuid);
        if(entry == null || !entry.matches(worldName, x, y, z)){
            return null;
        }
        return entry.areas;
    }

    public void put(UUID playerUuid,
                    String worldName,
                    double x,
                    double y,
                    double z,
                    List<AreaDto> areas){
        if(playerUuid == null || worldName == null){
            return;
        }
        entries.put(
                playerUuid,
                new Entry(worldName, x, y, z, areas)
        );
    }

    public void clear(UUID playerUuid){
        if(playerUuid != null){
            entries.remove(playerUuid);
        }
    }

    public void clearAll(){
        entries.clear();
    }

    public int size(){
        return entries.size();
    }

    private static final class Entry {
        private final String worldName;
        private final long xBits;
        private final long yBits;
        private final long zBits;
        private final List<AreaDto> areas;

        private Entry(String worldName,
                      double x,
                      double y,
                      double z,
                      List<AreaDto> areas) {
            this.worldName = worldName;
            this.xBits = Double.doubleToLongBits(x);
            this.yBits = Double.doubleToLongBits(y);
            this.zBits = Double.doubleToLongBits(z);
            this.areas = Collections.unmodifiableList(
                    areas == null ? new ArrayList<>() : new ArrayList<>(areas)
            );
        }

        private boolean matches(String worldName, double x, double y, double z){
            return this.worldName.equals(worldName)
                    && this.xBits == Double.doubleToLongBits(x)
                    && this.yBits == Double.doubleToLongBits(y)
                    && this.zBits == Double.doubleToLongBits(z);
        }
    }
}
