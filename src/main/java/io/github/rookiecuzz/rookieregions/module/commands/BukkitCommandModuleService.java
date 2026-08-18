package io.github.rookiecuzz.rookieregions.module.commands;

import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.runtime.BoundModuleRegion;
import io.github.rookiecuzz.rookieregions.runtime.ModuleBindingIssue;
import io.github.rookiecuzz.rookieregions.runtime.ModuleBindingResolution;
import io.github.rookiecuzz.rookieregions.api.ModuleBindingQuery;
import io.github.rookiecuzz.rookieregions.runtime.ModuleKind;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Physical enter/leave commands resolved independently of native protection. */
public final class BukkitCommandModuleService implements Listener {
    private final Plugin plugin;
    private final RegionContainer snapshots;
    private final ModuleBindingQuery bindings;
    private final Consumer<String> diagnosticSink;
    private final CommandTransitionResolver transitions =
            new CommandTransitionResolver();
    private final Map<UUID, Observation> observations = new HashMap<>();
    private final Map<UUID, Observation> retainedForReconcile = new HashMap<>();
    private final Set<String> emittedDiagnostics = new java.util.HashSet<>();

    public BukkitCommandModuleService(Plugin plugin,
                                      RegionContainer snapshots,
                                      ModuleBindingQuery bindings,
                                      Consumer<String> diagnosticSink) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.snapshots = Objects.requireNonNull(
                snapshots,
                "snapshot container cannot be null"
        );
        this.bindings = Objects.requireNonNull(
                bindings,
                "module binding resolver cannot be null"
        );
        this.diagnosticSink = Objects.requireNonNull(
                diagnosticSink,
                "module diagnostic sink cannot be null"
        );
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if(event.getPlayer().isOnline()) {
                reconcile(
                        event.getPlayer(),
                        snapshots.snapshot(),
                        event.getPlayer().getLocation(),
                        null
                );
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        observations.remove(player);
        retainedForReconcile.remove(player);
    }

    /** Retains old profiles so a snapshot change can still execute leave commands. */
    public void clear() {
        retainedForReconcile.clear();
        retainedForReconcile.putAll(observations);
        observations.clear();
    }

    public void reconcileOnlinePlayers(RegionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "command reconcile snapshot cannot be null");
        try {
            for(Player player : Bukkit.getOnlinePlayers()) {
                reconcile(player, snapshot, player.getLocation(), null);
            }
        } finally {
            retainedForReconcile.clear();
        }
    }

    private void observeMovement(PlayerMoveEvent event,
                                 MovementRoute registeredRoute) {
        if(movementRoute(event.getClass()) != registeredRoute) {
            return;
        }
        Location destination = event.getTo();
        if(destination == null || positionUnchanged(event.getFrom(), destination)) {
            return;
        }
        RegionSnapshot snapshot = snapshots.snapshot();
        UUID player = event.getPlayer().getUniqueId();
        Observation before = observations.get(player);
        if(before == null) {
            before = capture(snapshot, event.getFrom());
        }
        reconcile(event.getPlayer(), snapshot, destination, before);
    }

    private void reconcile(Player player,
                           RegionSnapshot snapshot,
                           Location location,
                           Observation explicitPrevious) {
        Observation current = capture(snapshot, location);
        UUID playerId = player.getUniqueId();
        Observation previous = explicitPrevious;
        if(previous == null) {
            previous = observations.get(playerId);
        }
        if(previous == null) {
            previous = retainedForReconcile.remove(playerId);
        }
        if(previous == null) {
            previous = Observation.empty();
        }

        LinkedHashMap<String, RegionCommandProfile> profiles = new LinkedHashMap<>();
        previous.presences().forEach((identity, presence) ->
                profiles.put(identity, presence.profile())
        );
        current.presences().forEach((identity, presence) ->
                profiles.put(identity, presence.profile())
        );
        List<RegionPresence> currentPresence = current.presences().entrySet().stream()
                .map(entry -> new RegionPresence(
                        entry.getKey(),
                        entry.getValue().depth()
                ))
                .toList();
        CommandTransition transition = transitions.resolve(
                previous.state(),
                currentPresence,
                profiles
        );

        LinkedHashMap<String, Presence> metadata = new LinkedHashMap<>(
                previous.presences()
        );
        metadata.putAll(current.presences());
        for(RegionCommandAction action : transition.getActions()) {
            Presence presence = metadata.get(action.getRegionKey());
            if(presence == null) {
                continue;
            }
            String command = action.getCommand()
                    .replace("{player}", player.getName())
                    .replace("{uuid}", playerId.toString())
                    .replace("{region}", presence.externalRegionId())
                    .replace("{provider}", presence.providerId())
                    .replace("{profile}", presence.profileRegion());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        observations.put(playerId, new Observation(
                current.presences(),
                transition.getNextState()
        ));
    }

    private Observation capture(RegionSnapshot snapshot, Location location) {
        if(location.getWorld() == null) {
            return Observation.empty();
        }
        ModuleBindingResolution resolution = bindings.resolveAt(
                snapshot,
                ModuleKind.COMMANDS,
                BukkitWorlds.id(location.getWorld()),
                location.getX(),
                location.getY(),
                location.getZ()
        );
        emitDiagnostics(resolution.issues());
        LinkedHashMap<String, Presence> presences = new LinkedHashMap<>();
        for(BoundModuleRegion bound : resolution.regions()) {
            presences.put(bound.identity(), new Presence(
                    bound.depth(),
                    bound.providerId(),
                    bound.externalRegionId(),
                    bound.profileRegion().toString(),
                    bound.profile().commands()
            ));
        }
        Map<String, Presence> immutable = Map.copyOf(presences);
        Map<String, Integer> active = new LinkedHashMap<>();
        immutable.forEach((identity, presence) ->
                active.put(identity, presence.depth())
        );
        return new Observation(immutable, CommandTransitionState.of(active));
    }

    private void emitDiagnostics(Collection<ModuleBindingIssue> issues) {
        for(ModuleBindingIssue issue : issues) {
            String text = issue.code() + " for " + issue.module() + " profile "
                    + issue.profileRegion() + ": " + issue.message();
            if(emittedDiagnostics.add(text)) {
                diagnosticSink.accept(text);
            }
        }
    }

    private static MovementRoute movementRoute(Class<?> eventType) {
        if(PlayerPortalEvent.class.isAssignableFrom(eventType)) {
            return MovementRoute.PORTAL;
        }
        if(PlayerTeleportEvent.class.isAssignableFrom(eventType)) {
            return MovementRoute.TELEPORT;
        }
        return MovementRoute.MOVE;
    }

    private static boolean positionUnchanged(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0;
    }

    private record Presence(int depth,
                            String providerId,
                            String externalRegionId,
                            String profileRegion,
                            RegionCommandProfile profile) {
    }

    private record Observation(Map<String, Presence> presences,
                               CommandTransitionState state) {
        private Observation {
            presences = Map.copyOf(presences);
            Objects.requireNonNull(state, "command state cannot be null");
        }

        private static Observation empty() {
            return new Observation(Map.of(), CommandTransitionState.empty());
        }
    }

    private enum MovementRoute {
        MOVE,
        TELEPORT,
        PORTAL
    }
}
