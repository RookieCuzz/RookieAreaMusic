package io.github.rookiecuzz.rookieareamusic.listener;

import io.github.rookiecuzz.rookieareamusic.editor.RegionEditSession;
import io.github.rookiecuzz.rookieareamusic.editor.RegionEditorService;
import io.github.rookiecuzz.rookieareamusic.editor.RegionEditorTool;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.meta.BundleMeta;

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
                    Block clickedBlock = event.getClickedBlock();
                    if(clickedBlock != null){
                        editor.addPoint(player, clickedBlock.getLocation());
                    }
                } else if(action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR){
                    if(player.isSneaking()){
                        editor.clearPoints(player);
                    } else {
                        editor.undoPoint(player);
                    }
                } else {
                    RegionEditSession session = editor.getSession(player.getUniqueId());
                    player.sendMessage(session != null && session.isAwaitingHeight()
                            ? "§e[RookieAreaMusic] 请用勾画笔右键一个方块确定切片 Y"
                            : "§e[RookieAreaMusic] 请右键方块添加顶点");
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
        if(editor.containsEditorTool(event.getPlayer().getInventory().getItemInMainHand())){
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event){
        if(editor.containsEditorTool(event.getItemDrop().getItemStack())){
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[RookieAreaMusic] 编辑工具不能丢弃");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event){
        if(editor.containsEditorTool(event.getMainHandItem())
                || editor.containsEditorTool(event.getOffHandItem())){
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event){
        boolean editorTool = editor.containsEditorTool(event.getCurrentItem())
                || editor.containsEditorTool(event.getCursor());
        boolean bundleInvolved = isBundle(event.getCurrentItem())
                || isBundle(event.getCursor());
        if(event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0){
            editorTool |= editor.containsEditorTool(
                    event.getWhoClicked().getInventory().getItem(event.getHotbarButton())
            );
        }
        if(event.getClick() == ClickType.SWAP_OFFHAND
                && event.getWhoClicked() instanceof Player){
            editorTool |= editor.containsEditorTool(
                    ((Player) event.getWhoClicked()).getInventory().getItemInOffHand()
            );
        }
        if(editorTool){
            if(!bundleInvolved && isSafeStorageMove(event)){
                return;
            }
            event.setCancelled(true);
            if(event.getWhoClicked() instanceof Player){
                ((Player) event.getWhoClicked()).sendMessage(
                        "§c[RookieAreaMusic] 编辑工具只能在背包和快捷栏之间移动"
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event){
        if(editor.containsEditorTool(event.getOldCursor())){
            int topSize = event.getView().getTopInventory().getSize();
            for(Integer rawSlot : event.getRawSlots()){
                int convertedSlot = event.getView().convertSlot(rawSlot);
                if(rawSlot < topSize || convertedSlot < 0 || convertedSlot >= 36){
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean isSafeStorageMove(InventoryClickEvent event){
        if(!(event.getClickedInventory() instanceof org.bukkit.inventory.PlayerInventory)){
            return false;
        }
        if(event.getSlot() < 0 || event.getSlot() >= 36 || event.isShiftClick()){
            return false;
        }
        ClickType click = event.getClick();
        return click != ClickType.DROP
                && click != ClickType.CONTROL_DROP
                && click != ClickType.CREATIVE
                && click != ClickType.SWAP_OFFHAND
                && click != ClickType.UNKNOWN;
    }

    private boolean isBundle(ItemStack item){
        return item != null
                && !item.getType().isAir()
                && item.getItemMeta() instanceof BundleMeta;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        editor.cancelImmediately(event.getPlayer(), false);
        editor.clearAreaPreview(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChanged(PlayerChangedWorldEvent event){
        boolean previewClosed = editor.clearAreaPreview(event.getPlayer().getUniqueId());
        if(editor.getSession(event.getPlayer().getUniqueId()) != null){
            editor.cancelImmediately(event.getPlayer(), false);
            event.getPlayer().sendMessage("§e[RookieAreaMusic] 由于切换世界，区域编辑已取消");
        }
        if(previewClosed){
            event.getPlayer().sendMessage("§e[RookieAreaMusic] 由于切换世界，区域轮廓预览已关闭");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event){
        boolean previewClosed = editor.clearAreaPreview(event.getEntity().getUniqueId());
        boolean removed = false;
        java.util.ListIterator<ItemStack> iterator = event.getDrops().listIterator();
        while(iterator.hasNext()){
            ItemStack item = iterator.next();
            if(!editor.containsEditorTool(item)){
                continue;
            }
            ItemStack retainedItem = editor.removeEditorTools(item);
            if(retainedItem == null || retainedItem.getType().isAir()){
                iterator.remove();
            } else {
                iterator.set(retainedItem);
            }
            removed = true;
        }
        if(editor.getSession(event.getEntity().getUniqueId()) != null){
            editor.cancelImmediately(event.getEntity(), false);
            event.getEntity().sendMessage("§e[RookieAreaMusic] 由于死亡，区域编辑已取消");
        } else if(removed){
            editor.removeTools(event.getEntity());
        }
        if(previewClosed){
            event.getEntity().sendMessage("§e[RookieAreaMusic] 由于死亡，区域轮廓预览已关闭");
        }
    }
}
