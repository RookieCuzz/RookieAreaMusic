package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.api.event.RegionEnterAttemptEvent;
import io.github.rookiecuzz.rookieregions.api.event.RegionEnterEvent;
import io.github.rookiecuzz.rookieregions.api.event.RegionLeaveEvent;
import io.github.rookiecuzz.rookieregions.api.event.EffectiveFlagChangeEvent;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitSubjects;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.core.ApplicableRegionSet;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionGraph;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;
import io.github.rookiecuzz.rookieregions.rule.RuleResolution;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Entry enforcement and physical Region enter/leave command transitions. */
public final class RegionTransitionListener implements Listener {
    private final Plugin plugin;
    private final RegionContainer snapshots;
    private final ProtectionService protection;
    private final FlagRegistry flags;
    private final RegionTransitionPlanner planner = new RegionTransitionPlanner();
    private final EffectiveFlagTracker flagTracker = new EffectiveFlagTracker();
    private final Map<UUID, RegionTransitionPlanner.Observation> observations =
            new HashMap<>();
    private final Map<UUID, RegionTransitionPlanner.Observation> retainedForReconcile =
            new HashMap<>();
    private final Map<UUID, EffectiveFlagTracker.Observation> flagObservations =
            new HashMap<>();
    private final Map<UUID, EffectiveFlagTracker.Observation> retainedFlagsForReconcile =
            new HashMap<>();

    public RegionTransitionListener(Plugin plugin,
                                    RegionContainer snapshots,
                                    ProtectionService protection) {
        this(plugin, snapshots, protection, ProtectionFlags.REGISTRY);
    }

    public RegionTransitionListener(Plugin plugin,
                                    RegionContainer snapshots,
                                    ProtectionService protection,
                                    FlagRegistry flags) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots cannot be null");
        this.protection = Objects.requireNonNull(
                protection, "protection service cannot be null"
        );
        this.flags = Objects.requireNonNull(flags, "flag registry cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void enforceMoveEntry(PlayerMoveEvent event) {
        enforceEntry(event, MovementRoute.MOVE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void enforceTeleportEntry(PlayerTeleportEvent event) {
        enforceEntry(event, MovementRoute.TELEPORT);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void enforcePortalEntry(PlayerPortalEvent event) {
        enforceEntry(event, MovementRoute.PORTAL);
    }

    private void enforceEntry(PlayerMoveEvent event,
                              MovementRoute registeredRoute) {
        if(movementRoute(event.getClass()) != registeredRoute) {
            return;
        }
        Location destination = event.getTo();
        if (destination == null || positionUnchanged(event.getFrom(), destination)) {
            return;
        }
        RegionSnapshot snapshot = snapshots.snapshot();
        ApplicableRegionSet before = regions(snapshot, event.getFrom());
        ApplicableRegionSet after = regions(snapshot, destination);
        Subject subject = BukkitSubjects.from(event.getPlayer());
        boolean enteredLocal = entersNewLocal(before, after);
        for (Region region : after.localRegions()) {
            if (before.containsLocal(region.key())) {
                continue;
            }
            RegionEnterAttemptEvent attempt = new RegionEnterAttemptEvent(
                    subject,
                    region
            );
            Bukkit.getPluginManager().callEvent(attempt);
            if (attempt.isCancelled()) {
                event.setCancelled(true);
                protection.notifyDenied(
                        event.getPlayer(), "You cannot enter this region."
                );
                return;
            }
        }
        if (enteredLocal && !entryAllowed(after, event.getPlayer(), subject)) {
            event.setCancelled(true);
            protection.notifyDenied(
                    event.getPlayer(), "You cannot enter this region."
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observeMove(PlayerMoveEvent event) {
        observeMovement(event, MovementRoute.MOVE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observeTeleport(PlayerTeleportEvent event) {
        observeMovement(event, MovementRoute.TELEPORT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observePortal(PlayerPortalEvent event) {
        observeMovement(event, MovementRoute.PORTAL);
    }

    private void observeMovement(PlayerMoveEvent event,
                                 MovementRoute registeredRoute) {
        if(movementRoute(event.getClass()) != registeredRoute) {
            return;
        }
        Location destination = event.getTo();
        if (destination == null || positionUnchanged(event.getFrom(), destination)) {
            return;
        }
        RegionSnapshot snapshot = snapshots.snapshot();
        ApplicableRegionSet before = regions(snapshot, event.getFrom());
        ApplicableRegionSet after = regions(snapshot, destination);
        Subject subject = BukkitSubjects.from(event.getPlayer());
        RegionTransitionPlanner.Observation physicalBefore = planner.plan(
                null,
                presences(snapshot, before)
        ).next();
        EffectiveFlagTracker.Observation flagsBefore = flagTracker.capture(
                before,
                subject,
                flags.values()
        );
        reconcile(
                event.getPlayer(),
                snapshot,
                after,
                subject,
                physicalBefore,
                flagsBefore
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                reconcile(event.getPlayer(), event.getPlayer().getLocation());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        observations.remove(event.getPlayer().getUniqueId());
        retainedForReconcile.remove(event.getPlayer().getUniqueId());
        flagObservations.remove(event.getPlayer().getUniqueId());
        retainedFlagsForReconcile.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        retainedForReconcile.clear();
        retainedForReconcile.putAll(observations);
        observations.clear();
        retainedFlagsForReconcile.clear();
        retainedFlagsForReconcile.putAll(flagObservations);
        flagObservations.clear();
    }

    public void reconcileOnlinePlayers() {
        reconcileOnlinePlayers(snapshots.snapshot());
    }

    /** Reconciles every player against exactly the supplied publication. */
    public void reconcileOnlinePlayers(RegionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "reconcile snapshot cannot be null");
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                reconcile(player, player.getLocation(), snapshot);
            }
        } finally {
            retainedForReconcile.clear();
            retainedFlagsForReconcile.clear();
        }
    }

    private void reconcile(Player player, Location location) {
        reconcile(player, location, snapshots.snapshot());
    }

    private void reconcile(Player player,
                           Location location,
                           RegionSnapshot snapshot) {
        ApplicableRegionSet applicable = regions(snapshot, location);
        reconcile(
                player,
                snapshot,
                applicable,
                BukkitSubjects.from(player),
                null,
                null
        );
    }

    private void reconcile(Player player,
                           RegionSnapshot snapshot,
                           ApplicableRegionSet applicable,
                           Subject subject,
                           RegionTransitionPlanner.Observation physicalBefore,
                           EffectiveFlagTracker.Observation flagsBefore) {
        List<RegionTransitionPlanner.Presence> current = presences(
                snapshot, applicable
        );
        UUID playerId = player.getUniqueId();
        RegionTransitionPlanner.Observation previous = physicalBefore;
        if(previous == null){
            previous = observations.get(playerId);
        }
        if (previous == null) {
            previous = retainedForReconcile.remove(playerId);
        }
        RegionTransitionPlanner.Plan transition = planner.plan(previous, current);
        observations.put(playerId, transition.next());

        EffectiveFlagTracker.Observation previousFlags = flagsBefore;
        if(previousFlags == null){
            previousFlags = flagObservations.get(playerId);
        }
        if(previousFlags == null){
            previousFlags = retainedFlagsForReconcile.remove(playerId);
        }
        EffectiveFlagTracker.Observation currentFlags = flagTracker.capture(
                applicable,
                subject,
                flags.values()
        );
        List<EffectiveFlagTracker.Change<?>> flagChanges = flagTracker.changes(
                previousFlags,
                currentFlags
        );
        flagObservations.put(playerId, currentFlags);

        for (RegionTransitionPlanner.Presence left : transition.left()) {
            Bukkit.getPluginManager().callEvent(
                    new RegionLeaveEvent(subject, left.region())
            );
        }
        for (RegionTransitionPlanner.Presence entered : transition.entered()) {
            Bukkit.getPluginManager().callEvent(
                    new RegionEnterEvent(subject, entered.region())
            );
        }
        for(EffectiveFlagTracker.Change<?> change : flagChanges){
            fireEffectiveFlagChange(subject, currentFlags, change);
        }
    }

    private static List<RegionTransitionPlanner.Presence> presences(
            RegionSnapshot snapshot,
            ApplicableRegionSet applicable) {
        RegionGraph graph = snapshot.graph();
        List<RegionTransitionPlanner.Presence> current = new java.util.ArrayList<>();
        for (Region region : applicable.localRegions()) {
            int depth = 0;
            for (Region ancestor : graph.ancestors(region.key())) {
                if (!ancestor.key().isGlobal()) {
                    depth++;
                }
            }
            current.add(new RegionTransitionPlanner.Presence(
                    region, depth, RegionCommandProfile.empty()
            ));
        }
        return List.copyOf(current);
    }

    private static <T> void fireEffectiveFlagChange(
            Subject subject,
            EffectiveFlagTracker.Observation observation,
            EffectiveFlagTracker.Change<T> change) {
        RuleResolution<T> previous = change.previous();
        RuleResolution<T> current = change.current();
        Bukkit.getPluginManager().callEvent(new EffectiveFlagChangeEvent<>(
                subject,
                observation.world(),
                previous,
                current
        ));
    }

    private static ApplicableRegionSet regions(RegionSnapshot snapshot,
                                               Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location has no world");
        }
        return new RegionQuery(snapshot).at(
                BukkitWorlds.id(location.getWorld()),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    private static boolean entryAllowed(ApplicableRegionSet regions,
                                        Player player,
                                        Subject subject) {
        if (player.hasPermission("rookieregions.admin")
                || player.hasPermission("rookieregions.bypass.entry")) {
            return true;
        }
        return regions.resolve(ProtectionFlags.ENTRY, subject)
                .value()
                .orElse(State.ALLOW) == State.ALLOW;
    }

    private static boolean positionUnchanged(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0;
    }

    static MovementRoute movementRoute(Class<?> eventType) {
        Objects.requireNonNull(eventType, "movement event type cannot be null");
        if(PlayerPortalEvent.class.isAssignableFrom(eventType)) {
            return MovementRoute.PORTAL;
        }
        if(PlayerTeleportEvent.class.isAssignableFrom(eventType)) {
            return MovementRoute.TELEPORT;
        }
        if(PlayerMoveEvent.class.isAssignableFrom(eventType)) {
            return MovementRoute.MOVE;
        }
        throw new IllegalArgumentException(
                "not a player movement event type: " + eventType.getName()
        );
    }

    static boolean entersNewLocal(ApplicableRegionSet before,
                                  ApplicableRegionSet after) {
        Objects.requireNonNull(before, "before regions cannot be null");
        Objects.requireNonNull(after, "after regions cannot be null");
        return after.localRegions().stream()
                .anyMatch(region -> !before.containsLocal(region.key()));
    }

    enum MovementRoute {
        MOVE,
        TELEPORT,
        PORTAL
    }
}
