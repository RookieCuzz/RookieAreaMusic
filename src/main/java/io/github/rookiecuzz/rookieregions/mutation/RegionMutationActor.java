package io.github.rookiecuzz.rookieregions.mutation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import io.github.rookiecuzz.rookieregions.rule.Subject;

/** Persistence- and Bukkit-neutral actor identity used for authorization. */
public final class RegionMutationActor {
    private final String actorId;
    private final UUID playerUuid;
    private final Set<String> groups;
    private final Set<String> permissions;

    public RegionMutationActor(String actorId,
                               UUID playerUuid,
                               Collection<String> groups,
                               Collection<String> permissions) {
        if(actorId == null || actorId.trim().isEmpty()){
            throw new IllegalArgumentException("actor ID must not be blank");
        }
        this.actorId = actorId.trim();
        this.playerUuid = playerUuid;
        this.groups = normalize(groups, "actor group");
        this.permissions = normalize(permissions, "permission");
    }

    public String actorId() {
        return actorId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public Set<String> groups() {
        return groups;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public boolean hasPermission(String permission){
        Objects.requireNonNull(permission, "permission cannot be null");
        String normalized = permission.trim().toLowerCase(Locale.ROOT);
        if(permissions.contains(MutationPermissions.ADMIN)
                || permissions.contains("rookieregions.*")
                || permissions.contains(normalized)){
            return true;
        }
        int separator = normalized.lastIndexOf('.');
        while(separator > 0){
            String wildcard = normalized.substring(0, separator) + ".*";
            if(permissions.contains(wildcard)){
                return true;
            }
            separator = normalized.lastIndexOf('.', separator - 1);
        }
        return false;
    }

    public Subject subject(){
        return new Subject(playerUuid, groups, permissions);
    }

    private static Set<String> normalize(Collection<String> source,
                                         String label){
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if(source != null){
            for(String value : source){
                if(value == null || value.trim().isEmpty()){
                    throw new IllegalArgumentException(label + " must not be blank");
                }
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }
}
