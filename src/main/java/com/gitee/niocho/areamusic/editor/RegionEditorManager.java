package com.gitee.niocho.areamusic.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns player sessions and exclusive locks for edited regions. */
public final class RegionEditorManager {
    private final Map<UUID, RegionEditSession> sessions = new HashMap<>();
    private final Map<String, UUID> regionLocks = new HashMap<>();

    public synchronized void begin(UUID playerUuid, RegionEditSession session){
        if(playerUuid == null || session == null){
            throw new IllegalArgumentException("玩家和编辑会话不能为空");
        }
        if(sessions.containsKey(playerUuid)){
            throw new IllegalStateException("你已经处于区域编辑模式");
        }
        String key = regionKey(session.getWorldName(), session.getAreaId());
        UUID owner = regionLocks.get(key);
        if(owner != null && !owner.equals(playerUuid)){
            throw new IllegalStateException("该区域正在被其他管理员编辑");
        }
        sessions.put(playerUuid, session);
        regionLocks.put(key, playerUuid);
    }

    public synchronized RegionEditSession get(UUID playerUuid){
        return sessions.get(playerUuid);
    }

    public synchronized RegionEditSession end(UUID playerUuid){
        RegionEditSession removed = sessions.remove(playerUuid);
        if(removed != null){
            regionLocks.remove(regionKey(removed.getWorldName(), removed.getAreaId()), playerUuid);
        }
        return removed;
    }

    public synchronized Collection<Map.Entry<UUID, RegionEditSession>> snapshot(){
        return new ArrayList<>(sessions.entrySet());
    }

    public synchronized int size(){
        return sessions.size();
    }

    public synchronized boolean isLocked(String worldName, String areaId){
        return regionLocks.containsKey(regionKey(worldName, areaId));
    }

    public synchronized void clear(){
        sessions.clear();
        regionLocks.clear();
    }

    private static String regionKey(String worldName, String areaId){
        return worldName + '\u0000' + areaId;
    }
}
