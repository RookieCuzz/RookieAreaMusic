package com.gitee.niocho.areamusic.source;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Main-thread scheduler for fixed-position, naturally attenuated sounds. */
public final class SoundSourcePlaybackEngine {
    private final Map<String, SourceState> states = new HashMap<>();

    public void tick(Collection<SoundSource> sources,
                     long now,
                     SoundSourceSink sink){
        if(sink == null){
            throw new IllegalArgumentException("音源输出不能为空");
        }

        Set<String> currentIds = new HashSet<>();
        if(sources != null){
            for(SoundSource source : sources){
                if(source == null || !source.isEnabled()){
                    continue;
                }
                currentIds.add(source.getUuid());
                SourceState state = states.get(source.getUuid());
                if(state == null
                        || !state.source.hasSamePlaybackSettings(source)){
                    state = new SourceState(source, now);
                    states.put(source.getUuid(), state);
                }
                if(now < state.nextPlayAt){
                    continue;
                }

                sink.play(source);
                state.nextPlayAt = safeAdd(
                        now,
                        safeCycleMillis(
                                source.getDurationSeconds(),
                                source.getIntervalSeconds()
                        )
                );
            }
        }

        Iterator<String> iterator = states.keySet().iterator();
        while(iterator.hasNext()){
            if(!currentIds.contains(iterator.next())){
                iterator.remove();
            }
        }
    }

    public void clear(){
        states.clear();
    }

    public int size(){
        return states.size();
    }

    private long safeCycleMillis(long durationSeconds,
                                 long intervalSeconds){
        long seconds;
        if(durationSeconds > Long.MAX_VALUE - intervalSeconds){
            seconds = Long.MAX_VALUE;
        } else {
            seconds = durationSeconds + intervalSeconds;
        }
        if(seconds > Long.MAX_VALUE / 1000L){
            return Long.MAX_VALUE;
        }
        return seconds * 1000L;
    }

    private long safeAdd(long now, long delay){
        if(delay > Long.MAX_VALUE - now){
            return Long.MAX_VALUE;
        }
        return now + delay;
    }

    private static final class SourceState {
        private final SoundSource source;
        private long nextPlayAt;

        private SourceState(SoundSource source, long nextPlayAt) {
            this.source = source;
            this.nextPlayAt = nextPlayAt;
        }
    }
}
