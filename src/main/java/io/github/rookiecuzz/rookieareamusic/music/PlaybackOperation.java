package io.github.rookiecuzz.rookieareamusic.music;

import io.github.rookiecuzz.rookieareamusic.config.AreaDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaybackOperation {
    public enum Type {
        PLAY,
        STOP,
        ENTER_COMMANDS,
        EXIT_COMMANDS
    }

    private final Type type;
    private final SelectedTrack track;
    private final String soundKey;
    private final AreaDto area;
    private final List<String> commandTemplates;
    private final long actionToken;
    private final boolean hasFrozenExitCommands;

    private PlaybackOperation(Type type,
                              SelectedTrack track,
                              String soundKey,
                              AreaDto area,
                              List<String> commandTemplates,
                              long actionToken,
                              boolean hasFrozenExitCommands) {
        this.type = type;
        this.track = track;
        this.soundKey = soundKey;
        this.area = area;
        this.commandTemplates = commandTemplates;
        this.actionToken = actionToken;
        this.hasFrozenExitCommands = hasFrozenExitCommands;
    }

    public static PlaybackOperation play(SelectedTrack track){
        return new PlaybackOperation(
                Type.PLAY,
                track,
                track.getSoundKey(),
                null,
                Collections.emptyList(),
                0L,
                false
        );
    }

    public static PlaybackOperation stop(String soundKey){
        return new PlaybackOperation(
                Type.STOP,
                null,
                soundKey,
                null,
                Collections.emptyList(),
                0L,
                false
        );
    }

    public static PlaybackOperation enterCommands(AreaDto area,
                                                   boolean hasFrozenExitCommands,
                                                   long actionToken){
        if(area == null){
            throw new IllegalArgumentException("area cannot be null");
        }
        requireActionToken(actionToken);
        List<String> configuredTemplates = area.getEnterCommands();
        List<String> templates = configuredTemplates == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<>(configuredTemplates)
                );
        return new PlaybackOperation(
                Type.ENTER_COMMANDS,
                null,
                null,
                area,
                templates,
                actionToken,
                hasFrozenExitCommands
        );
    }

    public static PlaybackOperation exitCommands(
            AreaDto area,
            List<String> commandTemplates,
            long actionToken){
        if(area == null){
            throw new IllegalArgumentException("area cannot be null");
        }
        requireActionToken(actionToken);
        List<String> templates = commandTemplates == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<>(commandTemplates)
                );
        return new PlaybackOperation(
                Type.EXIT_COMMANDS,
                null,
                null,
                area,
                templates,
                actionToken,
                false
        );
    }

    private static void requireActionToken(long actionToken){
        if(actionToken <= 0L){
            throw new IllegalArgumentException(
                    "actionToken must be positive"
            );
        }
    }

    public Type getType() {
        return type;
    }

    public SelectedTrack getTrack() {
        return track;
    }

    public String getSoundKey() {
        return soundKey;
    }

    public AreaDto getArea() {
        return area;
    }

    public List<String> getCommandTemplates() {
        return commandTemplates;
    }

    public long getActionToken() {
        return actionToken;
    }

    public boolean hasFrozenExitCommands() {
        return hasFrozenExitCommands;
    }
}
