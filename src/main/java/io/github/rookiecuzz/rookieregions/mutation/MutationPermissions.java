package io.github.rookiecuzz.rookieregions.mutation;

/** Permission nodes consumed by the pure mutation service. */
public final class MutationPermissions {
    public static final String ADMIN = "rookieregions.admin";
    public static final String CREATE = "rookieregions.region.create";
    public static final String EDIT_OWN = "rookieregions.region.edit.own";
    public static final String EDIT_ANY = "rookieregions.region.edit.any";
    public static final String OVERLAP = "rookieregions.region.overlap";
    public static final String DELETE = "rookieregions.region.delete";
    public static final String MUSIC = "rookieregions.module.music";
    public static final String COMMANDS = "rookieregions.module.commands";

    private MutationPermissions() {
    }
}
