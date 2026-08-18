package io.github.rookiecuzz.rookieregions.module.music;

import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.protection.ProtectionService;
import io.github.rookiecuzz.rookieregions.provider.NativeRegionProvider;
import io.github.rookiecuzz.rookieregions.runtime.BoundModuleRegion;
import io.github.rookiecuzz.rookieregions.runtime.ModuleBindingIssue;
import io.github.rookiecuzz.rookieregions.runtime.ModuleBindingResolution;
import io.github.rookiecuzz.rookieregions.runtime.ModuleBindingResolver;
import io.github.rookiecuzz.rookieregions.api.ModuleBindingQuery;
import io.github.rookiecuzz.rookieregions.runtime.ModuleKind;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Main-thread music scanner backed by the pure hierarchy resolver. */
public final class BukkitMusicService implements Runnable, Listener {
    private final RegionContainer snapshots;
    private final ModuleBindingQuery moduleBindings;
    private final Map<String, MusicChannelDefinition> definitions;
    private final RegionMusicResolver resolver = new RegionMusicResolver();
    private final MusicPlaybackPlanner playback = new MusicPlaybackPlanner();
    private final Map<UUID, Map<String, MusicPlaybackPlanner.ActiveTrack>> sessions =
            new HashMap<>();
    private final Consumer<String> diagnosticSink;
    private final java.util.Set<String> emittedDiagnostics = new java.util.HashSet<>();

    public BukkitMusicService(RegionContainer snapshots,
                              ProtectionService protection,
                              Map<String, MusicChannelDefinition> definitions) {
        this(
                snapshots,
                new ModuleBindingResolver(Map.of(
                        NativeRegionProvider.ID,
                        new NativeRegionProvider(snapshots)
                )),
                protection,
                definitions,
                message -> Bukkit.getLogger().warning("[RookieRegions] " + message)
        );
    }

    public BukkitMusicService(RegionContainer snapshots,
                              ModuleBindingQuery moduleBindings,
                              ProtectionService protection,
                              Map<String, MusicChannelDefinition> definitions,
                              Consumer<String> diagnosticSink) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshot container");
        this.moduleBindings = Objects.requireNonNull(
                moduleBindings,
                "module binding resolver"
        );
        Objects.requireNonNull(protection, "protection service");
        this.definitions = Map.copyOf(definitions);
        this.diagnosticSink = Objects.requireNonNull(
                diagnosticSink,
                "module diagnostic sink"
        );
    }

    @Override
    public void run() {
        reconcileOnlinePlayers(snapshots.snapshot());
    }

    /** Applies music using one immutable snapshot for the complete scan. */
    public void reconcileOnlinePlayers(RegionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "music reconcile snapshot cannot be null");
        for (Player player : Bukkit.getOnlinePlayers()) {
            reconcile(player, snapshot);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopAll(event.getPlayer());
    }

    public void clear() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopAll(player);
        }
        sessions.clear();
    }

    private void reconcile(Player player, RegionSnapshot snapshot) {
        Location location = player.getLocation();
        if(location.getWorld() == null){
            return;
        }
        ModuleBindingResolution applicable = moduleBindings.resolveAt(
                snapshot,
                ModuleKind.MUSIC,
                BukkitWorlds.id(location.getWorld()),
                location.getX(),
                location.getY(),
                location.getZ()
        );
        emitDiagnostics(applicable.issues());
        Collection<RegionMusicRecord> records = applicableMusic(applicable.regions());
        MusicResolution resolution = resolver.resolve(records, definitions);
        Map<String, ResolvedMusicLayer> desired = new LinkedHashMap<>();

        for (ResolvedMusicChannel channel : resolution.getChannels().values()) {
            int layerIndex = 0;
            for (ResolvedMusicLayer layer : channel.getLayers()) {
                if (layer.getTracks().isEmpty()) {
                    continue;
                }
                String playbackKey = channel.getDefinition().getName()
                        + ":" + layerIndex++ + ":" + layer.getRegionKey();
                desired.put(playbackKey, layer);
            }
        }

        UUID playerId = player.getUniqueId();
        Map<String, MusicPlaybackPlanner.ActiveTrack> active = sessions.getOrDefault(
                playerId,
                Map.of()
        );
        long now = System.nanoTime();
        MusicPlaybackPlanner.Plan plan = playback.plan(
                active,
                desired,
                now,
                bound -> ThreadLocalRandom.current().nextInt(bound)
        );
        for(String sound : plan.stopSounds()){
            player.stopSound(sound, SoundCategory.MUSIC);
        }
        for(MusicPlaybackPlanner.Start start : plan.starts()){
            MusicPlaybackPlanner.ActiveTrack track = start.track();
            player.playSound(
                    location,
                    track.track().getSound(),
                    SoundCategory.MUSIC,
                    track.volume(),
                    track.pitch()
            );
        }
        if(plan.next().isEmpty()){
            sessions.remove(playerId);
        }else{
            sessions.put(playerId, plan.next());
        }
    }

    private static Collection<RegionMusicRecord> applicableMusic(
            Collection<BoundModuleRegion> applicable) {
        return applicable.stream()
                .map(bound -> new RegionMusicRecord(
                        bound.identity(),
                        bound.parentIdentity().orElse(null),
                        bound.profile().music()
                ))
                .toList();
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

    private void stopAll(Player player) {
        Map<String, MusicPlaybackPlanner.ActiveTrack> active = sessions.remove(
                player.getUniqueId()
        );
        if (active == null) {
            return;
        }
        active.values().stream()
                .map(track -> track.track().getSound())
                .distinct()
                .forEach(sound -> player.stopSound(sound, SoundCategory.MUSIC));
    }
}
