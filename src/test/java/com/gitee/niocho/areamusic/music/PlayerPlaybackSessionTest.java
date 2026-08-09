package com.gitee.niocho.areamusic.music;

import com.gitee.niocho.areamusic.config.AreaDto;
import com.gitee.niocho.areamusic.config.ChannelMode;
import com.gitee.niocho.areamusic.config.ChannelTrigger;
import com.gitee.niocho.areamusic.config.PlaybackChannelConfig;
import com.gitee.niocho.areamusic.config.PlaybackChannelRegistry;
import com.gitee.niocho.areamusic.config.Priority;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPlaybackSessionTest {
    @Test
    void exclusiveOnlyAllowsStrictlyHigherOverwrite(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto low = area("low", "bgm", Priority.NORMAL, 0, true, true);
        AreaDto equal = area("equal", "bgm", Priority.NORMAL, 20, true, true);
        AreaDto high = area("high", "bgm", Priority.HIGH, 0, false, true);

        RecordingPlaybackSink first = reconcile(session, 0, low);
        assertTypes(first.snapshot(), PlaybackOperation.Type.PLAY);

        RecordingPlaybackSink equalAttempt = reconcile(session, 100, low, equal);
        assertTrue(equalAttempt.snapshot().isEmpty());
        assertTrue(session.isRegionSelected("low"));

        RecordingPlaybackSink disabledOverwrite = reconcile(
                session,
                200,
                low,
                high
        );
        assertTrue(disabledOverwrite.snapshot().isEmpty());
        assertTrue(session.isRegionSelected("low"));

        high.setOverWrite(true);
        RecordingPlaybackSink takeover = reconcile(session, 300, low, high);
        assertTypes(
                takeover.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.PLAY
        );
        assertFalse(session.isRegionSelected("low"));
        assertTrue(session.isRegionSelected("high"));
    }

    @Test
    void additiveHonorsLayerLimitAndImmediatelyFillsVacancy(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        PlaybackChannelRegistry channels = channels(2, 2);
        AreaDto first = area("first", "ambience", Priority.HIGH, 0, false, true);
        AreaDto second = area("second", "ambience", Priority.NORMAL, 10, false, true);
        AreaDto third = area("third", "ambience", Priority.NORMAL, 0, false, true);

        RecordingPlaybackSink initial = reconcile(
                session,
                channels,
                0,
                first,
                second,
                third
        );
        assertTypes(
                initial.snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.PLAY
        );
        assertTrue(session.isRegionSelected("first"));
        assertTrue(session.isRegionSelected("second"));
        assertFalse(session.isRegionSelected("third"));

        RecordingPlaybackSink vacancy = reconcile(
                session,
                channels,
                100,
                second,
                third
        );
        assertTypes(
                vacancy.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.PLAY
        );
        assertTrue(session.isRegionSelected("third"));
    }

    @Test
    void suppressedEnterOnceRequiresLeaveAndReenter(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        PlaybackChannelRegistry channels = channels(2, 1);
        AreaDto top = area("top", "stinger", Priority.HIGH, 0, false, true);
        AreaDto suppressed = area(
                "suppressed",
                "stinger",
                Priority.NORMAL,
                0,
                false,
                true
        );

        RecordingPlaybackSink initial = reconcile(
                session,
                channels,
                0,
                top,
                suppressed
        );
        assertTypes(initial.snapshot(), PlaybackOperation.Type.PLAY);
        assertTrue(session.isRegionCompleted("suppressed"));

        RecordingPlaybackSink stillInside = reconcile(
                session,
                channels,
                100,
                suppressed
        );
        assertTypes(stillInside.snapshot(), PlaybackOperation.Type.STOP);
        assertFalse(session.isRegionSelected("suppressed"));

        reconcile(session, channels, 200);
        RecordingPlaybackSink reentered = reconcile(
                session,
                channels,
                300,
                suppressed
        );
        assertTypes(reentered.snapshot(), PlaybackOperation.Type.PLAY);
    }

    @Test
    void identicalSoundKeyUsesReferencesAndStopsOnlyAfterLastLeaves(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        PlaybackChannelRegistry channels = channels(3, 2);
        AreaDto first = area("first", "ambience", Priority.HIGH, 0, false, true);
        AreaDto second = area("second", "ambience", Priority.NORMAL, 0, false, true);
        TrackSelector sharedTrack = area -> track("shared.sound", 1000);

        RecordingPlaybackSink initial = reconcile(
                session,
                channels,
                sharedTrack,
                0,
                first,
                second
        );
        assertTypes(initial.snapshot(), PlaybackOperation.Type.PLAY);
        assertEquals(2, session.getSoundReferenceCount("shared.sound"));

        RecordingPlaybackSink oneLeft = reconcile(
                session,
                channels,
                sharedTrack,
                100,
                second
        );
        assertTrue(oneLeft.snapshot().isEmpty());
        assertEquals(1, session.getSoundReferenceCount("shared.sound"));

        RecordingPlaybackSink allLeft = reconcile(
                session,
                channels,
                sharedTrack,
                200
        );
        assertTypes(allLeft.snapshot(), PlaybackOperation.Type.STOP);
        assertEquals(0, session.getActiveSoundCount());
    }

    @Test
    void identicalSoundAcrossChannelsStillUsesOnePhysicalInstance(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto bgm = area("bgm-area", "bgm", Priority.HIGH, 0, false, true);
        AreaDto ambience = area(
                "ambience-area",
                "ambience",
                Priority.NORMAL,
                0,
                false,
                true
        );
        TrackSelector sharedTrack = area -> track("shared.cross-channel", 1000);

        RecordingPlaybackSink initial = reconcile(
                session,
                PlaybackChannelRegistry.defaults(),
                sharedTrack,
                0,
                bgm,
                ambience
        );

        assertTypes(initial.snapshot(), PlaybackOperation.Type.PLAY);
        assertEquals(
                2,
                session.getSoundReferenceCount("shared.cross-channel")
        );
    }

    @Test
    void reloadOfSameEnterOnceAreaDoesNotReplayIt(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto beforeReload = area(
                "stinger-area",
                "stinger",
                Priority.NORMAL,
                0,
                false,
                false
        );
        reconcile(session, 0, beforeReload);

        AreaDto afterReload = area(
                "stinger-area",
                "stinger",
                Priority.HIGH,
                10,
                false,
                false
        );
        RecordingPlaybackSink reloaded = reconcile(
                session,
                100,
                afterReload
        );

        assertTrue(reloaded.snapshot().isEmpty());
        assertTrue(session.isRegionSelected("stinger-area"));
    }

    @Test
    void clearingSessionStopsAndAllowsEnterOnceToReplay(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto stinger = area(
                "stinger-area",
                "stinger",
                Priority.NORMAL,
                0,
                false,
                false
        );
        reconcile(session, 0, stinger);

        RecordingPlaybackSink restarted = new RecordingPlaybackSink();
        session.clear(restarted);
        session.reconcile(
                Collections.singletonList(stinger),
                PlaybackChannelRegistry.defaults(),
                100,
                area -> track(area.getUuid() + ".sound", 1000),
                restarted
        );

        assertTypes(
                restarted.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.PLAY
        );
    }

    @Test
    void loopingContinuousTrackRestartsButNonLoopingTrackCompletes(){
        PlaybackChannelRegistry channels = channels(2, 2);
        PlayerPlaybackSession loopingSession = new PlayerPlaybackSession();
        AreaDto looping = area("looping", "ambience", Priority.NORMAL, 0, false, true);
        RecordingPlaybackSink loopingStart = reconcile(
                loopingSession,
                channels,
                0,
                looping
        );
        assertTypes(loopingStart.snapshot(), PlaybackOperation.Type.PLAY);
        RecordingPlaybackSink loopingRestart = reconcile(
                loopingSession,
                channels,
                1000,
                looping
        );
        assertTypes(loopingRestart.snapshot(), PlaybackOperation.Type.PLAY);

        PlayerPlaybackSession oneShotSession = new PlayerPlaybackSession();
        AreaDto oneShot = area("one-shot", "ambience", Priority.NORMAL, 0, false, false);
        reconcile(oneShotSession, channels, 0, oneShot);
        RecordingPlaybackSink expired = reconcile(
                oneShotSession,
                channels,
                1000,
                oneShot
        );
        assertTrue(expired.snapshot().isEmpty());
        assertTrue(oneShotSession.isRegionCompleted("one-shot"));
    }

    private RecordingPlaybackSink reconcile(PlayerPlaybackSession session,
                                             long now,
                                             AreaDto... areas){
        return reconcile(
                session,
                PlaybackChannelRegistry.defaults(),
                area -> track(area.getUuid() + ".sound", 1000),
                now,
                areas
        );
    }

    private RecordingPlaybackSink reconcile(PlayerPlaybackSession session,
                                             PlaybackChannelRegistry channels,
                                             long now,
                                             AreaDto... areas){
        return reconcile(
                session,
                channels,
                area -> track(area.getUuid() + ".sound", 1000),
                now,
                areas
        );
    }

    private RecordingPlaybackSink reconcile(PlayerPlaybackSession session,
                                             PlaybackChannelRegistry channels,
                                             TrackSelector selector,
                                             long now,
                                             AreaDto... areas){
        RecordingPlaybackSink sink = new RecordingPlaybackSink();
        session.reconcile(Arrays.asList(areas), channels, now, selector, sink);
        return sink;
    }

    private SelectedTrack track(String soundKey, long duration){
        return new SelectedTrack(
                soundKey,
                soundKey,
                soundKey,
                duration,
                1.0f,
                1.0f
        );
    }

    private AreaDto area(String id,
                         String channel,
                         Priority priority,
                         int order,
                         boolean overwrite,
                         boolean loop){
        return AreaDto.builder()
                .uuid(id)
                .areaId(id)
                .channel(channel)
                .priority(priority)
                .order(order)
                .overWrite(overwrite)
                .loop(loop)
                .enabled(true)
                .random(false)
                .volume(1.0f)
                .pitch(1.0f)
                .musicId(Collections.singletonList(id + "-track"))
                .build();
    }

    private PlaybackChannelRegistry channels(int ambienceLayers,
                                              int stingerLayers){
        Map<String, PlaybackChannelConfig> values = new LinkedHashMap<>();
        values.put("bgm", channel(
                ChannelMode.EXCLUSIVE,
                1,
                ChannelTrigger.CONTINUOUS
        ));
        values.put("ambience", channel(
                ChannelMode.ADDITIVE,
                ambienceLayers,
                ChannelTrigger.CONTINUOUS
        ));
        values.put("stinger", channel(
                ChannelMode.ADDITIVE,
                stingerLayers,
                ChannelTrigger.ENTER_ONCE
        ));
        return PlaybackChannelRegistry.of(values);
    }

    private PlaybackChannelConfig channel(ChannelMode mode,
                                          int maxLayers,
                                          ChannelTrigger trigger){
        return PlaybackChannelConfig.builder()
                .mode(mode)
                .maxLayers(maxLayers)
                .trigger(trigger)
                .build();
    }

    private void assertTypes(List<PlaybackOperation> operations,
                             PlaybackOperation.Type... types){
        assertEquals(types.length, operations.size());
        for(int index = 0; index < types.length; index++){
            assertEquals(types[index], operations.get(index).getType());
        }
    }
}
