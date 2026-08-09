package com.gitee.niocho.areamusic.listener;

import com.gitee.niocho.areamusic.editor.RegionEditorService;
import com.gitee.niocho.areamusic.editor.RegionEditorTool;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class RegionEditorListener implements Listener {
    private final RegionEditorService editor;

    public RegionEditorListener(RegionEditorService editor) {
        this.editor = editor;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event){
        if(event.getHand() != EquipmentSlot.HAND){
            return;
        }
        RegionEditorTool tool = editor.identifyTool(event.getItem());
        if(tool == null){
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if(editor.getSession(player.getUniqueId()) == null){
            editor.removeTools(player);
            player.sendMessage("§c[RookieAreaMusic] 编辑会话已经失效，工具已清理");
            return;
        }

        Action action = event.getAction();
        switch (tool){
            case POINT:
                if(action == Action.RIGHT_CLICK_BLOCK){
                    editor.addPoint(player, event.getClickedBlock().getLocation());
                } else if(action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR){
                    if(player.isSneaking()){
                        editor.clearPoints(player);
                    } else {
                        editor.undoPoint(player);
                    }
                } else {
                    player.sendMessage("§e[RookieAreaMusic] 请右键方块添加顶点");
                }
                break;
            case NEXT:
                editor.nextSlice(player, player.isSneaking());
                break;
            case PREVIOUS:
                editor.previousSlice(player);
                break;
            case FINISH:
                editor.finish(player);
                break;
            case CANCEL:
                editor.requestCancel(player);
                break;
            default:
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEntityEvent event){
        if(editor.identifyTool(event.getPlayer().getInventory().getItemInMainHand()) != null){
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event){
        if(editor.identifyTool(event.getItemDrop().getItemStack()) != null){
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[RookieAreaMusic] 编辑工具不能丢弃");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event){
        if(editor.identifyTool(event.getMainHandItem()) != null
                || editor.identifyTool(event.getOffHandItem()) != null){
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event){
        boolean editorTool = editor.identifyTool(event.getCurrentItem()) != null
                || editor.identifyTool(event.getCursor()) != null;
        if(event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0){
            editorTool |= editor.identifyTool(
                    event.getWhoClicked().getInventory().getItem(event.getHotbarButton())
            ) != null;
        }
        if(event.getClick() == ClickType.SWAP_OFFHAND
                && event.getWhoClicked() instanceof Player){
            editorTool |= editor.identifyTool(
                    ((Player) event.getWhoClicked()).getInventory().getItemInOffHand()
            ) != null;
        }
        if(editorTool){
            event.setCancelled(true);
            if(event.getWhoClicked() instanceof Player){
                ((Player) event.getWhoClicked()).sendMessage("§c[RookieAreaMusic] 编辑工具不能移动或存入容器");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event){
        if(editor.identifyTool(event.getOldCursor()) != null){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        editor.cancelImmediately(event.getPlayer(), false);
    }

    @EventHandler
    public void onWorldChanged(PlayerChangedWorldEvent event){
        if(editor.getSession(event.getPlayer().getUniqueId()) != null){
            editor.cancelImmediately(event.getPlayer(), false);
            event.getPlayer().sendMessage("§e[RookieAreaMusic] 由于切换世界，区域编辑已取消");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event){
        boolean removed = false;
        java.util.Iterator<ItemStack> iterator = event.getDrops().iterator();
        while(iterator.hasNext()){
            if(editor.identifyTool(iterator.next()) != null){
                iterator.remove();
                removed = true;
            }
        }
        if(editor.getSession(event.getEntity().getUniqueId()) != null){
            editor.cancelImmediately(event.getEntity(), false);
            event.getEntity().sendMessage("§e[RookieAreaMusic] 由于死亡，区域编辑已取消");
        } else if(removed){
            editor.removeTools(event.getEntity());
        }
    }
}
