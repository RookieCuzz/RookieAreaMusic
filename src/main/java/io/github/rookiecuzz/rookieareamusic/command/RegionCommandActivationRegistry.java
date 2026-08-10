package io.github.rookiecuzz.rookieareamusic.command;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Matches one accepted enter-command activation with exactly one exit. */
public final class RegionCommandActivationRegistry {
    private final ConcurrentMap<UUID, ConcurrentMap<String, Long>> activations =
            new ConcurrentHashMap<>();

    public boolean hasActivation(UUID playerUuid, String areaUuid){
        if(playerUuid == null || areaUuid == null){
            return false;
        }
        ConcurrentMap<String, Long> playerActivations =
                activations.get(playerUuid);
        return playerActivations != null
                && playerActivations.containsKey(areaUuid);
    }

    public boolean activate(UUID playerUuid,
                            String areaUuid,
                            long actionToken){
        if(playerUuid == null || areaUuid == null || actionToken <= 0L){
            return false;
        }
        return activations.computeIfAbsent(
                playerUuid,
                ignored -> new ConcurrentHashMap<>()
        ).putIfAbsent(areaUuid, actionToken) == null;
    }

    public boolean consume(UUID playerUuid,
                           String areaUuid,
                           long actionToken){
        if(playerUuid == null || areaUuid == null || actionToken <= 0L){
            return false;
        }
        ConcurrentMap<String, Long> playerActivations =
                activations.get(playerUuid);
        if(playerActivations == null
                || !playerActivations.remove(
                        areaUuid,
                        Long.valueOf(actionToken)
                )){
            return false;
        }
        return true;
    }

    public void clearPlayer(UUID playerUuid){
        if(playerUuid != null){
            activations.remove(playerUuid);
        }
    }

    public void clearAll(){
        activations.clear();
    }
}
