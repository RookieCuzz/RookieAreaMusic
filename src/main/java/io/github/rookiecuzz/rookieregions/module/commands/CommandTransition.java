package io.github.rookiecuzz.rookieregions.module.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Commands emitted for a membership change plus the state for the next scan. */
public final class CommandTransition {
    private final List<RegionCommandAction> actions;
    private final CommandTransitionState nextState;

    CommandTransition(List<RegionCommandAction> actions,
                      CommandTransitionState nextState) {
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
        this.nextState = nextState;
    }

    public List<RegionCommandAction> getActions() {
        return actions;
    }

    public CommandTransitionState getNextState() {
        return nextState;
    }
}
