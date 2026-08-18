package io.github.rookiecuzz.rookieregions.module.commands;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable physical region membership retained between scans. */
public final class CommandTransitionState {
    private static final CommandTransitionState EMPTY =
            new CommandTransitionState(Collections.<String, Integer>emptyMap());

    private final Map<String, Integer> activeRegions;

    private CommandTransitionState(Map<String, Integer> activeRegions) {
        this.activeRegions = Collections.unmodifiableMap(
                new LinkedHashMap<>(activeRegions)
        );
    }

    public static CommandTransitionState empty(){
        return EMPTY;
    }

    static CommandTransitionState of(Map<String, Integer> activeRegions){
        return activeRegions.isEmpty()
                ? EMPTY
                : new CommandTransitionState(activeRegions);
    }

    public Map<String, Integer> getActiveRegions() {
        return activeRegions;
    }
}
