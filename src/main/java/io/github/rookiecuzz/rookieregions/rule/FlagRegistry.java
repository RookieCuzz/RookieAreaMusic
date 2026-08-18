package io.github.rookiecuzz.rookieregions.rule;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable registry frozen before region documents are decoded. */
public final class FlagRegistry {
    private final Map<String, Flag<?>> flags;

    public FlagRegistry(Collection<Flag<?>> definitions) {
        LinkedHashMap<String, Flag<?>> copy = new LinkedHashMap<>();
        if(definitions != null){
            for(Flag<?> flag : definitions){
                if(flag == null || copy.putIfAbsent(flag.name(), flag) != null){
                    throw new IllegalArgumentException(
                            "flag registry contains null or duplicate definitions"
                    );
                }
            }
        }
        this.flags = Collections.unmodifiableMap(copy);
    }

    public Optional<Flag<?>> find(String name) {
        if(name == null){
            return Optional.empty();
        }
        return Optional.ofNullable(flags.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    public Flag<?> require(String name) {
        return find(name).orElseThrow(() ->
                new IllegalArgumentException("unknown flag: " + name));
    }

    public Collection<Flag<?>> values() {
        return flags.values();
    }
}
