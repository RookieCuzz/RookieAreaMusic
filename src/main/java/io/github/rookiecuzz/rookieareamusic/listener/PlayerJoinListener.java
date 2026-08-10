package io.github.rookiecuzz.rookieareamusic.listener;

import io.github.rookiecuzz.rookieareamusic.RookieAreaMusic;
import io.github.rookiecuzz.rookieareamusic.player.PlayerLocationSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener{
    private final RookieAreaMusic plugin;

    public PlayerJoinListener(RookieAreaMusic plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEvent(PlayerJoinEvent event){
        Player player = event.getPlayer();
        PlayerLocationSnapshot location = PlayerLocationSnapshot.from(player.getLocation());
        plugin.clearPlayerState(player.getUniqueId());
        if(location == null){
            return;
        }
        plugin.submitPlayerSnapshot(player.getUniqueId(), location);
    }
}
