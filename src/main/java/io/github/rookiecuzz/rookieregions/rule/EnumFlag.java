package io.github.rookiecuzz.rookieregions.rule;

import java.util.Locale;

/** Strongly typed enum flag encoded as a lower-case enum constant name. */
public final class EnumFlag<E extends Enum<E>> extends Flag<E> {
    public EnumFlag(String name,
                    Class<E> enumType,
                    ConflictStrategy<E> conflicts) {
        this(name, enumType, InheritanceMode.INHERIT, FlagDefault.unset(),
                conflicts, FlagScope.ANY_REGION, ActorScope.ANY,
                "rookieregions.region.flag");
    }

    public EnumFlag(String name,
                    Class<E> enumType,
                    InheritanceMode inheritance,
                    FlagDefault<E> defaultProvider,
                    ConflictStrategy<E> conflicts,
                    FlagScope regionScope,
                    ActorScope actorScope,
                    String modificationPermission) {
        super(Flag.<E>builder(name, enumType, codec(enumType))
                .inheritance(inheritance)
                .defaultProvider(defaultProvider)
                .conflictStrategy(conflicts)
                .scope(regionScope)
                .actorScope(actorScope)
                .modificationPermission(modificationPermission));
    }

    private static <E extends Enum<E>> FlagCodec<E> codec(Class<E> type) {
        if(type == null) {
            throw new IllegalArgumentException("enum flag type cannot be null");
        }
        return new FlagCodec<>() {
            @Override
            public Object encode(E value) {
                return value.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public E decode(Object encoded) {
                if(!(encoded instanceof String value)) {
                    throw new IllegalArgumentException("enum flag must be a string");
                }
                try {
                    return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(
                            "invalid " + type.getSimpleName() + " value: " + value,
                            exception
                    );
                }
            }
        };
    }
}
