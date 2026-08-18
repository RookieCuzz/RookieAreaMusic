package example;

import io.github.rookiecuzz.rookieregions.api.ApiCapability;
import io.github.rookiecuzz.rookieregions.api.RookieRegionsApi;
import io.github.rookiecuzz.rookieregions.api.RookieRegionsBootstrap;
import io.github.rookiecuzz.rookieregions.api.event.RegionEnterEvent;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitSubjects;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.rule.BuildAction;
import io.github.rookiecuzz.rookieregions.rule.StateFlag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class RookieRegionsIntegrationExample extends JavaPlugin
        implements Listener {
    public static final StateFlag FLIGHT = new StateFlag("example.flight");

    private RookieRegionsApi regions;

    @Override
    public void onLoad() {
        RookieRegionsBootstrap bootstrap = Bukkit.getServicesManager()
                .load(RookieRegionsBootstrap.class);
        if(bootstrap == null) {
            throw new IllegalStateException("RookieRegions bootstrap unavailable");
        }
        bootstrap.registerFlag(this, FLIGHT);
    }

    @Override
    public void onEnable() {
        regions = Bukkit.getServicesManager().load(RookieRegionsApi.class);
        if(regions == null || !regions.supports(ApiCapability.SNAPSHOT_QUERY)) {
            throw new IllegalStateException("RookieRegions API unavailable");
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    public boolean canBreak(Player player) {
        var location = player.getLocation();
        return regions.protection().decideBuild(
                BukkitWorlds.id(location.getWorld()),
                location.getX(), location.getY(), location.getZ(),
                BukkitSubjects.from(player),
                BuildAction.BREAK
        ).allowed();
    }

    @EventHandler
    public void onEnter(RegionEnterEvent event) {
        getLogger().info(event.subject().playerId()
                + " entered " + event.region().key().id());
    }
}
