package io.github.rookiecuzz.rookieareamusic.music;

import io.github.rookiecuzz.rookieareamusic.config.AreaDto;
import io.github.rookiecuzz.rookieareamusic.config.ChannelMode;
import io.github.rookiecuzz.rookieareamusic.config.ChannelTrigger;
import io.github.rookiecuzz.rookieareamusic.config.PlaybackChannelConfig;
import io.github.rookiecuzz.rookieareamusic.config.PlaybackChannelRegistry;
import io.github.rookiecuzz.rookieareamusic.config.Priority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        AreaDto top = withCommands(
                area("top", "stinger", Priority.HIGH, 0, false, true),
                "top {player}"
        );
        AreaDto suppressed = withCommands(area(
                "suppressed",
                "stinger",
                Priority.NORMAL,
                0,
                false,
                true
        ), "suppressed {player}");

        RecordingPlaybackSink initial = reconcile(
                session,
                channels,
                0,
                top,
                suppressed
        );
        assertTypes(
                initial.snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        assertTrue(session.isRegionCompleted("suppressed"));

        RecordingPlaybackSink stillInside = reconcile(
                session,
                channels,
                100,
                suppressed
        );
        assertTypes(
                stillInside.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        assertTrue(
                stillInside.snapshot().get(1).getCommandTemplates().isEmpty()
        );
        assertFalse(session.isRegionSelected("suppressed"));

        reconcile(session, channels, 200);
        RecordingPlaybackSink reentered = reconcile(
                session,
                channels,
                300,
                suppressed
        );
        assertTypes(
                reentered.snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
    }

    @Test
    void enterCommandsRunOncePerPhysicalEntryAndUseImmutableSnapshot(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto stinger = withCommands(
                area(
                        "landmark",
                        "stinger",
                        Priority.NORMAL,
                        0,
                        false,
                        false
                ),
                "effect give {player} glowing 5"
        );

        RecordingPlaybackSink entered = reconcile(session, 0, stinger);
        assertTypes(
                entered.snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        PlaybackOperation commandOperation = entered.snapshot().get(1);
        assertSame(stinger, commandOperation.getArea());
        assertFalse(commandOperation.hasFrozenExitCommands());
        assertEquals(
                Collections.singletonList(
                        "effect give {player} glowing 5"
                ),
                commandOperation.getCommandTemplates()
        );
        stinger.getEnterCommands().set(0, "changed after selection");
        assertEquals(
                "effect give {player} glowing 5",
                commandOperation.getCommandTemplates().get(0)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> commandOperation.getCommandTemplates().add("late")
        );

        assertTrue(reconcile(session, 100, stinger).snapshot().isEmpty());
        assertTypes(
                reconcile(session, 200).snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        assertTypes(
                reconcile(session, 300, stinger).snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
    }

    @Test
    void exitCommandsRunOnceAndUseAFrozenTemplate(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto stinger = withExitCommands(
                withCommands(
                        area(
                                "landmark",
                                "stinger",
                                Priority.NORMAL,
                                0,
                                false,
                                false
                        ),
                        "say entered {player}"
                ),
                "say left {player}"
        );

        RecordingPlaybackSink entered = reconcile(session, 0, stinger);
        assertTypes(
                entered.snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        PlaybackOperation firstEntry = entered.snapshot().get(1);
        assertTrue(firstEntry.hasFrozenExitCommands());
        long firstActionToken = firstEntry.getActionToken();
        assertTrue(firstActionToken > 0L);
        stinger.getExitCommands().set(0, "changed after entry");
        assertTrue(reconcile(session, 100, stinger).snapshot().isEmpty());

        RecordingPlaybackSink left = reconcile(session, 200);
        assertTypes(
                left.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        PlaybackOperation exitOperation = left.snapshot().get(1);
        assertSame(stinger, exitOperation.getArea());
        assertEquals(firstActionToken, exitOperation.getActionToken());
        assertEquals(
                Collections.singletonList("say left {player}"),
                exitOperation.getCommandTemplates()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> exitOperation.getCommandTemplates().add("late")
        );
        assertTrue(reconcile(session, 300).snapshot().isEmpty());

        RecordingPlaybackSink reentered = reconcile(session, 400, stinger);
        assertTypes(
                reentered.snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        long secondActionToken = reentered.snapshot().get(1).getActionToken();
        assertTrue(secondActionToken > firstActionToken);
        RecordingPlaybackSink leftAgain = reconcile(session, 500);
        assertTypes(
                leftAgain.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        assertEquals(
                "changed after entry",
                leftAgain.snapshot().get(1).getCommandTemplates().get(0)
        );
        assertEquals(
                secondActionToken,
                leftAgain.snapshot().get(1).getActionToken()
        );
    }

    @Test
    void additiveExitCommandsOnlyPairWithSelectedLayers(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        PlaybackChannelRegistry channels = channels(2, 2);
        AreaDto first = withEntryAndExitCommands(
                area("first", "stinger", Priority.HIGH, 0, false, false)
        );
        AreaDto second = withEntryAndExitCommands(
                area("second", "stinger", Priority.NORMAL, 10, false, false)
        );
        AreaDto suppressed = withEntryAndExitCommands(
                area("suppressed", "stinger", Priority.NORMAL, 0, false, false)
        );

        RecordingPlaybackSink entered = reconcile(
                session,
                channels,
                0,
                first,
                second,
                suppressed
        );
        assertEquals(
                2,
                countType(
                        entered.snapshot(),
                        PlaybackOperation.Type.ENTER_COMMANDS
                )
        );
        assertTrue(session.isRegionCompleted("suppressed"));

        RecordingPlaybackSink left = reconcile(session, channels, 100);
        assertEquals(
                2,
                countType(left.snapshot(), PlaybackOperation.Type.STOP)
        );
        assertEquals(
                2,
                countType(
                        left.snapshot(),
                        PlaybackOperation.Type.EXIT_COMMANDS
                )
        );
        List<AreaDto> exitedAreas = new ArrayList<>();
        for(PlaybackOperation operation : left.snapshot()){
            if(operation.getType() == PlaybackOperation.Type.EXIT_COMMANDS){
                exitedAreas.add(operation.getArea());
            }
        }
        assertEquals(Arrays.asList(first, second), exitedAreas);
        assertFalse(exitedAreas.contains(suppressed));
    }

    @Test
    void sharedSoundStillEmitsCommandsForEachSelectedRegion(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        PlaybackChannelRegistry channels = channels(2, 2);
        AreaDto first = withCommands(
                area("first", "stinger", Priority.HIGH, 0, false, false),
                "first {player}"
        );
        AreaDto second = withCommands(
                area("second", "stinger", Priority.NORMAL, 0, false, false),
                "second {player}"
        );

        RecordingPlaybackSink entered = reconcile(
                session,
                channels,
                ignored -> track("shared.stinger", 1000),
                0,
                first,
                second
        );

        assertTypes(
                entered.snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        assertSame(first, entered.snapshot().get(1).getArea());
        assertSame(second, entered.snapshot().get(2).getArea());
        assertEquals(2, session.getSoundReferenceCount("shared.stinger"));
    }

    @Test
    void sharedSoundExitCommandsRemainIndependentPerRegion(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        PlaybackChannelRegistry channels = channels(2, 2);
        AreaDto first = withEntryAndExitCommands(
                area("first", "stinger", Priority.HIGH, 0, false, false)
        );
        AreaDto second = withEntryAndExitCommands(
                area("second", "stinger", Priority.NORMAL, 0, false, false)
        );
        TrackSelector shared = ignored -> track("shared.stinger", 1000);

        RecordingPlaybackSink entered = reconcile(
                session,
                channels,
                shared,
                0,
                first,
                second
        );
        assertTypes(
                entered.snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        long firstToken = entered.snapshot().get(1).getActionToken();
        long secondToken = entered.snapshot().get(2).getActionToken();
        assertTrue(secondToken > firstToken);

        RecordingPlaybackSink firstLeft = reconcile(
                session,
                channels,
                shared,
                100,
                second
        );
        assertTypes(
                firstLeft.snapshot(),
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        assertSame(first, firstLeft.snapshot().get(0).getArea());
        assertEquals(
                firstToken,
                firstLeft.snapshot().get(0).getActionToken()
        );
        assertEquals(1, session.getSoundReferenceCount("shared.stinger"));

        RecordingPlaybackSink secondLeft = reconcile(
                session,
                channels,
                shared,
                200
        );
        assertTypes(
                secondLeft.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        assertSame(second, secondLeft.snapshot().get(1).getArea());
        assertEquals(
                secondToken,
                secondLeft.snapshot().get(1).getActionToken()
        );
    }

    @Test
    void commandOnlyEnterOnceCompletesWithoutSelectingATrack(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto commandOnly = withCommands(
                area(
                        "command-only",
                        "stinger",
                        Priority.NORMAL,
                        0,
                        false,
                        false
                ),
                "say entered {player}"
        );
        commandOnly.setMusicId(Collections.emptyList());
        RecordingPlaybackSink sink = new RecordingPlaybackSink();

        session.reconcile(
                Collections.singletonList(commandOnly),
                PlaybackChannelRegistry.defaults(),
                0,
                ignored -> {
                    throw new AssertionError(
                            "command-only regions must not select a track"
                    );
                },
                sink
        );

        assertTypes(
                sink.snapshot(),
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        assertTrue(session.isRegionCompleted("command-only"));
        assertEquals(0, session.getActiveSoundCount());
    }

    @Test
    void commandOnlyRegionEmitsItsPairedExit(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto commandOnly = withExitCommands(
                withCommands(
                        area(
                                "command-only-exit",
                                "stinger",
                                Priority.NORMAL,
                                0,
                                false,
                                false
                        ),
                        "say entered {player}"
                ),
                "say left {player}"
        );
        commandOnly.setMusicId(Collections.emptyList());

        assertTypes(
                reconcile(session, 0, commandOnly).snapshot(),
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        RecordingPlaybackSink left = reconcile(session, 100);
        assertTypes(
                left.snapshot(),
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        assertSame(commandOnly, left.snapshot().get(0).getArea());
    }

    @Test
    void invalidConfiguredTrackCompletesWithoutRunningCommands(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto broken = withCommands(
                area("broken", "stinger", Priority.NORMAL, 0, false, false),
                "must-not-run {player}"
        );
        RecordingPlaybackSink sink = reconcile(
                session,
                PlaybackChannelRegistry.defaults(),
                ignored -> null,
                0,
                broken
        );

        assertTrue(sink.snapshot().isEmpty());
        assertTrue(session.isRegionCompleted("broken"));
        assertTrue(reconcile(session, 100, broken).snapshot().isEmpty());
    }

    @Test
    void emptyEnterOnceCompletesWithoutOutput(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto empty = area(
                "empty",
                "stinger",
                Priority.NORMAL,
                0,
                false,
                false
        );
        empty.setMusicId(Collections.emptyList());

        RecordingPlaybackSink entered = reconcile(session, 0, empty);

        assertTrue(entered.snapshot().isEmpty());
        assertTrue(session.isRegionCompleted("empty"));
    }

    @Test
    void forcedReselectionAfterChannelChangeDoesNotRunEnterCommands(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        PlaybackChannelRegistry channels = channelsWithTwoStingers();
        AreaDto beforeReload = withExitCommands(
                withCommands(
                        area(
                                "boss",
                                "stinger",
                                Priority.NORMAL,
                                0,
                                false,
                                false
                        ),
                        "boss-before {player}"
                ),
                "boss-left-before {player}"
        );
        assertTypes(
                reconcile(session, channels, 0, beforeReload).snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );

        AreaDto afterReload = withExitCommands(
                withCommands(
                        area(
                                "boss",
                                "stinger_alt",
                                Priority.HIGH,
                                0,
                                false,
                                false
                        ),
                        "boss-after {player}"
                ),
                "boss-left-after {player}"
        );
        RecordingPlaybackSink forced = reconcile(
                session,
                channels,
                100,
                afterReload
        );

        assertTypes(
                forced.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.PLAY
        );

        RecordingPlaybackSink left = reconcile(session, channels, 200);
        assertTypes(
                left.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        PlaybackOperation exit = left.snapshot().get(1);
        assertSame(afterReload, exit.getArea());
        assertEquals(
                Collections.singletonList("boss-left-before {player}"),
                exit.getCommandTemplates()
        );
    }

    @Test
    void playbackRestartReplaysActiveSoundWithoutRepeatingCommands(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto stinger = withExitCommands(
                withCommands(
                        area(
                                "boss",
                                "stinger",
                                Priority.NORMAL,
                                0,
                                false,
                                false
                        ),
                        "boss {player}"
                ),
                "boss-left {player}"
        );
        assertTypes(
                reconcile(session, 0, stinger).snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );

        RecordingPlaybackSink restarted = new RecordingPlaybackSink();
        session.restartPlayback(restarted, 500L);
        session.reconcile(
                Collections.singletonList(stinger),
                PlaybackChannelRegistry.defaults(),
                500,
                area -> track(area.getUuid() + ".sound", 1000),
                restarted
        );

        assertTypes(
                restarted.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.PLAY
        );
        assertTrue(session.isRegionSelected("boss"));

        RecordingPlaybackSink left = reconcile(session, 600);
        assertTypes(
                left.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.EXIT_COMMANDS
        );
    }

    @Test
    void playbackRestartDoesNotReviveExpiredEnterOnceSound(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto stinger = withCommands(
                area("boss", "stinger", Priority.NORMAL, 0, false, false),
                "boss {player}"
        );
        assertTypes(
                reconcile(session, 0, stinger).snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );

        RecordingPlaybackSink restarted = new RecordingPlaybackSink();
        session.restartPlayback(restarted, 1000L);
        session.reconcile(
                Collections.singletonList(stinger),
                PlaybackChannelRegistry.defaults(),
                1000L,
                area -> track(area.getUuid() + ".sound", 1000),
                restarted
        );

        assertTrue(restarted.snapshot().isEmpty());
        assertTrue(session.isRegionCompleted("boss"));
    }

    @Test
    void playbackRestartExpiresStateFromAChannelRemovedByReload(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto oldChannelStinger = withCommands(
                area(
                        "old-boss",
                        "stinger_alt",
                        Priority.NORMAL,
                        0,
                        false,
                        false
                ),
                "boss {player}"
        );
        assertTypes(
                reconcile(
                        session,
                        channelsWithTwoStingers(),
                        0,
                        oldChannelStinger
                ).snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );

        RecordingPlaybackSink restarted = new RecordingPlaybackSink();
        session.restartPlayback(restarted, 1000L);

        assertTrue(restarted.snapshot().isEmpty());
        assertTrue(session.isRegionCompleted("old-boss"));
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
    void reloadRefreshesExitAreaIdentityButKeepsEntryTimeTemplate(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto beforeReload = withExitCommands(
                withCommands(
                        area(
                                "reload-exit",
                                "stinger",
                                Priority.NORMAL,
                                0,
                                false,
                                false
                        ),
                        "entered-before {player}"
                ),
                "left-before {player}"
        );
        assertTypes(
                reconcile(session, 0, beforeReload).snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );

        AreaDto afterReload = withExitCommands(
                withCommands(
                        area(
                                "reload-exit",
                                "stinger",
                                Priority.HIGH,
                                10,
                                false,
                                false
                        ),
                        "entered-after {player}"
                ),
                "left-after {player}"
        );
        assertTrue(reconcile(session, 100, afterReload).snapshot().isEmpty());

        RecordingPlaybackSink left = reconcile(session, 200);
        assertTypes(
                left.snapshot(),
                PlaybackOperation.Type.STOP,
                PlaybackOperation.Type.EXIT_COMMANDS
        );
        PlaybackOperation exit = left.snapshot().get(1);
        assertSame(afterReload, exit.getArea());
        assertEquals(
                Collections.singletonList("left-before {player}"),
                exit.getCommandTemplates()
        );
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
    void clearingSessionNeverEmitsOrRetainsExitCommands(){
        PlayerPlaybackSession session = new PlayerPlaybackSession();
        AreaDto stinger = withEntryAndExitCommands(area(
                "clear-exit",
                "stinger",
                Priority.NORMAL,
                0,
                false,
                false
        ));
        assertTypes(
                reconcile(session, 0, stinger).snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );

        RecordingPlaybackSink cleared = new RecordingPlaybackSink();
        session.clear(cleared);
        assertTypes(cleared.snapshot(), PlaybackOperation.Type.STOP);
        assertTrue(reconcile(session, 100).snapshot().isEmpty());

        assertTypes(
                reconcile(session, 200, stinger).snapshot(),
                PlaybackOperation.Type.PLAY,
                PlaybackOperation.Type.ENTER_COMMANDS
        );
        session.clear(null);
        assertTrue(reconcile(session, 300).snapshot().isEmpty());
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

    private AreaDto withCommands(AreaDto area, String... commands){
        area.setEnterCommands(new ArrayList<>(Arrays.asList(commands)));
        return area;
    }

    private AreaDto withExitCommands(AreaDto area, String... commands){
        area.setExitCommands(new ArrayList<>(Arrays.asList(commands)));
        return area;
    }

    private AreaDto withEntryAndExitCommands(AreaDto area){
        withCommands(area, "entered " + area.getUuid());
        return withExitCommands(area, "left " + area.getUuid());
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

    private PlaybackChannelRegistry channelsWithTwoStingers(){
        Map<String, PlaybackChannelConfig> values = new LinkedHashMap<>();
        values.put("bgm", channel(
                ChannelMode.EXCLUSIVE,
                1,
                ChannelTrigger.CONTINUOUS
        ));
        values.put("ambience", channel(
                ChannelMode.ADDITIVE,
                2,
                ChannelTrigger.CONTINUOUS
        ));
        values.put("stinger", channel(
                ChannelMode.ADDITIVE,
                2,
                ChannelTrigger.ENTER_ONCE
        ));
        values.put("stinger_alt", channel(
                ChannelMode.ADDITIVE,
                2,
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

    private int countType(List<PlaybackOperation> operations,
                          PlaybackOperation.Type type){
        int result = 0;
        for(PlaybackOperation operation : operations){
            if(operation.getType() == type){
                result++;
            }
        }
        return result;
    }
}
