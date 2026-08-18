package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.bukkit.BukkitSubjects;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.api.ProtectionDecision;
import io.github.rookiecuzz.rookieregions.api.ProtectionQuery;
import io.github.rookiecuzz.rookieregions.core.ApplicableRegionSet;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.rule.BuildAction;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.State;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.ChatColor;

/** Synchronous, allocation-light Paper protection facade. */
public final class ProtectionService {
    private final Supplier<RegionSnapshot> snapshots;
    private final BooleanSupplier notifyDenied;

    public ProtectionService(Supplier<RegionSnapshot> snapshots) {
        this(snapshots, () -> true);
    }

    public ProtectionService(Supplier<RegionSnapshot> snapshots,
                             BooleanSupplier notifyDenied) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshot supplier");
        this.notifyDenied = Objects.requireNonNull(
                notifyDenied, "denied notification supplier"
        );
    }

    public ProtectionDecision decide(Location location,
                                     Flag<State> flag,
                                     Player actor,
                                     String bypassSuffix) {
        return decide(pinnedQuery(), location, flag, actor, bypassSuffix);
    }

    public ProtectionDecision decide(RegionQuery query,
                                     Location location,
                                     Flag<State> flag,
                                     Player actor,
                                     String bypassSuffix) {
        Objects.requireNonNull(query, "pinned region query");
        Objects.requireNonNull(flag, "flag");
        Objects.requireNonNull(location, "protection location");
        return new ProtectionQuery(query).decide(
                BukkitWorlds.id(Objects.requireNonNull(
                        location.getWorld(), "protection world"
                )),
                location.getX(), location.getY(), location.getZ(),
                flag,
                actor == null ? io.github.rookiecuzz.rookieregions.rule.Subject.none()
                        : BukkitSubjects.from(actor),
                bypassSuffix
        );
    }

    public ProtectionDecision decideBuild(Location location,
                                          Player actor,
                                          BuildAction action) {
        return decideBuild(pinnedQuery(), location, actor, action);
    }

    public ProtectionDecision decideBuild(RegionQuery query,
                                          Location location,
                                          Player actor,
                                          BuildAction action) {
        Objects.requireNonNull(query, "pinned region query");
        Objects.requireNonNull(location, "protection location");
        return new ProtectionQuery(query).decideBuild(
                BukkitWorlds.id(Objects.requireNonNull(
                        location.getWorld(), "protection world"
                )),
                location.getX(), location.getY(), location.getZ(),
                BukkitSubjects.from(actor), action
        );
    }

    public ProtectionDecision decideContainer(Location location,
                                                Player actor) {
        return decideContainer(pinnedQuery(), location, actor);
    }

    public ProtectionDecision decideContainer(RegionQuery query,
                                                Location location,
                                                Player actor) {
        Objects.requireNonNull(query, "pinned region query");
        Objects.requireNonNull(location, "protection location");
        return new ProtectionQuery(query).decideContainer(
                BukkitWorlds.id(Objects.requireNonNull(
                        location.getWorld(), "protection world"
                )),
                location.getX(), location.getY(), location.getZ(),
                BukkitSubjects.from(actor)
        );
    }

    public ApplicableRegionSet regions(Location location) {
        return regions(pinnedQuery(), location);
    }

    public RegionQuery pinnedQuery() {
        return new RegionQuery(snapshots.get());
    }

    public ApplicableRegionSet regions(RegionQuery query, Location location) {
        Objects.requireNonNull(query, "pinned region query");
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location has no world");
        }
        return query.at(
                BukkitWorlds.id(location.getWorld()),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    public void notifyDenied(Player player, String message) {
        if(player != null && notifyDenied.getAsBoolean()) {
            player.sendMessage(ChatColor.RED + message);
        }
    }

}
