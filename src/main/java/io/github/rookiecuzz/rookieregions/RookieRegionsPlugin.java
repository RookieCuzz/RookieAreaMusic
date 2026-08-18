package io.github.rookiecuzz.rookieregions;

import io.github.rookiecuzz.rookieregions.api.RookieRegionsApi;
import io.github.rookiecuzz.rookieregions.api.DefaultRookieRegionsApi;
import io.github.rookiecuzz.rookieregions.api.RookieRegionsBootstrap;
import io.github.rookiecuzz.rookieregions.api.event.SnapshotPublishedEvent;
import io.github.rookiecuzz.rookieregions.api.event.RegionCreateEvent;
import io.github.rookiecuzz.rookieregions.api.event.RegionDeleteEvent;
import io.github.rookiecuzz.rookieregions.api.event.RegionUpdateEvent;
import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.config.RookieRegionsSettings;
import io.github.rookiecuzz.rookieregions.config.RegionRuntimeValidator;
import io.github.rookiecuzz.rookieregions.command.RootCommand;
import io.github.rookiecuzz.rookieregions.command.RegionAdministrationService;
import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.module.music.BukkitMusicService;
import io.github.rookiecuzz.rookieregions.module.commands.BukkitCommandModuleService;
import io.github.rookiecuzz.rookieregions.editor.bukkit.BukkitRegionEditor;
import io.github.rookiecuzz.rookieregions.editor.bukkit.SelectionWandListener;
import io.github.rookiecuzz.rookieregions.editor.model.RegionEditorManager;
import io.github.rookiecuzz.rookieregions.mutation.ConfirmationStore;
import io.github.rookiecuzz.rookieregions.mutation.PlacementPolicy;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationService;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationApi;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationPublication;
import io.github.rookiecuzz.rookieregions.mutation.SaveMode;
import io.github.rookiecuzz.rookieregions.persistence.RegionLoadException;
import io.github.rookiecuzz.rookieregions.persistence.RegionRepository;
import io.github.rookiecuzz.rookieregions.persistence.RepositoryMutationPort;
import io.github.rookiecuzz.rookieregions.persistence.RegionDocumentCodec;
import io.github.rookiecuzz.rookieregions.persistence.codec.ShapeJsonCodec;
import io.github.rookiecuzz.rookieregions.protection.BuildProtectionListener;
import io.github.rookiecuzz.rookieregions.protection.ExplosionProtectionListener;
import io.github.rookiecuzz.rookieregions.protection.ProtectionService;
import io.github.rookiecuzz.rookieregions.protection.PvpProtectionListener;
import io.github.rookiecuzz.rookieregions.protection.RegionTransitionListener;
import io.github.rookiecuzz.rookieregions.protection.UseProtectionListener;
import io.github.rookiecuzz.rookieregions.provider.WorldGuardProviderFactory;
import io.github.rookiecuzz.rookieregions.provider.NativeRegionProvider;
import io.github.rookiecuzz.rookieregions.provider.RegionProvider;
import io.github.rookiecuzz.rookieregions.provider.WorldGuardProvider;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class RookieRegionsPlugin extends JavaPlugin {
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(
            new IoThreadFactory()
    );

    private RegionRepository repository;
    private RookieRegionsBootstrap bootstrap;
    private FlagRegistry flagRegistry;
    private WorldGuardProvider worldGuardProvider;
    private RegionContainer regions;
    private RegionMutationService mutations;
    private RookieRegionsApi api;
    private ProtectionService protection;
    private RegionTransitionListener transitions;
    private BukkitCommandModuleService commands;
    private BukkitMusicService music;
    private BukkitTask musicTask;
    private volatile RookieRegionsSettings settings;
    private BukkitRegionEditor editor;
    private final Object publicationMonitor = new Object();
    private final ArrayDeque<RegionMutationPublication> publicationQueue =
            new ArrayDeque<>();
    private boolean publicationDrainScheduled;

    @Override
    public void onLoad() {
        bootstrap = new RookieRegionsBootstrap(
                this, ProtectionFlags.REGISTRY.values()
        );
        Bukkit.getServicesManager().register(
                RookieRegionsBootstrap.class,
                bootstrap,
                this,
                ServicePriority.Normal
        );
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RookieRegionsBootstrap.Snapshot registrations = bootstrap.freeze(this);
        flagRegistry = registrations.flags();
        try {
            settings = RookieRegionsSettings.load(getConfig());
            repository = new RegionRepository(
                    getDataFolder().toPath(),
                    new RegionDocumentCodec(
                            flagRegistry, ShapeJsonCodec.INSTANCE
                    )
            );
            RegionSnapshot initial = repository.load(0L, loadedWorldIds());
            RegionRuntimeValidator.validate(initial, settings);
            regions = new RegionContainer(initial);
        } catch (Exception exception) {
            getLogger().severe("RookieRegions could not stage its initial snapshot: "
                    + describe(exception));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        NativeRegionProvider nativeProvider = new NativeRegionProvider(regions);
        worldGuardProvider = WorldGuardProviderFactory.create(
                getServer().getPluginManager()
        );
        LinkedHashMap<String, RegionProvider> providers = new LinkedHashMap<>(
                registrations.providers()
        );
        providers.put(nativeProvider.id(), nativeProvider);
        providers.put(worldGuardProvider.id(), worldGuardProvider);
        api = new DefaultRookieRegionsApi(regions, flagRegistry, providers);
        Bukkit.getServicesManager().register(
                RookieRegionsApi.class,
                api,
                this,
                ServicePriority.Normal
        );
        RepositoryMutationPort mutationPort = new RepositoryMutationPort(
                repository, regions
        );
        RookieRegionsSettings initialSettings = settings;
        mutations = new RegionMutationService(
                mutationPort,
                new PlacementPolicy(),
                new ConfirmationStore(settings.confirmationLifetime()),
                ioExecutor,
                snapshot -> RegionRuntimeValidator.validate(
                        snapshot, initialSettings
                ),
                flagRegistry
        );
        mutations.addPublicationListener(this::onRegionMutationPublished);
        Bukkit.getServicesManager().register(
                RegionMutationApi.class,
                mutations,
                this,
                ServicePriority.Normal
        );
        editor = new BukkitRegionEditor(
                new RegionEditorManager(), mutations::invalidateConfirmations
        );
        getServer().getPluginManager().registerEvents(
                new SelectionWandListener(editor), this
        );
        new RootCommand(
                this,
                editor,
                new RegionAdministrationService(mutations, ioExecutor)
        ).register();
        protection = new ProtectionService(
                () -> regions.snapshot(),
                () -> settings.notifyDeniedActions()
        );
        registerRuntimeListeners();
        startMusic();

        getServer().getPluginManager().registerEvents(
                new WorldLifecycleListener(this),
                this
        );
        getLogger().info("RookieRegions enabled with "
                + regions.snapshot().records().size() + " region records.");
    }

    @Override
    public void onDisable() {
        synchronized(publicationMonitor) {
            publicationQueue.clear();
            publicationDrainScheduled = false;
        }
        if (musicTask != null) {
            musicTask.cancel();
        }
        if (music != null) {
            music.clear();
        }
        if (transitions != null) {
            transitions.clear();
        }
        if (editor != null) {
            editor.sessions().clear();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        ioExecutor.shutdownNow();
    }

    private void registerRuntimeListeners() {
        var manager = getServer().getPluginManager();
        manager.registerEvents(new BuildProtectionListener(protection), this);
        manager.registerEvents(new UseProtectionListener(protection), this);
        manager.registerEvents(new PvpProtectionListener(protection), this);
        manager.registerEvents(new ExplosionProtectionListener(protection), this);
        transitions = new RegionTransitionListener(
                this, regions, protection, flagRegistry
        );
        manager.registerEvents(transitions, this);
        commands = new BukkitCommandModuleService(
                this,
                regions,
                api.moduleBindings(),
                message -> getLogger().warning(message)
        );
        manager.registerEvents(commands, this);
    }

    private void startMusic() {
        if (musicTask != null) {
            musicTask.cancel();
        }
        if (music != null) {
            HandlerList.unregisterAll(music);
            music.clear();
        }
        music = new BukkitMusicService(
                regions,
                api.moduleBindings(),
                protection,
                settings.musicChannels(),
                message -> getLogger().warning(message)
        );
        getServer().getPluginManager().registerEvents(music, this);
        musicTask = Bukkit.getScheduler().runTaskTimer(
                this,
                music,
                settings.musicScanPeriodTicks(),
                settings.musicScanPeriodTicks()
        );
    }

    public CompletableFuture<Boolean> reloadAll(CommandSender sender) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("reloadAll must be started on the Paper thread");
        }
        RookieRegionsSettings stagedSettings;
        try {
            YamlConfiguration stagedConfig = new YamlConfiguration();
            stagedConfig.load(new java.io.File(getDataFolder(), "config.yml"));
            stagedSettings = RookieRegionsSettings.load(stagedConfig);
        } catch (Exception exception) {
            sender.sendMessage(ChatColor.RED + "Invalid config.yml: " + exception.getMessage());
            return CompletableFuture.completedFuture(false);
        }
        RegionSnapshot before = regions.snapshot();
        List<WorldId> worlds = loadedWorldIds();
        sender.sendMessage(ChatColor.GRAY + "Staging RookieRegions reload...");
        return CompletableFuture.supplyAsync(() -> {
            try {
                RegionSnapshot staged = repository.load(
                        before.revision() + 1L, worlds
                );
                RegionRuntimeValidator.validate(staged, stagedSettings);
                return staged;
            } catch (RegionLoadException exception) {
                throw new ReloadFailure(exception);
            }
        }, ioExecutor).handle((staged, failure) -> {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(this, () -> {
                if (failure != null) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    sender.sendMessage(ChatColor.RED + "Reload rejected; old snapshot kept: "
                            + describe(cause));
                    result.complete(false);
                    return;
                }
                RegionSnapshot published;
                try {
                    published = mutations.publishStagedReload(
                            before.revision(),
                            staged.records().values(),
                            candidate -> RegionRuntimeValidator.validate(
                                    candidate, stagedSettings
                            )
                    );
                } catch (io.github.rookiecuzz.rookieregions.mutation.RevisionConflictException conflict) {
                    sender.sendMessage(ChatColor.RED
                            + "Reload became stale because regions changed; retry it.");
                    result.complete(false);
                    return;
                } catch (Exception exception) {
                    sender.sendMessage(ChatColor.RED
                            + "Reload rejected; old snapshot kept: "
                            + describe(exception));
                    result.complete(false);
                    return;
                }
                settings = stagedSettings;
                reloadConfig();
                mutations.updateConfirmationLifetime(
                        stagedSettings.confirmationLifetime()
                );
                mutations.invalidateAllConfirmations();
                editor.sessions().clear();
                worldGuardProvider.refresh();
                startMusic();
                reconcileRuntime(published);
                Bukkit.getPluginManager().callEvent(
                        new SnapshotPublishedEvent(before, published)
                );
                sender.sendMessage(ChatColor.GREEN + "RookieRegions reloaded at revision "
                        + published.revision() + ".");
                result.complete(true);
            });
            return result;
        }).thenCompose(value -> value);
    }

    public RookieRegionsApi api() {
        return api;
    }

    public RegionMutationService mutations() {
        return mutations;
    }

    public RookieRegionsSettings settings() {
        return settings;
    }

    /** Reconciles online-player physical, flag, command, and music state after a write. */
    public void reconcileRuntime() {
        reconcileRuntime(regions.snapshot());
    }

    /** Reconciles all runtime consumers against exactly one publication. */
    public void reconcileRuntime(RegionSnapshot snapshot) {
        if(!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("runtime reconciliation requires the Paper thread");
        }
        if(transitions != null) {
            transitions.clear();
            transitions.reconcileOnlinePlayers(snapshot);
        }
        if(commands != null) {
            commands.clear();
            commands.reconcileOnlinePlayers(snapshot);
        }
        if(music != null) {
            music.reconcileOnlinePlayers(snapshot);
        }
    }

    private void onRegionMutationPublished(RegionMutationPublication publication) {
        boolean schedule;
        synchronized(publicationMonitor) {
            publicationQueue.addLast(publication);
            schedule = !publicationDrainScheduled;
            publicationDrainScheduled = true;
        }
        if(schedule) {
            Bukkit.getScheduler().runTask(this, this::drainMutationPublications);
        }
    }

    private void drainMutationPublications() {
        while(true) {
            RegionMutationPublication publication;
            synchronized(publicationMonitor) {
                publication = publicationQueue.pollFirst();
                if(publication == null) {
                    publicationDrainScheduled = false;
                    return;
                }
            }
            if(isEnabled()) {
                try {
                    publishRuntimeEvents(publication);
                } catch(RuntimeException failure) {
                    getLogger().severe(
                            "Runtime publication reconciliation failed at revision "
                                    + publication.currentSnapshot().revision()
                                    + ": " + describe(failure)
                    );
                }
            }
        }
    }

    private void publishRuntimeEvents(RegionMutationPublication publication) {
        if(editor != null && publication.actor().playerUuid() != null
                && editor.session(publication.actor().playerUuid()).isPresent()) {
            try {
                editor.markSaved(
                        publication.actor().playerUuid(), publication.sessionId()
                );
            } catch(IllegalStateException ignored) {
                // A non-editor API mutation or a newer editor session.
            }
        }
        try {
            switch(publication.mode()) {
                case CREATE -> Bukkit.getPluginManager().callEvent(
                        new RegionCreateEvent(
                                publication.currentRegion().orElseThrow(),
                                publication.actor().subject()
                        )
                );
                case EDIT -> Bukkit.getPluginManager().callEvent(
                        new RegionUpdateEvent(
                                publication.previousRegion().orElseThrow(),
                                publication.currentRegion().orElseThrow(),
                                publication.actor().subject()
                        )
                );
                case DELETE -> Bukkit.getPluginManager().callEvent(
                        new RegionDeleteEvent(
                                publication.previousRegion().orElseThrow(),
                                publication.actor().subject()
                        )
                );
            }
            reconcileRuntime(publication.currentSnapshot());
        } finally {
            Bukkit.getPluginManager().callEvent(new SnapshotPublishedEvent(
                    publication.previousSnapshot(), publication.currentSnapshot()
            ));
        }
    }

    private List<WorldId> loadedWorldIds() {
        return Bukkit.getWorlds().stream().map(BukkitWorlds::id).toList();
    }

    private static String describe(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current != current.getCause()) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static final class ReloadFailure extends RuntimeException {
        private ReloadFailure(Throwable cause) {
            super(cause);
        }
    }

    private static final class IoThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "RookieRegions-IO");
            thread.setDaemon(true);
            return thread;
        }
    }
}
