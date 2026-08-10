package io.github.rookiecuzz.rookieareamusic.music;

import io.github.rookiecuzz.rookieareamusic.config.AreaDto;
import io.github.rookiecuzz.rookieareamusic.config.ChannelMode;
import io.github.rookiecuzz.rookieareamusic.config.ChannelTrigger;
import io.github.rookiecuzz.rookieareamusic.config.PlaybackChannelConfig;
import io.github.rookiecuzz.rookieareamusic.config.PlaybackChannelRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player logical playback state. Callers serialize access for each player.
 */
public final class PlayerPlaybackSession {
    public static final Comparator<AreaDto> AREA_ORDER = (first, second) -> {
        int priority = Integer.compare(
                priorityValue(second),
                priorityValue(first)
        );
        if(priority != 0){
            return priority;
        }
        int order = Integer.compare(orderValue(second), orderValue(first));
        if(order != 0){
            return order;
        }
        return stableAreaId(first).compareTo(stableAreaId(second));
    };

    private final Map<String, RegionState> regions = new HashMap<>();
    private final Map<String, SoundGroup> sounds = new HashMap<>();
    private final Map<String, EntryActivation> entryActivations =
            new HashMap<>();
    private final Set<String> previousInside = new HashSet<>();
    private long nextActionToken = 1L;

    public void reconcile(List<AreaDto> insideAreas,
                          PlaybackChannelRegistry channels,
                          long now,
                          TrackSelector selector,
                          PlaybackSink sink){
        if(channels == null || selector == null || sink == null){
            throw new IllegalArgumentException("播放状态机参数不能为空");
        }

        Map<String, AreaDto> insideById = normalizeInside(insideAreas);
        Set<String> forceEntered = updateExistingStates(
                insideById,
                channels,
                sink
        );
        emitExitedCommands(insideById.keySet(), sink);
        expireSounds(now);

        Map<String, List<AreaDto>> byChannel = groupByChannel(
                insideById.values(),
                channels
        );
        Map<String, AreaDto> desired = new LinkedHashMap<>();
        Set<String> actualEntered = new HashSet<>();
        for(String areaUuid : insideById.keySet()){
            if(!previousInside.contains(areaUuid)){
                actualEntered.add(areaUuid);
            }
        }
        Set<String> enteredForPlayback = new HashSet<>(actualEntered);
        enteredForPlayback.addAll(forceEntered);

        for(Map.Entry<String, List<AreaDto>> channelEntry : byChannel.entrySet()){
            String channelName = channelEntry.getKey();
            List<AreaDto> candidates = channelEntry.getValue();
            PlaybackChannelConfig channel = channels.require(channelName);
            candidates.sort(AREA_ORDER);

            List<AreaDto> eligible = eligibleCandidates(
                    channelName,
                    candidates,
                    channel,
                    enteredForPlayback
            );
            if(channel.getMode() == ChannelMode.EXCLUSIVE){
                AreaDto selected = selectExclusive(channelName, eligible);
                if(selected != null){
                    desired.put(selected.getUuid(), selected);
                }
            } else {
                int limit = Math.min(channel.getMaxLayers(), eligible.size());
                for(int index = 0; index < limit; index++){
                    AreaDto selected = eligible.get(index);
                    desired.put(selected.getUuid(), selected);
                }
            }

            if(channel.getTrigger() == ChannelTrigger.ENTER_ONCE){
                for(AreaDto candidate : candidates){
                    if(enteredForPlayback.contains(candidate.getUuid())
                            && !desired.containsKey(candidate.getUuid())){
                        RegionState suppressed = stateFor(
                                candidate,
                                channel.getTrigger()
                        );
                        suppressed.selected = false;
                        suppressed.completed = true;
                    }
                }
            }
        }

        applyDesired(
                desired,
                channels,
                selector,
                sink,
                now,
                actualEntered
        );
        previousInside.clear();
        previousInside.addAll(insideById.keySet());
    }

    /**
     * Restarts physical playback without turning players who are already
     * inside a region into new entrants. In particular, enter-once commands
     * stay consumed while active custom sounds can be replayed after a
     * resource pack becomes available.
     */
    public void restartPlayback(PlaybackSink sink, long now){
        // A resource-pack callback can race the normal scan immediately after
        // a stinger expires. Settle expiry first so an already completed
        // enter-once sound cannot be revived by the restart.
        expireSounds(now);
        if(sink != null){
            for(String soundKey : new ArrayList<>(sounds.keySet())){
                sink.stop(soundKey);
            }
        }
        sounds.clear();
        for(RegionState state : regions.values()){
            state.track = null;
        }
    }

    public void clear(PlaybackSink sink){
        if(sink != null){
            for(String soundKey : new ArrayList<>(sounds.keySet())){
                sink.stop(soundKey);
            }
        }
        sounds.clear();
        regions.clear();
        entryActivations.clear();
        previousInside.clear();
    }

    public int getActiveSoundCount(){
        return sounds.size();
    }

    public int getSoundReferenceCount(String soundKey){
        SoundGroup group = sounds.get(soundKey);
        return group == null ? 0 : group.regionUuids.size();
    }

    public boolean isRegionCompleted(String areaUuid){
        RegionState state = regions.get(areaUuid);
        return state != null && state.completed;
    }

    public boolean isRegionSelected(String areaUuid){
        RegionState state = regions.get(areaUuid);
        return state != null && state.selected;
    }

    private Map<String, AreaDto> normalizeInside(List<AreaDto> insideAreas){
        Map<String, AreaDto> result = new LinkedHashMap<>();
        if(insideAreas == null){
            return result;
        }
        for(AreaDto area : insideAreas){
            if(area != null && area.getUuid() != null){
                result.put(area.getUuid(), area);
            }
        }
        return result;
    }

    private Set<String> updateExistingStates(
            Map<String, AreaDto> insideById,
            PlaybackChannelRegistry channels,
            PlaybackSink sink){
        Set<String> forceEntered = new HashSet<>();
        Iterator<Map.Entry<String, RegionState>> iterator =
                regions.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<String, RegionState> entry = iterator.next();
            RegionState state = entry.getValue();
            AreaDto currentArea = insideById.get(entry.getKey());
            if(currentArea == null){
                releaseTrack(state, sink);
                iterator.remove();
                continue;
            }

            EntryActivation activation = entryActivations.get(
                    currentArea.getUuid()
            );
            if(activation != null){
                activation.area = currentArea;
            }

            PlaybackChannelConfig currentChannel = channels.require(
                    currentArea.getChannel()
            );
            if(!state.channelName.equals(currentArea.getChannel())
                    || state.trigger != currentChannel.getTrigger()){
                releaseTrack(state, sink);
                iterator.remove();
                forceEntered.add(currentArea.getUuid());
                continue;
            }
            state.area = currentArea;
        }
        return forceEntered;
    }

    private void expireSounds(long now){
        List<SoundGroup> expired = new ArrayList<>();
        for(SoundGroup group : sounds.values()){
            if(now >= group.expiresAt){
                expired.add(group);
            }
        }

        for(SoundGroup group : expired){
            sounds.remove(group.soundKey);
            for(String areaUuid : group.regionUuids){
                RegionState state = regions.get(areaUuid);
                if(state == null){
                    continue;
                }
                state.track = null;
                if(state.trigger == ChannelTrigger.CONTINUOUS
                        && Boolean.TRUE.equals(state.area.getLoop())){
                    state.completed = false;
                } else {
                    state.completed = true;
                }
            }
        }
    }

    private Map<String, List<AreaDto>> groupByChannel(
            Collection<AreaDto> areas,
            PlaybackChannelRegistry channels){
        Map<String, List<AreaDto>> result = new LinkedHashMap<>();
        for(AreaDto area : areas){
            channels.require(area.getChannel());
            result.computeIfAbsent(
                    area.getChannel(),
                    ignored -> new ArrayList<>()
            ).add(area);
        }
        return result;
    }

    private List<AreaDto> eligibleCandidates(
            String channelName,
            List<AreaDto> candidates,
            PlaybackChannelConfig channel,
            Set<String> newlyEntered){
        if(channel.getTrigger() == ChannelTrigger.CONTINUOUS){
            if(channel.getMode() == ChannelMode.EXCLUSIVE){
                return new ArrayList<>(candidates);
            }
            List<AreaDto> result = new ArrayList<>();
            for(AreaDto candidate : candidates){
                RegionState state = regions.get(candidate.getUuid());
                if(state == null || !state.completed){
                    result.add(candidate);
                }
            }
            return result;
        }

        List<AreaDto> result = new ArrayList<>();
        for(AreaDto candidate : candidates){
            RegionState state = regions.get(candidate.getUuid());
            boolean active = state != null
                    && state.selected
                    && !state.completed;
            if(active || newlyEntered.contains(candidate.getUuid())){
                result.add(candidate);
            }
        }
        result.sort(AREA_ORDER);
        return result;
    }

    private AreaDto selectExclusive(String channelName,
                                    List<AreaDto> candidates){
        if(candidates.isEmpty()){
            return null;
        }
        candidates.sort(AREA_ORDER);
        AreaDto top = candidates.get(0);
        RegionState current = currentExclusiveState(channelName, candidates);
        if(current == null || current.area.getUuid().equals(top.getUuid())){
            return top;
        }
        if(Boolean.TRUE.equals(top.getOverWrite())
                && priorityValue(top) > priorityValue(current.area)){
            return top;
        }
        return current.area;
    }

    private RegionState currentExclusiveState(String channelName,
                                              List<AreaDto> candidates){
        Set<String> candidateIds = new HashSet<>();
        for(AreaDto candidate : candidates){
            candidateIds.add(candidate.getUuid());
        }
        List<RegionState> selected = new ArrayList<>();
        for(RegionState state : regions.values()){
            if(state.selected
                    && state.channelName.equals(channelName)
                    && candidateIds.contains(state.area.getUuid())){
                selected.add(state);
            }
        }
        if(selected.isEmpty()){
            return null;
        }
        selected.sort((first, second) -> AREA_ORDER.compare(
                first.area,
                second.area
        ));
        return selected.get(0);
    }

    private void applyDesired(Map<String, AreaDto> desired,
                              PlaybackChannelRegistry channels,
                              TrackSelector selector,
                              PlaybackSink sink,
                              long now,
                              Set<String> actualEntered){
        List<String> removeStates = new ArrayList<>();
        for(RegionState state : new ArrayList<>(regions.values())){
            if(!state.selected || desired.containsKey(state.area.getUuid())){
                continue;
            }
            releaseTrack(state, sink);
            state.selected = false;
            if(state.trigger == ChannelTrigger.ENTER_ONCE){
                state.completed = true;
            } else if(!state.completed){
                removeStates.add(state.area.getUuid());
            }
        }
        for(String areaUuid : removeStates){
            regions.remove(areaUuid);
        }

        List<RegionState> toStart = new ArrayList<>();
        for(AreaDto area : desired.values()){
            PlaybackChannelConfig channel = channels.require(area.getChannel());
            RegionState state = stateFor(area, channel.getTrigger());
            state.selected = true;
            if(state.track == null && !state.completed){
                toStart.add(state);
            }
        }
        toStart.sort((first, second) -> AREA_ORDER.compare(
                first.area,
                second.area
        ));

        for(RegionState state : toStart){
            boolean hasConfiguredMusic = state.area.getMusicId() != null
                    && !state.area.getMusicId().isEmpty();
            boolean hasEnterCommands = state.area.getEnterCommands() != null
                    && !state.area.getEnterCommands().isEmpty();
            if(!hasConfiguredMusic){
                if(state.trigger == ChannelTrigger.ENTER_ONCE){
                    emitEnterCommandsIfNeeded(
                            state,
                            actualEntered,
                            hasEnterCommands,
                            sink
                    );
                    state.completed = true;
                }
                continue;
            }

            SelectedTrack selected = selector.select(state.area);
            if(selected == null
                    || selected.getSoundKey() == null
                    || selected.getSoundKey().trim().isEmpty()
                    || selected.getDurationMillis() <= 0){
                if(state.trigger == ChannelTrigger.ENTER_ONCE){
                    state.completed = true;
                }
                continue;
            }
            state.track = selected;
            SoundGroup group = sounds.get(selected.getSoundKey());
            if(group == null){
                group = new SoundGroup(
                        selected.getSoundKey(),
                        safeExpiry(now, selected.getDurationMillis())
                );
                sounds.put(selected.getSoundKey(), group);
                sink.play(selected);
            }
            group.regionUuids.add(state.area.getUuid());
            emitEnterCommandsIfNeeded(
                    state,
                    actualEntered,
                    hasEnterCommands,
                    sink
            );
        }

        Iterator<Map.Entry<String, RegionState>> iterator =
                regions.entrySet().iterator();
        while(iterator.hasNext()){
            RegionState state = iterator.next().getValue();
            if(!state.selected && !state.completed && state.track == null){
                iterator.remove();
            }
        }
    }

    private void emitEnterCommandsIfNeeded(
            RegionState state,
            Set<String> actualEntered,
            boolean hasEnterCommands,
            PlaybackSink sink){
        if(state.trigger != ChannelTrigger.ENTER_ONCE
                || state.commandsTriggered
                || !hasEnterCommands
                || !actualEntered.contains(state.area.getUuid())){
            return;
        }
        List<String> configuredExitCommands = state.area.getExitCommands();
        List<String> frozenExitCommands = configuredExitCommands == null
                ? Collections.emptyList()
                : new ArrayList<>(configuredExitCommands);
        long actionToken = claimActionToken();
        sink.enterCommands(
                state.area,
                !frozenExitCommands.isEmpty(),
                actionToken
        );
        state.commandsTriggered = true;
        entryActivations.put(
                state.area.getUuid(),
                new EntryActivation(
                        state.area,
                        frozenExitCommands,
                        actionToken
                )
        );
    }

    private void emitExitedCommands(Set<String> insideAreaUuids,
                                    PlaybackSink sink){
        List<EntryActivation> exited = new ArrayList<>();
        Iterator<Map.Entry<String, EntryActivation>> iterator =
                entryActivations.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<String, EntryActivation> entry = iterator.next();
            if(insideAreaUuids.contains(entry.getKey())){
                continue;
            }
            exited.add(entry.getValue());
            iterator.remove();
        }
        exited.sort((first, second) -> AREA_ORDER.compare(
                first.area,
                second.area
        ));
        for(EntryActivation activation : exited){
            sink.exitCommands(
                    activation.area,
                    activation.commandTemplates,
                    activation.actionToken
            );
        }
    }

    private long claimActionToken(){
        if(nextActionToken == Long.MAX_VALUE){
            throw new IllegalStateException("action token space exhausted");
        }
        return nextActionToken++;
    }

    private RegionState stateFor(AreaDto area, ChannelTrigger trigger){
        RegionState state = regions.get(area.getUuid());
        if(state == null){
            state = new RegionState(area, trigger);
            regions.put(area.getUuid(), state);
        } else {
            state.area = area;
            state.channelName = area.getChannel();
            state.trigger = trigger;
        }
        return state;
    }

    private void releaseTrack(RegionState state, PlaybackSink sink){
        if(state.track == null){
            return;
        }
        String soundKey = state.track.getSoundKey();
        SoundGroup group = sounds.get(soundKey);
        if(group != null){
            group.regionUuids.remove(state.area.getUuid());
            if(group.regionUuids.isEmpty()){
                sounds.remove(soundKey);
                sink.stop(soundKey);
            }
        }
        state.track = null;
    }

    private static long safeExpiry(long now, long durationMillis){
        if(durationMillis > Long.MAX_VALUE - now){
            return Long.MAX_VALUE;
        }
        return now + durationMillis;
    }

    private static int priorityValue(AreaDto area){
        return area == null || area.getPriority() == null
                ? Integer.MIN_VALUE
                : area.getPriority().getValue();
    }

    private static int orderValue(AreaDto area){
        return area == null || area.getOrder() == null ? 0 : area.getOrder();
    }

    private static String stableAreaId(AreaDto area){
        if(area == null){
            return "";
        }
        if(area.getAreaId() != null){
            return area.getAreaId();
        }
        return area.getUuid() == null ? "" : area.getUuid();
    }

    private static final class RegionState {
        private AreaDto area;
        private String channelName;
        private ChannelTrigger trigger;
        private boolean selected;
        private boolean completed;
        private boolean commandsTriggered;
        private SelectedTrack track;

        private RegionState(AreaDto area, ChannelTrigger trigger) {
            this.area = area;
            this.channelName = area.getChannel();
            this.trigger = trigger;
        }
    }

    private static final class SoundGroup {
        private final String soundKey;
        private final long expiresAt;
        private final Set<String> regionUuids = new LinkedHashSet<>();

        private SoundGroup(String soundKey, long expiresAt) {
            this.soundKey = soundKey;
            this.expiresAt = expiresAt;
        }
    }

    private static final class EntryActivation {
        private AreaDto area;
        private final List<String> commandTemplates;
        private final long actionToken;

        private EntryActivation(AreaDto area,
                                List<String> commandTemplates,
                                long actionToken) {
            this.area = area;
            this.commandTemplates = Collections.unmodifiableList(
                    new ArrayList<>(commandTemplates)
            );
            this.actionToken = actionToken;
        }
    }
}
