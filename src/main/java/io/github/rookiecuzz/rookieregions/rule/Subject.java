package io.github.rookiecuzz.rookieregions.rule;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Bukkit-independent snapshot of an actor used by synchronous rule queries. */
public final class Subject {
    private static final Subject NONE = new Subject(null, Set.of(), Set.of());

    private final UUID playerId;
    private final Set<String> groups;
    private final Set<String> permissions;

    public Subject(UUID playerId,
                   Collection<String> groups,
                   Collection<String> permissions) {
        this.playerId = playerId;
        this.groups = normalized(groups);
        this.permissions = normalized(permissions);
    }

    public static Subject none() {
        return NONE;
    }

    public static Subject player(UUID playerId) {
        if(playerId == null){
            throw new IllegalArgumentException("player subject UUID cannot be null");
        }
        return new Subject(playerId, Set.of(), Set.of());
    }

    public UUID playerId() {
        return playerId;
    }

    public Set<String> groups() {
        return groups;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public boolean hasPermission(String permission) {
        if(permission == null) {
            return false;
        }
        String normalized = permission.trim().toLowerCase(Locale.ROOT);
        if(normalized.isEmpty()) {
            return false;
        }
        if(permissions.contains(normalized) || permissions.contains("*")) {
            return true;
        }
        int separator = normalized.lastIndexOf('.');
        while(separator > 0) {
            if(permissions.contains(normalized.substring(0, separator) + ".*")) {
                return true;
            }
            separator = normalized.lastIndexOf('.', separator - 1);
        }
        return false;
    }

    private static Set<String> normalized(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if(values != null){
            for(String value : values){
                if(value == null || value.trim().isEmpty()){
                    throw new IllegalArgumentException("subject token cannot be blank");
                }
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
