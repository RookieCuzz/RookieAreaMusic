package com.gitee.niocho.areamusic.utils;

import com.gitee.niocho.areamusic.RookieAreaMusic;
import com.gitee.niocho.areamusic.config.AreaDto;
import com.gitee.niocho.areamusic.config.MusicDto;
import com.gitee.niocho.areamusic.music.PlayerPlaybackSession;
import com.gitee.niocho.areamusic.music.RecordingPlaybackSink;
import com.gitee.niocho.areamusic.music.SelectedTrack;
import com.gitee.niocho.areamusic.player.PlayerLocationSnapshot;
import com.gitee.niocho.areamusic.runtime.RuntimeState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Pure region and playback-decision work. This class never calls Bukkit APIs. */
public class MusicUtil {
    private final RookieAreaMusic plugin;

    public MusicUtil(RookieAreaMusic plugin) {
        this.plugin = plugin;
    }

    public void processPlayer(UUID playerUuid,
                              PlayerLocationSnapshot location,
                              RuntimeState runtimeState,
                              boolean restartPlayback){
        if(playerUuid == null || location == null || runtimeState == null){
            return;
        }
        PlayerPlaybackSession session = plugin.getOrCreatePlaybackSession(
                playerUuid
        );
        RecordingPlaybackSink sink = new RecordingPlaybackSink();
        if(restartPlayback){
            plugin.getPlayerRegionCache().clear(playerUuid);
            session.clear(sink);
        }
        List<AreaDto> insideAreas = getCachedInsideList(
                playerUuid,
                location,
                runtimeState
        );
        session.reconcile(
                insideAreas,
                runtimeState.getChannels(),
                System.currentTimeMillis(),
                area -> selectTrack(playerUuid, area, runtimeState),
                sink
        );
        plugin.enqueuePlaybackOperations(playerUuid, sink.snapshot());
    }

    protected SelectedTrack selectTrack(UUID playerUuid,
                                        AreaDto area,
                                        RuntimeState runtimeState){
        List<String> musicIds = area.getMusicId();
        if(musicIds == null || musicIds.isEmpty()){
            return null;
        }

        int playTarget;
        if(Boolean.TRUE.equals(area.getRandom())){
            playTarget = ThreadLocalRandom.current().nextInt(musicIds.size());
        } else {
            playTarget = plugin.nextSequentialIndex(
                    playerUuid,
                    area.getUuid(),
                    musicIds.size()
            );
        }

        MusicDto music = runtimeState.getMusics().get(musicIds.get(playTarget));
        if(music == null
                || music.getMusicDuration() == null
                || music.getMusicDuration() <= 0
                || music.getMusicURL() == null
                || music.getMusicURL().trim().isEmpty()){
            return null;
        }
        long durationMillis = safeSecondsToMillis(music.getMusicDuration());
        return new SelectedTrack(
                music.getMusicId(),
                music.getUuid(),
                music.getMusicURL(),
                durationMillis,
                area.getVolume(),
                area.getPitch()
        );
    }

    protected List<AreaDto> getInsideList(RuntimeState state,
                                          String worldName,
                                          double x,
                                          double y,
                                          double z,
                                          boolean loopOnly){
        List<AreaDto> candidates = state.getSpatialIndex()
                .getCandidates(worldName, x, z);
        return filterInsideAreas(candidates, x, y, z, loopOnly);
    }

    protected List<AreaDto> getCachedInsideList(
            UUID playerUuid,
            PlayerLocationSnapshot location,
            RuntimeState runtimeState){
        List<AreaDto> cached = plugin.getPlayerRegionCache().get(
                playerUuid,
                location.getWorldName(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
        if(cached != null){
            return cached;
        }

        List<AreaDto> computed = getInsideList(
                runtimeState,
                location.getWorldName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                false
        );
        cachePlayerRegions(playerUuid, location, computed);
        return computed;
    }

    protected void cachePlayerRegions(UUID playerUuid,
                                      PlayerLocationSnapshot location,
                                      List<AreaDto> insideAreas){
        plugin.getPlayerRegionCache().put(
                playerUuid,
                location.getWorldName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                insideAreas
        );
    }

    protected List<AreaDto> filterInsideAreas(Iterable<AreaDto> areas,
                                              double x,
                                              double y,
                                              double z,
                                              boolean loopOnly){
        List<AreaDto> result = new ArrayList<>();
        if(areas == null){
            return result;
        }

        for(AreaDto area : areas){
            if(area == null || !Boolean.TRUE.equals(area.getEnabled())){
                continue;
            }
            if(loopOnly && !Boolean.TRUE.equals(area.getLoop())){
                continue;
            }
            if(isInside(area, x, y, z)){
                result.add(area);
            }
        }
        return result;
    }

    protected boolean isInside(AreaDto areaDto,
                               double x,
                               double y,
                               double z){
        return areaDto.getShape() != null
                && areaDto.getShape().contains(x, y, z);
    }

    private long safeSecondsToMillis(long seconds){
        if(seconds > Long.MAX_VALUE / 1000L){
            return Long.MAX_VALUE;
        }
        return seconds * 1000L;
    }
}
