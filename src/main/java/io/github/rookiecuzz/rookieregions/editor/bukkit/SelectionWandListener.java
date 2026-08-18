package io.github.rookiecuzz.rookieregions.editor.bukkit;

import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.editor.model.BlockPoint;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/** Wooden-axe selection listener for active native editor sessions. */
public final class SelectionWandListener implements Listener {
    private final BukkitRegionEditor editor;

    public SelectionWandListener(BukkitRegionEditor editor) {
        this.editor = Objects.requireNonNull(editor, "Bukkit editor cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSelect(PlayerInteractEvent event){
        if(event.getHand() != EquipmentSlot.HAND
                || event.getItem() == null
                || event.getItem().getType() != Material.WOODEN_AXE
                || editor.session(event.getPlayer().getUniqueId()).isEmpty()){
            return;
        }
        Action action = event.getAction();
        if(action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_BLOCK){
            return;
        }
        Block block = event.getClickedBlock();
        if(block == null){
            return;
        }
        event.setCancelled(true);
        try {
            SelectionFeedback feedback = editor.select(
                    event.getPlayer().getUniqueId(),
                    BukkitWorlds.id(block.getWorld()),
                    new BlockPoint(block.getX(), block.getY(), block.getZ()),
                    action == Action.LEFT_CLICK_BLOCK
                            ? SelectionClick.PRIMARY
                            : SelectionClick.SECONDARY
            );
            event.getPlayer().sendMessage(
                    ChatColor.AQUA + "[RookieRegions] " + feedback.message()
            );
        } catch (RuntimeException exception){
            event.getPlayer().sendMessage(
                    ChatColor.RED + "[RookieRegions] " + safeMessage(exception)
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        cancel(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event){
        cancel(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event){
        cancel(event.getEntity());
    }

    private void cancel(org.bukkit.entity.Player player){
        if(editor.session(player.getUniqueId()).isPresent()){
            editor.cancel(player.getUniqueId());
        }
    }

    private String safeMessage(RuntimeException exception){
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
