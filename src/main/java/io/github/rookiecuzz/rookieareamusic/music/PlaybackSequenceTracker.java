package io.github.rookiecuzz.rookieareamusic.music;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaybackSequenceTracker {
    private final Map<UUID, Map<String, Integer>> positions = new ConcurrentHashMap<>();

    public int next(UUID playerUuid, String areaUuid, int playlistSize){
        if(playerUuid == null || areaUuid == null || areaUuid.isEmpty()){
            throw new IllegalArgumentException("playerUuid 和 areaUuid 不能为空");
        }
        if(playlistSize <= 0){
            throw new IllegalArgumentException("playlistSize 必须大于 0");
        }

        Map<String, Integer> playerPositions = positions.computeIfAbsent(
                playerUuid,
                ignored -> new ConcurrentHashMap<>()
        );
        synchronized (playerPositions){
            int current = playerPositions.getOrDefault(areaUuid, 0);
            int selected = Math.floorMod(current, playlistSize);
            playerPositions.put(areaUuid, (selected + 1) % playlistSize);
            return selected;
        }
    }

    public void clear(UUID playerUuid){
        if(playerUuid != null){
            positions.remove(playerUuid);
        }
    }

    public void clearAll(){
        positions.clear();
    }
}
