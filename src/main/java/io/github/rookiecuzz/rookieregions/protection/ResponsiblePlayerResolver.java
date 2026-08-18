package io.github.rookiecuzz.rookieregions.protection;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

/** Resolves the player responsible for a bounded Bukkit entity-source chain. */
public final class ResponsiblePlayerResolver {
    public static Player from(Entity source){
        return resolve(
                source,
                entity -> entity instanceof Player player ? player : null,
                ResponsiblePlayerResolver::parentSource
        );
    }

    /** Pure identity-based chain traversal shared with focused tests. */
    static <N, P> P resolve(N source,
                            Function<? super N, ? extends P> player,
                            Function<? super N, ? extends N> parent){
        if(player == null || parent == null){
            throw new IllegalArgumentException(
                    "player and parent resolvers cannot be null"
            );
        }
        Set<N> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        N current = source;
        while(current != null && visited.add(current)){
            P resolved = player.apply(current);
            if(resolved != null){
                return resolved;
            }
            current = parent.apply(current);
        }
        return null;
    }

    private static Entity parentSource(Entity source){
        if(source instanceof TNTPrimed tnt){
            return tnt.getSource();
        }
        if(source instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter){
            return shooter;
        }
        if(source instanceof Tameable tameable
                && tameable.getOwner() instanceof Entity owner){
            return owner;
        }
        return null;
    }

    private ResponsiblePlayerResolver(){
    }
}
