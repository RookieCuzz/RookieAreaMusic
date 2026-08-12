package io.github.rookiecuzz.rookieareamusic.editor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Owns the independently toggled saved-region preview for each player. */
public final class RegionPreviewManager {
    public enum ToggleResult {
        SHOWN,
        HIDDEN
    }

    private final Map<UUID, Target> targets = new HashMap<>();

    /**
     * Shows or replaces a player's preview, unless the same target is already
     * shown, in which case that preview is hidden.
     */
    public synchronized ToggleResult toggle(UUID playerUuid,
                                            String worldName,
                                            String areaId){
        if(playerUuid == null){
            throw new IllegalArgumentException("玩家 UUID 不能为空");
        }
        if(isBlank(worldName) || isBlank(areaId)){
            throw new IllegalArgumentException("预览世界和区域 ID 不能为空");
        }

        Target requested = new Target(worldName, areaId);
        if(requested.equals(targets.get(playerUuid))){
            targets.remove(playerUuid);
            return ToggleResult.HIDDEN;
        }
        targets.put(playerUuid, requested);
        return ToggleResult.SHOWN;
    }

    public synchronized Target get(UUID playerUuid){
        return targets.get(playerUuid);
    }

    public synchronized boolean remove(UUID playerUuid){
        return targets.remove(playerUuid) != null;
    }

    public synchronized void clear(){
        targets.clear();
    }

    /** Returns a stable, read-only copy suitable for rendering iteration. */
    public synchronized Map<UUID, Target> snapshot(){
        return Collections.unmodifiableMap(new HashMap<>(targets));
    }

    private static boolean isBlank(String value){
        return value == null || value.trim().isEmpty();
    }

    public static final class Target {
        private final String worldName;
        private final String areaId;

        private Target(String worldName, String areaId) {
            this.worldName = worldName;
            this.areaId = areaId;
        }

        public String getWorldName(){
            return worldName;
        }

        public String getAreaId(){
            return areaId;
        }

        @Override
        public boolean equals(Object other){
            if(this == other){
                return true;
            }
            if(!(other instanceof Target)){
                return false;
            }
            Target target = (Target) other;
            return worldName.equals(target.worldName) && areaId.equals(target.areaId);
        }

        @Override
        public int hashCode(){
            return Objects.hash(worldName, areaId);
        }
    }
}
