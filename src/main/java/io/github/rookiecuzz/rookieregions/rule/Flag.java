package io.github.rookiecuzz.rookieregions.rule;

import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Complete typed rule definition, including its resolution policies. */
public class Flag<T> {
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9._-]*");

    private final String name;
    private final Class<?> valueType;
    private final FlagCodec<T> codec;
    private final InheritanceMode inheritance;
    private final FlagDefault<T> defaultProvider;
    private final ConflictStrategy<T> conflictStrategy;
    private final FlagScope scope;
    private final ActorScope actorScope;
    private final String modificationPermission;
    private final UnaryOperator<T> normalizer;

    protected Flag(Builder<T> builder) {
        String normalizedName = Objects.requireNonNull(
                builder.name,
                "flag name cannot be null"
        ).trim().toLowerCase(Locale.ROOT);
        if(!NAME.matcher(normalizedName).matches()){
            throw new IllegalArgumentException("invalid flag name: " + builder.name);
        }
        this.name = normalizedName;
        this.valueType = Objects.requireNonNull(
                builder.valueType,
                "flag value type cannot be null"
        );
        this.codec = Objects.requireNonNull(builder.codec, "flag codec cannot be null");
        this.inheritance = Objects.requireNonNull(
                builder.inheritance,
                "flag inheritance cannot be null"
        );
        this.defaultProvider = Objects.requireNonNull(
                builder.defaultProvider,
                "flag default cannot be null"
        );
        this.conflictStrategy = Objects.requireNonNull(
                builder.conflictStrategy,
                "flag conflict strategy cannot be null"
        );
        this.scope = Objects.requireNonNull(builder.scope, "flag scope cannot be null");
        this.actorScope = Objects.requireNonNull(
                builder.actorScope, "flag actor scope cannot be null"
        );
        this.modificationPermission = requirePermission(
                builder.modificationPermission
        );
        this.normalizer = Objects.requireNonNull(
                builder.normalizer,
                "flag normalizer cannot be null"
        );
    }

    public static <T> Builder<T> builder(String name,
                                         Class<?> valueType,
                                         FlagCodec<T> codec) {
        return new Builder<>(name, valueType, codec);
    }

    public String name() {
        return name;
    }

    public Class<?> valueType() {
        return valueType;
    }

    public FlagCodec<T> codec() {
        return codec;
    }

    public InheritanceMode inheritance() {
        return inheritance;
    }

    public FlagDefault<T> defaultProvider() {
        return defaultProvider;
    }

    public ConflictStrategy<T> conflictStrategy() {
        return conflictStrategy;
    }

    public FlagScope scope() {
        return scope;
    }

    public ActorScope actorScope() {
        return actorScope;
    }

    public String modificationPermission() {
        return modificationPermission;
    }

    public FlagValue<T> value(T requested) {
        if(requested == null || !valueType.isInstance(requested)){
            throw new IllegalArgumentException(
                    "flag " + name + " requires " + valueType.getTypeName()
            );
        }
        T normalized = normalizer.apply(requested);
        if(normalized == null || !valueType.isInstance(normalized)){
            throw new IllegalArgumentException("flag normalizer returned an invalid value");
        }
        return new FlagValue<>(this, normalized);
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof Flag<?> other
                && name.equals(other.name)
                && valueType.equals(other.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, valueType);
    }

    @Override
    public String toString() {
        return name + "<" + valueType.getTypeName() + ">";
    }

    public static final class Builder<T> {
        private final String name;
        private final Class<?> valueType;
        private final FlagCodec<T> codec;
        private InheritanceMode inheritance = InheritanceMode.INHERIT;
        private FlagDefault<T> defaultProvider = FlagDefault.unset();
        private ConflictStrategy<T> conflictStrategy = ConflictStrategies.reject();
        private FlagScope scope = FlagScope.ANY_REGION;
        private ActorScope actorScope = ActorScope.ANY;
        private String modificationPermission = "rookieregions.region.flag";
        private UnaryOperator<T> normalizer = UnaryOperator.identity();

        private Builder(String name, Class<?> valueType, FlagCodec<T> codec) {
            this.name = name;
            this.valueType = valueType;
            this.codec = codec;
        }

        public Builder<T> inheritance(InheritanceMode value) {
            this.inheritance = value;
            return this;
        }

        public Builder<T> defaultProvider(FlagDefault<T> value) {
            this.defaultProvider = value;
            return this;
        }

        public Builder<T> conflictStrategy(ConflictStrategy<T> value) {
            this.conflictStrategy = value;
            return this;
        }

        public Builder<T> scope(FlagScope value) {
            this.scope = value;
            return this;
        }

        public Builder<T> actorScope(ActorScope value) {
            this.actorScope = value;
            return this;
        }

        public Builder<T> modificationPermission(String value) {
            this.modificationPermission = value;
            return this;
        }

        public Builder<T> normalizer(UnaryOperator<T> value) {
            this.normalizer = value;
            return this;
        }

        public Flag<T> build() {
            return new Flag<>(this);
        }
    }

    private static String requirePermission(String permission) {
        if(permission == null || permission.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "flag modification permission must not be blank"
            );
        }
        return permission.trim().toLowerCase(Locale.ROOT);
    }
}
