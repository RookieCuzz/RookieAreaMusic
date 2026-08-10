package io.github.rookiecuzz.rookieareamusic.listener;

import io.github.rookiecuzz.rookieareamusic.RookieAreaMusic;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Restarts custom sounds once a CraftEngine-managed pack is ready client-side.
 * This listener intentionally depends only on Bukkit's public API.
 */
public final class PlayerResourcePackStatusListener implements Listener {
    private final RookieAreaMusic plugin;

    public PlayerResourcePackStatusListener(RookieAreaMusic plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event){
        if(!plugin.isCraftEngineAvailable()
                || event.getStatus()
                != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED){
            return;
        }
        plugin.onResourcePackLoaded(event.getPlayer().getUniqueId());
    }
}
