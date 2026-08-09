package com.gitee.niocho.areamusic.source;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Main-thread scheduler for fixed-position, naturally attenuated sounds. */
public final class SoundSourcePlaybackEngine {
    private static final long FAILED_PLAY_RETRY_MILLIS = 1000L;
    private final Map<String, SourceState> states = new HashMap<>();

    public void tick(Collection<SoundSource> sources,
                     long now,
                     SoundSourceSink sink){
        if(sink == null){
            throw new IllegalArgumentException("音源输出不能为空");
        }

        Map<String, SoundSource> currentSources = new HashMap<>();
        if(sources != null){
            for(SoundSource source : sources){
                if(source == null || !source.isEnabled()){
                    continue;
                }
                currentSources.put(source.getUuid(), source);
            }
        }

        for(SourceState state : states.values()){
            state.expirePlayback(now);
        }

        Set<String> interruptedPlaybackKeys = new HashSet<>();
        Iterator<Map.Entry<String, SourceState>> iterator = states.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<String, SourceState> entry = iterator.next();
            SourceState state = entry.getValue();
            SoundSource current = currentSources.get(entry.getKey());
            if(current == null || !state.source.hasSamePlaybackSettings(current)){
                String playbackKey = playbackKey(state.source);
                if(state.playing && interruptedPlaybackKeys.add(playbackKey)){
                    sink.stop(state.source);
                }
                iterator.remove();
            }
        }

        for(SoundSource source : currentSources.values()){
            states.computeIfAbsent(
                    source.getUuid(),
                    ignored -> new SourceState(source, now)
            );
        }
        if(!interruptedPlaybackKeys.isEmpty()){
            for(SourceState state : states.values()){
                if(interruptedPlaybackKeys.contains(playbackKey(state.source))
                        && state.playing){
                    state.interruptPlayback(now);
                }
            }
        }

        for(SoundSource source : currentSources.values()){
            SourceState state = states.get(source.getUuid());
            if(now < state.nextPlayAt){
                continue;
            }
            if(sink.play(source)){
                state.playing = true;
                state.playingUntil = safeAdd(
                        now,
                        safeSecondsMillis(source.getDurationSeconds())
                );
                state.nextPlayAt = safeAdd(
                        now,
                        safeCycleMillis(
                                source.getDurationSeconds(),
                                source.getIntervalSeconds()
                        )
                );
            } else {
                state.playing = false;
                state.playingUntil = now;
                state.nextPlayAt = safeAdd(now, FAILED_PLAY_RETRY_MILLIS);
            }
        }
    }

    public void clear(){
        states.clear();
    }

    public void clear(SoundSourceSink sink){
        if(sink == null){
            clear();
            return;
        }
        Set<String> stoppedPlaybackKeys = new HashSet<>();
        for(SourceState state : states.values()){
            String playbackKey = playbackKey(state.source);
            if(state.playing && stoppedPlaybackKeys.add(playbackKey)){
                sink.stop(state.source);
            }
        }
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

    private long safeSecondsMillis(long seconds){
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

    private String playbackKey(SoundSource source){
        return source.getWorldName() + '\u0000' + source.getSoundKey();
    }

    private static final class SourceState {
        private final SoundSource source;
        private long nextPlayAt;
        private long playingUntil;
        private boolean playing;

        private SourceState(SoundSource source, long nextPlayAt) {
            this.source = source;
            this.nextPlayAt = nextPlayAt;
        }

        private void expirePlayback(long now){
            if(playing && now >= playingUntil){
                playing = false;
            }
        }

        private void interruptPlayback(long now){
            playing = false;
            playingUntil = now;
            nextPlayAt = now;
        }
    }
}
