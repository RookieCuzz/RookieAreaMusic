package io.github.rookiecuzz.rookieregions.module.commands;

import java.util.Objects;

/** One command ready for dispatch, independent of Bukkit or music selection. */
public final class RegionCommandAction {
    private final String regionKey;
    private final CommandPhase phase;
    private final String command;

    RegionCommandAction(String regionKey,
                        CommandPhase phase,
                        String command) {
        this.regionKey = regionKey;
        this.phase = phase;
        this.command = command;
    }

    public String getRegionKey() {
        return regionKey;
    }

    public CommandPhase getPhase() {
        return phase;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public boolean equals(Object value) {
        if(this == value){
            return true;
        }
        if(!(value instanceof RegionCommandAction)){
            return false;
        }
        RegionCommandAction other = (RegionCommandAction) value;
        return regionKey.equals(other.regionKey)
                && phase == other.phase
                && command.equals(other.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regionKey, phase, command);
    }
}
