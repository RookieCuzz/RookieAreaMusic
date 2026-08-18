package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.FlagValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable core region. Module-specific data deliberately lives elsewhere. */
public final class Region {
    private final RegionKey key;
    private final RegionShape shape;
    private final int priority;
    private final RegionKey parent;
    private final RegionDomain owners;
    private final RegionDomain members;
    private final Map<String, FlagValue<?>> flags;

    private Region(Builder builder) {
        this.key = Objects.requireNonNull(builder.key, "region key cannot be null");
        this.shape = Objects.requireNonNull(builder.shape, "region shape cannot be null");
        this.priority = builder.priority;
        this.parent = builder.parent;
        this.owners = builder.owners == null ? RegionDomain.empty() : builder.owners;
        this.members = builder.members == null ? RegionDomain.empty() : builder.members;
        this.flags = Collections.unmodifiableMap(new LinkedHashMap<>(builder.flags));
    }

    public static Builder builder(RegionKey key, RegionShape shape) {
        return new Builder(key, shape);
    }

    public RegionKey key() {
        return key;
    }

    public RegionShape shape() {
        return shape;
    }

    public int priority() {
        return priority;
    }

    public Optional<RegionKey> parent() {
        return Optional.ofNullable(parent);
    }

    public RegionDomain owners() {
        return owners;
    }

    public RegionDomain members() {
        return members;
    }

    public Map<String, FlagValue<?>> flags() {
        return flags;
    }

    public <T> Optional<FlagValue<T>> flag(Flag<T> definition) {
        Objects.requireNonNull(definition, "flag definition cannot be null");
        FlagValue<?> stored = flags.get(definition.name());
        if(stored == null){
            return Optional.empty();
        }
        if(!definition.equals(stored.flag())){
            throw new IllegalStateException(
                    "incompatible flag definition stored for " + definition.name()
            );
        }
        @SuppressWarnings("unchecked")
        FlagValue<T> typed = (FlagValue<T>) stored;
        return Optional.of(typed);
    }

    public static final class Builder {
        private final RegionKey key;
        private final RegionShape shape;
        private int priority;
        private RegionKey parent;
        private RegionDomain owners = RegionDomain.empty();
        private RegionDomain members = RegionDomain.empty();
        private final Map<String, FlagValue<?>> flags = new LinkedHashMap<>();

        private Builder(RegionKey key, RegionShape shape) {
            this.key = key;
            this.shape = shape;
        }

        public Builder priority(int value) {
            this.priority = value;
            return this;
        }

        public Builder parent(RegionKey value) {
            this.parent = value;
            return this;
        }

        public Builder owners(RegionDomain value) {
            this.owners = value;
            return this;
        }

        public Builder members(RegionDomain value) {
            this.members = value;
            return this;
        }

        public <T> Builder flag(Flag<T> definition, T value) {
            return flagValue(definition.value(value));
        }

        public Builder flagValue(FlagValue<?> value) {
            Objects.requireNonNull(value, "flag value cannot be null");
            FlagValue<?> previous = flags.putIfAbsent(value.flag().name(), value);
            if(previous != null){
                throw new IllegalArgumentException(
                        "duplicate region flag: " + value.flag().name()
                );
            }
            return this;
        }

        public Region build() {
            return new Region(this);
        }
    }
}
