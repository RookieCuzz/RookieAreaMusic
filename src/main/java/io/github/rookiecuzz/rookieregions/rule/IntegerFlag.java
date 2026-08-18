package io.github.rookiecuzz.rookieregions.rule;

/** Strongly typed 32-bit integer flag whose conflict policy is explicit. */
public final class IntegerFlag extends Flag<Integer> {
    public IntegerFlag(String name, ConflictStrategy<Integer> conflicts) {
        this(name, InheritanceMode.INHERIT, FlagDefault.unset(), conflicts,
                FlagScope.ANY_REGION, ActorScope.ANY,
                "rookieregions.region.flag");
    }

    public IntegerFlag(String name,
                       InheritanceMode inheritance,
                       FlagDefault<Integer> defaultProvider,
                       ConflictStrategy<Integer> conflicts,
                       FlagScope regionScope,
                       ActorScope actorScope,
                       String modificationPermission) {
        super(Flag.<Integer>builder(name, Integer.class, FlagCodecs.INTEGER)
                .inheritance(inheritance)
                .defaultProvider(defaultProvider)
                .conflictStrategy(conflicts)
                .scope(regionScope)
                .actorScope(actorScope)
                .modificationPermission(modificationPermission));
    }
}
