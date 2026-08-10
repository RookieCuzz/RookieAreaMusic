package io.github.rookiecuzz.rookieareamusic.listener;

import io.github.rookiecuzz.rookieareamusic.RookieAreaMusic;
import io.github.rookiecuzz.rookieareamusic.player.PlayerLocationSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PlayerTeleportListener implements Listener {
    private final RookieAreaMusic plugin;

    public PlayerTeleportListener(RookieAreaMusic plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEvent(PlayerTeleportEvent event){
        Player player = event.getPlayer();
        PlayerLocationSnapshot to = PlayerLocationSnapshot.from(event.getTo());
        if(to == null){
            return;
        }
        plugin.submitPlayerSnapshot(player.getUniqueId(), to);
    }
}
