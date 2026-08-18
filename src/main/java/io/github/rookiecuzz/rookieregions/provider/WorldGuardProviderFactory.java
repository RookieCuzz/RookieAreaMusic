package io.github.rookiecuzz.rookieregions.provider;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;

/** Creates the optional WorldGuard provider without linking its API. */
public final class WorldGuardProviderFactory {
    private WorldGuardProviderFactory() {
    }

    public static WorldGuardProvider create(PluginManager pluginManager) {
        Objects.requireNonNull(pluginManager, "Bukkit plugin manager cannot be null");
        Plugin plugin = pluginManager.getPlugin("WorldGuard");
        if(plugin == null){
            return UnavailableWorldGuardProvider.because(
                    "WorldGuard plugin is not installed"
            );
        }
        if(!plugin.isEnabled()){
            return UnavailableWorldGuardProvider.because(
                    "WorldGuard plugin is disabled"
            );
        }
        return create(new BukkitWorldGuardReflectionFacade(
                pluginManager,
                Bukkit::getWorlds
        ));
    }

    /** Injection seam used by tests and non-standard class loaders. */
    public static ReflectiveWorldGuardProvider create(
            WorldGuardReflectionFacade facade) {
        return new ReflectiveWorldGuardProvider(facade);
    }
}
