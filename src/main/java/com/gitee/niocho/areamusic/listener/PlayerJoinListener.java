package com.gitee.niocho.areamusic.listener;

import com.gitee.niocho.areamusic.RookieAreaMusic;
import com.gitee.niocho.areamusic.player.PlayerLocationSnapshot;
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
