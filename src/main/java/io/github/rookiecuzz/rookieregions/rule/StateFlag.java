package io.github.rookiecuzz.rookieregions.rule;

/** Strongly typed two-state flag with deterministic DENY-wins conflicts. */
public final class StateFlag extends Flag<State> {
    public StateFlag(String name) {
        this(name, InheritanceMode.INHERIT, FlagDefault.unset(),
                FlagScope.ANY_REGION, ActorScope.ANY,
                "rookieregions.region.flag");
    }

    public StateFlag(String name,
                     InheritanceMode inheritance,
                     FlagDefault<State> defaultProvider,
                     FlagScope regionScope,
                     ActorScope actorScope,
                     String modificationPermission) {
        super(Flag.<State>builder(name, State.class, FlagCodecs.STATE)
                .inheritance(inheritance)
                .defaultProvider(defaultProvider)
                .conflictStrategy(ConflictStrategies.denyWins())
                .scope(regionScope)
                .actorScope(actorScope)
                .modificationPermission(modificationPermission));
    }
}
