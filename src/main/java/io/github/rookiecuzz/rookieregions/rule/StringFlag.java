package io.github.rookiecuzz.rookieregions.rule;

/** Strongly typed string flag whose conflict policy is explicit. */
public final class StringFlag extends Flag<String> {
    public StringFlag(String name, ConflictStrategy<String> conflicts) {
        this(name, InheritanceMode.INHERIT, FlagDefault.unset(), conflicts,
                FlagScope.ANY_REGION, ActorScope.ANY,
                "rookieregions.region.flag");
    }

    public StringFlag(String name,
                      InheritanceMode inheritance,
                      FlagDefault<String> defaultProvider,
                      ConflictStrategy<String> conflicts,
                      FlagScope regionScope,
                      ActorScope actorScope,
                      String modificationPermission) {
        super(Flag.<String>builder(name, String.class, FlagCodecs.STRING)
                .inheritance(inheritance)
                .defaultProvider(defaultProvider)
                .conflictStrategy(conflicts)
                .scope(regionScope)
                .actorScope(actorScope)
                .modificationPermission(modificationPermission));
    }
}
