package com.gitee.niocho.areamusic.async;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serializes work per player and prevents an older position task from
 * overwriting a newer result when Bukkit's async pool executes out of order.
 */
public final class PlayerTaskCoordinator {
    private final ConcurrentMap<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final AtomicLong revisionSequence = new AtomicLong();

    public long nextRevision(UUID playerUuid){
        long revision = revisionSequence.incrementAndGet();
        state(playerUuid).revision.set(revision);
        return revision;
    }

    public boolean runIfCurrent(UUID playerUuid, long revision, Runnable task){
        if(playerUuid == null || task == null){
            return false;
        }
        PlayerState state = state(playerUuid);
        synchronized (state.monitor){
            if(state.revision.get() != revision){
                return false;
            }
            task.run();
            return true;
        }
    }

    public void runSerialized(UUID playerUuid, Runnable task){
        if(playerUuid == null || task == null){
            return;
        }
        PlayerState state = state(playerUuid);
        synchronized (state.monitor){
            task.run();
        }
    }

    public void invalidate(UUID playerUuid){
        if(playerUuid != null){
            PlayerState state = state(playerUuid);
            synchronized (state.monitor){
                state.revision.set(revisionSequence.incrementAndGet());
            }
        }
    }

    public void invalidateAll(){
        for(PlayerState state : states.values()){
            synchronized (state.monitor){
                state.revision.set(revisionSequence.incrementAndGet());
            }
        }
    }

    public void remove(UUID playerUuid){
        if(playerUuid == null){
            return;
        }
        PlayerState state = states.get(playerUuid);
        if(state == null){
            return;
        }
        synchronized (state.monitor){
            state.revision.set(revisionSequence.incrementAndGet());
            states.remove(playerUuid, state);
        }
    }

    public void clear(){
        invalidateAll();
        states.clear();
    }

    public int size(){
        return states.size();
    }

    private PlayerState state(UUID playerUuid){
        if(playerUuid == null){
            throw new IllegalArgumentException("playerUuid 不能为空");
        }
        return states.computeIfAbsent(playerUuid, ignored -> new PlayerState());
    }

    private static final class PlayerState {
        private final Object monitor = new Object();
        private final AtomicLong revision = new AtomicLong();
    }
}
