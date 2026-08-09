package com.gitee.niocho.areamusic.listener;

import com.gitee.niocho.areamusic.RookieAreaMusic;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final RookieAreaMusic plugin;

    public PlayerQuitListener(RookieAreaMusic plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEvent(PlayerQuitEvent event){
        plugin.clearPlayerState(event.getPlayer().getUniqueId());
    }
}
