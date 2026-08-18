package io.github.rookiecuzz.rookieregions.rule;

import java.util.LinkedHashSet;
import java.util.Set;

/** Typed set flag; each definition decides whether peer values are merged. */
public final class SetFlag<E> extends Flag<Set<E>> {
    public SetFlag(String name,
                   FlagCodec<E> elementCodec,
                   boolean mergeConflicts) {
        this(name, elementCodec, mergeConflicts, InheritanceMode.INHERIT,
                FlagDefault.unset(), FlagScope.ANY_REGION, ActorScope.ANY,
                "rookieregions.region.flag");
    }

    @SuppressWarnings("unchecked")
    public SetFlag(String name,
                   FlagCodec<E> elementCodec,
                   boolean mergeConflicts,
                   InheritanceMode inheritance,
                   FlagDefault<Set<E>> defaultProvider,
                   FlagScope regionScope,
                   ActorScope actorScope,
                   String modificationPermission) {
        super(Flag.<Set<E>>builder(
                        name,
                        (Class<Set<E>>) (Class<?>) Set.class,
                        codec(elementCodec)
                )
                .inheritance(inheritance)
                .defaultProvider(defaultProvider)
                .conflictStrategy(mergeConflicts
                        ? ConflictStrategies.union()
                        : ConflictStrategies.reject())
                .scope(regionScope)
                .actorScope(actorScope)
                .modificationPermission(modificationPermission)
                .normalizer(Set::copyOf));
    }

    private static <E> FlagCodec<Set<E>> codec(FlagCodec<E> elementCodec) {
        if(elementCodec == null) {
            throw new IllegalArgumentException("set element codec cannot be null");
        }
        return new FlagCodec<>() {
            @Override
            public Object encode(Set<E> value) {
                return value.stream().map(elementCodec::encode).toList();
            }

            @Override
            public Set<E> decode(Object encoded) {
                if(!(encoded instanceof Iterable<?> values)) {
                    throw new IllegalArgumentException("set flag must be a list");
                }
                LinkedHashSet<E> result = new LinkedHashSet<>();
                for(Object value : values) {
                    result.add(elementCodec.decode(value));
                }
                return Set.copyOf(result);
            }
        };
    }
}
