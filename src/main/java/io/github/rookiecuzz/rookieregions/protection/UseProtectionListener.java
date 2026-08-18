package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.InventoryHolder;

public final class UseProtectionListener implements Listener {
    private final ProtectionService protection;

    public UseProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Location location = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        boolean container = event.getClickedBlock().getState() instanceof InventoryHolder
                || event.getClickedBlock().getType() == org.bukkit.Material.ENDER_CHEST;
        boolean allowed = container
                ? protection.decideContainer(location, event.getPlayer()).allowed()
                : protection.decide(
                        location,
                        ProtectionFlags.USE,
                        event.getPlayer(),
                        "use"
                ).allowed();
        if (!allowed) {
            event.setCancelled(true);
            protection.notifyDenied(
                    event.getPlayer(), "You cannot use that in this region."
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        if(isPreciseEntityInteraction(event.getClass())) {
            return;
        }
        handleEntityUse(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreciseEntityUse(PlayerInteractAtEntityEvent event) {
        handleEntityUse(event);
    }

    private void handleEntityUse(PlayerInteractEntityEvent event) {
        boolean container = usesContainerRule(event.getRightClicked());
        boolean allowed = container
                ? protection.decideContainer(
                        event.getRightClicked().getLocation(),
                        event.getPlayer()
                ).allowed()
                : protection.decide(
                        event.getRightClicked().getLocation(),
                        ProtectionFlags.USE,
                        event.getPlayer(),
                        "use"
                ).allowed();
        if (!allowed) {
            event.setCancelled(true);
            protection.notifyDenied(
                    event.getPlayer(), "You cannot use that in this region."
            );
        }
    }

    static boolean isPreciseEntityInteraction(Class<?> eventType) {
        return PlayerInteractAtEntityEvent.class.isAssignableFrom(eventType);
    }

    static boolean usesContainerRule(Object target) {
        return target instanceof InventoryHolder;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainer(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location != null && !protection.decideContainer(
                location, player
        ).allowed()) {
            event.setCancelled(true);
            protection.notifyDenied(
                    player, "You cannot use that in this region."
            );
        }
    }
}
