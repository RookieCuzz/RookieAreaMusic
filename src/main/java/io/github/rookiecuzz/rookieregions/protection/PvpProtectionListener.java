package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class PvpProtectionListener implements Listener {
    private final ProtectionService protection;

    public PvpProtectionListener(ProtectionService protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = ResponsiblePlayerResolver.from(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!protection.decide(
                victim.getLocation(),
                ProtectionFlags.PVP,
                attacker,
                "pvp"
        ).allowed()) {
            event.setCancelled(true);
            protection.notifyDenied(
                    attacker, "PvP is disabled in this region."
            );
        }
    }

}
