package io.github.rookiecuzz.rookieregions;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

final class WorldLifecycleListener implements Listener {
    private final RookieRegionsPlugin plugin;
    private boolean queued;

    WorldLifecycleListener(RookieRegionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLoad(WorldLoadEvent event) {
        queueReload();
    }

    @EventHandler
    public void onUnload(WorldUnloadEvent event) {
        queueReload();
    }

    private void queueReload() {
        if (queued) {
            return;
        }
        queued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            queued = false;
            plugin.reloadAll(Bukkit.getConsoleSender());
        });
    }
}
