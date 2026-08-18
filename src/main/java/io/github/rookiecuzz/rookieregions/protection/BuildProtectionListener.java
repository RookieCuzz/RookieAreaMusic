package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.rule.BuildAction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitSubjects;

public final class BuildProtectionListener implements Listener {
    private final ProtectionService protection;

    public BuildProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!protection.decideBuild(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                event.getPlayer(),
                BuildAction.BREAK
        ).allowed()) {
            event.setCancelled(true);
            protection.notifyDenied(
                    event.getPlayer(), "You cannot build in this region."
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!protection.decideBuild(
                event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5),
                event.getPlayer(),
                BuildAction.PLACE
        ).allowed()) {
            event.setCancelled(true);
            protection.notifyDenied(
                    event.getPlayer(), "You cannot build in this region."
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        if (event.getPlayer().hasPermission("rookieregions.admin")
                || event.getPlayer().hasPermission("rookieregions.bypass.build")
                || event.getPlayer().hasPermission("rookieregions.bypass.block-place")) {
            return;
        }
        var query = protection.pinnedQuery();
        var subject = BukkitSubjects.from(event.getPlayer());
        boolean denied = event.getReplacedBlockStates().stream().anyMatch(state ->
                !query.allowsBuild(
                        BukkitWorlds.id(state.getWorld()),
                        state.getX() + 0.5d,
                        state.getY() + 0.5d,
                        state.getZ() + 0.5d,
                        subject,
                        BuildAction.PLACE
                )
        );
        if (denied) {
            event.setCancelled(true);
            protection.notifyDenied(
                    event.getPlayer(), "You cannot build in this region."
            );
        }
    }
}
