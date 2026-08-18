package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public final class ExplosionProtectionListener implements Listener {
    private final ProtectionService protection;

    public ExplosionProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        removeDenied(event.blockList(), null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        removeDenied(
                event.blockList(),
                ResponsiblePlayerResolver.from(event.getEntity())
        );
    }

    private void removeDenied(List<Block> blocks, Player actor) {
        var query = protection.pinnedQuery();
        blocks.removeIf(block -> !protection.decide(
                query,
                new Location(
                        block.getWorld(),
                        block.getX() + 0.5d,
                        block.getY() + 0.5d,
                        block.getZ() + 0.5d
                ),
                ProtectionFlags.EXPLOSION,
                actor,
                "explosion"
        ).allowed());
    }
}
