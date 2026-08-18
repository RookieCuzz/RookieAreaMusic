package io.github.rookiecuzz.rookieregions.rule;

import java.util.List;

/** Built-in rules consumed by the Paper protection adapter and claim service. */
public final class ProtectionFlags {
    public static final Flag<State> BUILD = ownershipProtected("build");
    /** Unset is intentionally handled by ProtectionRuleSet as BUILD. */
    public static final Flag<State> BLOCK_BREAK = buildOverride("block-break");
    /** Unset is intentionally handled by ProtectionRuleSet as BUILD. */
    public static final Flag<State> BLOCK_PLACE = buildOverride("block-place");
    public static final Flag<State> USE = ownershipProtected("use");
    /** Unset is intentionally handled by ProtectionRuleSet as USE. */
    public static final Flag<State> CONTAINER = buildOverride("container");

    public static final Flag<State> PVP = allowByDefault("pvp");
    public static final Flag<State> ENTRY = allowByDefault("entry");
    public static final Flag<State> EXPLOSION = allowByDefault("explosion");

    public static final Flag<State> ALLOW_PLAYER_REGIONS = new StateFlag(
            "core.allow-player-regions",
            InheritanceMode.LOCAL_ONLY,
            FlagDefault.constant(State.DENY),
            FlagScope.ANY_REGION,
            ActorScope.ANY,
            "rookieregions.admin"
    );

    public static final FlagRegistry REGISTRY = new FlagRegistry(List.of(
            BUILD,
            BLOCK_BREAK,
            BLOCK_PLACE,
            USE,
            CONTAINER,
            PVP,
            ENTRY,
            EXPLOSION,
            ALLOW_PLAYER_REGIONS
    ));

    private static Flag<State> ownershipProtected(String name) {
        return new StateFlag(
                name,
                InheritanceMode.INHERIT,
                context -> java.util.Optional.of(
                        context.isWilderness() || context.association().isAssociated()
                                ? State.ALLOW
                                : State.DENY
                ),
                FlagScope.ANY_REGION,
                ActorScope.ANY,
                "rookieregions.region.flag"
        );
    }

    private static Flag<State> buildOverride(String name) {
        return new StateFlag(
                name,
                InheritanceMode.INHERIT,
                FlagDefault.unset(),
                FlagScope.ANY_REGION,
                ActorScope.ANY,
                "rookieregions.region.flag"
        );
    }

    private static Flag<State> allowByDefault(String name) {
        return new StateFlag(
                name,
                InheritanceMode.INHERIT,
                FlagDefault.constant(State.ALLOW),
                FlagScope.ANY_REGION,
                ActorScope.ANY,
                "rookieregions.region.flag"
        );
    }

    private ProtectionFlags() {
    }
}
