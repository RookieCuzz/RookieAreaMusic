package io.github.rookiecuzz.rookieregions.core;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stable world identity plus its namespaced-key metadata. */
public final class WorldId implements Comparable<WorldId> {
    private static final Pattern KEY = Pattern.compile(
            "[a-z0-9._-]+:[a-z0-9/._-]+"
    );

    private final UUID uuid;
    private final String namespacedKey;

    public WorldId(UUID uuid, String namespacedKey) {
        this.uuid = Objects.requireNonNull(uuid, "world UUID cannot be null");
        if(namespacedKey == null){
            throw new IllegalArgumentException("world namespaced key cannot be null");
        }
        String normalized = namespacedKey.trim().toLowerCase(Locale.ROOT);
        if(!KEY.matcher(normalized).matches()){
            throw new IllegalArgumentException(
                    "invalid world namespaced key: " + namespacedKey
            );
        }
        this.namespacedKey = normalized;
    }

    public UUID uuid() {
        return uuid;
    }

    public String namespacedKey() {
        return namespacedKey;
    }

    @Override
    public int compareTo(WorldId other) {
        return uuid.compareTo(other.uuid);
    }

    /** World UUID is identity; the namespaced key is diagnostic metadata. */
    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof WorldId other && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return namespacedKey + "[" + uuid + "]";
    }
}
