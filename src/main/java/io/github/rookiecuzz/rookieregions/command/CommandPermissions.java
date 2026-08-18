package io.github.rookiecuzz.rookieregions.command;

import io.github.rookiecuzz.rookieregions.mutation.MutationPermissions;

public final class CommandPermissions {
    public static final String VIEW = "rookieregions.region.view";
    public static final String DELETE = MutationPermissions.DELETE;
    public static final String FLAG = "rookieregions.region.flag";
    public static final String MUSIC = MutationPermissions.MUSIC;
    public static final String COMMANDS = MutationPermissions.COMMANDS;
    public static final String RELOAD = "rookieregions.reload";

    private CommandPermissions() {
    }
}
