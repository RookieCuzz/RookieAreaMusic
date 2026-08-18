package io.github.rookiecuzz.rookieregions.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Immutable domain of player UUIDs and permission-provider group IDs. */
public final class RegionDomain {
    private static final RegionDomain EMPTY = new RegionDomain(Set.of(), Set.of());

    private final Set<UUID> players;
    private final Set<String> groups;

    public RegionDomain(Collection<UUID> players, Collection<String> groups) {
        LinkedHashSet<UUID> playerCopy = new LinkedHashSet<>();
        if(players != null){
            for(UUID player : players){
                if(player == null){
                    throw new IllegalArgumentException("domain player cannot be null");
                }
                playerCopy.add(player);
            }
        }
        LinkedHashSet<String> groupCopy = new LinkedHashSet<>();
        if(groups != null){
            for(String group : groups){
                groupCopy.add(normalizeGroup(group));
            }
        }
        this.players = Collections.unmodifiableSet(playerCopy);
        this.groups = Collections.unmodifiableSet(groupCopy);
    }

    public static RegionDomain empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<UUID> players() {
        return players;
    }

    public Set<String> groups() {
        return groups;
    }

    public boolean contains(UUID player, Set<String> subjectGroups) {
        if(player != null && players.contains(player)){
            return true;
        }
        if(subjectGroups != null){
            for(String group : subjectGroups){
                if(group != null && groups.contains(normalizeGroup(group))){
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return players.isEmpty() && groups.isEmpty();
    }

    private static String normalizeGroup(String group) {
        if(group == null){
            throw new IllegalArgumentException("domain group cannot be null");
        }
        String normalized = group.trim().toLowerCase(Locale.ROOT);
        if(normalized.isEmpty() || normalized.indexOf('\0') >= 0){
            throw new IllegalArgumentException("invalid domain group: " + group);
        }
        return normalized;
    }

    public static final class Builder {
        private final Set<UUID> players = new LinkedHashSet<>();
        private final Set<String> groups = new LinkedHashSet<>();

        public Builder player(UUID player) {
            if(player == null){
                throw new IllegalArgumentException("domain player cannot be null");
            }
            players.add(player);
            return this;
        }

        public Builder group(String group) {
            groups.add(normalizeGroup(group));
            return this;
        }

        public RegionDomain build() {
            return players.isEmpty() && groups.isEmpty()
                    ? EMPTY
                    : new RegionDomain(players, groups);
        }
    }
}
