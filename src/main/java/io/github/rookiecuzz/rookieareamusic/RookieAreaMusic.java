package io.github.rookiecuzz.rookieareamusic;

import io.github.rookiecuzz.rookieareamusic.async.PlayerTaskCoordinator;
import io.github.rookiecuzz.rookieareamusic.command.EntryCommandDispatcher;
import io.github.rookiecuzz.rookieareamusic.command.MainExecutor;
import io.github.rookiecuzz.rookieareamusic.command.MainTabCompleter;
import io.github.rookiecuzz.rookieareamusic.command.RegionCommandActivationRegistry;
import io.github.rookiecuzz.rookieareamusic.config.AreaDto;
import io.github.rookiecuzz.rookieareamusic.config.ConfigManager;
import io.github.rookiecuzz.rookieareamusic.editor.RegionEditorService;
import io.github.rookiecuzz.rookieareamusic.geometry.SlicedPolygonVolume;
import io.github.rookiecuzz.rookieareamusic.listener.RegionEditorListener;
import io.github.rookiecuzz.rookieareamusic.listener.PlayerResourcePackStatusListener;
import io.github.rookiecuzz.rookieareamusic.listener.PlayerJoinListener;
import io.github.rookiecuzz.rookieareamusic.listener.PlayerQuitListener;
import io.github.rookiecuzz.rookieareamusic.listener.PlayerTeleportListener;
import io.github.rookiecuzz.rookieareamusic.music.PlaybackOperation;
import io.github.rookiecuzz.rookieareamusic.music.PlaybackSequenceTracker;
import io.github.rookiecuzz.rookieareamusic.music.PlayerPlaybackSession;
import io.github.rookiecuzz.rookieareamusic.music.RecordingPlaybackSink;
import io.github.rookiecuzz.rookieareamusic.music.SelectedTrack;
import io.github.rookiecuzz.rookieareamusic.player.PlayerLocationSnapshot;
import io.github.rookiecuzz.rookieareamusic.runtime.RuntimeState;
import io.github.rookiecuzz.rookieareamusic.spatial.PlayerRegionCache;
import io.github.rookiecuzz.rookieareamusic.spatial.RegionSpatialIndex;
import io.github.rookiecuzz.rookieareamusic.source.SoundSource;
import io.github.rookiecuzz.rookieareamusic.source.SoundSourcePlaybackEngine;
import io.github.rookiecuzz.rookieareamusic.source.SoundSourceSink;
import io.github.rookiecuzz.rookieareamusic.utils.MusicUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class RookieAreaMusic extends JavaPlugin {
    private static final int ASYNC_SCAN_WORKERS = 4;

    private ConfigManager configManager;
    private BukkitTask task;
    private RegionEditorService regionEditorService;
    private MusicUtil musicUtil;
    private EntryCommandDispatcher entryCommandDispatcher;
    private final PlaybackSequenceTracker playbackSequenceTracker =
            new PlaybackSequenceTracker();
    private final PlayerRegionCache playerRegionCache =
            new PlayerRegionCache();
    private final PlayerTaskCoordinator playerTaskCoordinator =
            new PlayerTaskCoordinator();
    private final SoundSourcePlaybackEngine soundSourcePlaybackEngine =
            new SoundSourcePlaybackEngine();
    private final SoundSourceSink soundSourceSink = new SoundSourceSink() {
        @Override
        public boolean play(SoundSource source) {
            return playSoundSource(source);
        }

        @Override
        public void stop(SoundSource source) {
            stopSoundSource(source);
        }
    };
    private final ConcurrentMap<UUID, PlayerPlaybackSession> playbackSessions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PlayerOutputQueue> outputQueues =
            new ConcurrentHashMap<>();
    private final RegionCommandActivationRegistry commandActivations =
            new RegionCommandActivationRegistry();
    private final ConcurrentMap<UUID, Long> resourcePackReloadRevisions =
            new ConcurrentHashMap<>();
    private final Set<UUID> pendingPlaybackRestarts =
            ConcurrentHashMap.newKeySet();
    private final AtomicLong resourcePackReloadSequence = new AtomicLong();
    private final AtomicBoolean playerScanRunning = new AtomicBoolean();
    private final AtomicBoolean rescanRequested = new AtomicBoolean();
    private volatile RuntimeState runtimeState = RuntimeState.empty();
    private volatile boolean craftEngineAvailable;
    private volatile boolean stopping;

    @Override
    public void onEnable() {
        stopping = false;
        try {
            configManager = new ConfigManager(this);
            musicUtil = new MusicUtil(this);
            configManager.load();
            publishRuntimeState();
            entryCommandDispatcher = new EntryCommandDispatcher(this);
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "RookieAreaMusic 配置加载失败，插件将被停用", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerTeleportListener(this), this);
        getServer().getPluginManager().registerEvents(
                new PlayerResourcePackStatusListener(this),
                this
        );
        regionEditorService = new RegionEditorService(this);
        getServer().getPluginManager().registerEvents(
                new RegionEditorListener(regionEditorService),
                this
        );
        regionEditorService.start();
        craftEngineAvailable = getServer().getPluginManager()
                .isPluginEnabled("CraftEngine");
        if(craftEngineAvailable){
            getLogger().info(
                    "检测到 CraftEngine：将直接使用其资源包 Sound Event，"
                            + "并在资源包加载成功后刷新玩家播放状态"
            );
        }
        task = createScanTask();
        Objects.requireNonNull(getServer().getPluginCommand("am"))
                .setExecutor(new MainExecutor(this));
        Objects.requireNonNull(getServer().getPluginCommand("am"))
                .setTabCompleter(new MainTabCompleter(this));
    }

    public void onReload() throws IOException {
        configManager.reload();
        RuntimeState loaded = buildRuntimeState();

        BukkitTask newTask;
        try {
            newTask = createScanTask();
        } catch (RuntimeException e) {
            throw new IOException("无法重新创建区域音乐检查任务", e);
        }

        if(regionEditorService != null){
            regionEditorService.stop();
        }
        playerTaskCoordinator.invalidateAll();
        runtimeState = loaded;
        EntryCommandDispatcher dispatcher = entryCommandDispatcher;
        if(dispatcher != null){
            dispatcher.setCommandsEnabled(getConfig().getBoolean(
                    "actions.commands.enabled",
                    true
            ));
        }
        playerRegionCache.clearAll();
        BukkitTask oldTask = task;
        task = newTask;
        if(oldTask != null){
            oldTask.cancel();
        }
        if(regionEditorService != null){
            regionEditorService.start();
        }
        playbackSequenceTracker.clearAll();
        tickSoundSources();
        requestImmediateScan();
    }

    @Override
    public void onDisable() {
        stopping = true;
        if(regionEditorService != null){
            regionEditorService.stop();
            regionEditorService = null;
        }
        if(task != null){
            task.cancel();
            task = null;
        }
        playerTaskCoordinator.invalidateAll();

        for(Map.Entry<UUID, PlayerPlaybackSession> entry
                : playbackSessions.entrySet()){
            RecordingPlaybackSink sink = new RecordingPlaybackSink();
            entry.getValue().clear(sink);
            applyPlaybackOperations(entry.getKey(), sink.snapshot());
        }
        playbackSessions.clear();
        outputQueues.clear();
        commandActivations.clearAll();
        resourcePackReloadRevisions.clear();
        pendingPlaybackRestarts.clear();
        playbackSequenceTracker.clearAll();
        soundSourcePlaybackEngine.clear(soundSourceSink);
        playerRegionCache.clearAll();
        playerTaskCoordinator.clear();
        playerScanRunning.set(false);
        rescanRequested.set(false);
        runtimeState = RuntimeState.empty();
        craftEngineAvailable = false;
        entryCommandDispatcher = null;

        if(configManager != null
                && configManager.getAreas() != null
                && configManager.getMusics() != null){
            try {
                configManager.unload();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "RookieAreaMusic 配置保存失败", e);
            }
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RegionEditorService getRegionEditorService() {
        return regionEditorService;
    }

    public int nextSequentialIndex(UUID playerUuid,
                                   String areaUuid,
                                   int playlistSize){
        return playbackSequenceTracker.next(
                playerUuid,
                areaUuid,
                playlistSize
        );
    }

    public void clearPlayerState(UUID playerUuid){
        if(playerUuid == null){
            return;
        }
        playerTaskCoordinator.remove(playerUuid);
        resourcePackReloadRevisions.remove(playerUuid);
        pendingPlaybackRestarts.remove(playerUuid);
        commandActivations.clearPlayer(playerUuid);
        playbackSequenceTracker.clear(playerUuid);
        playerRegionCache.clear(playerUuid);
        PlayerPlaybackSession session = playbackSessions.remove(playerUuid);
        if(session != null){
            session.clear(null);
        }
        PlayerOutputQueue queue = outputQueues.remove(playerUuid);
        if(queue != null){
            queue.operations.clear();
        }
    }

    public void rebuildSpatialIndex(){
        RuntimeState loaded = buildRuntimeState();
        playerTaskCoordinator.invalidateAll();
        runtimeState = loaded;
        playerRegionCache.clearAll();
        requestImmediateScan();
    }

    public RuntimeState getRuntimeState() {
        return runtimeState;
    }

    public PlayerRegionCache getPlayerRegionCache() {
        return playerRegionCache;
    }

    public PlayerPlaybackSession getOrCreatePlaybackSession(UUID playerUuid){
        return playbackSessions.computeIfAbsent(
                playerUuid,
                ignored -> new PlayerPlaybackSession()
        );
    }

    public boolean isCraftEngineAvailable(){
        return craftEngineAvailable;
    }

    /**
     * Coalesces successful resource-pack responses and restarts this player's
     * logical playback after the client has made CraftEngine sounds available.
     */
    public void onResourcePackLoaded(UUID playerUuid){
        if(playerUuid == null || stopping || !craftEngineAvailable){
            return;
        }
        long revision = resourcePackReloadSequence.incrementAndGet();
        resourcePackReloadRevisions.put(playerUuid, revision);
        try {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if(stopping || !resourcePackReloadRevisions.remove(
                        playerUuid,
                        Long.valueOf(revision)
                )){
                    return;
                }
                Player player = Bukkit.getPlayer(playerUuid);
                if(player == null || !player.isOnline()){
                    return;
                }
                PlayerLocationSnapshot location = PlayerLocationSnapshot.from(
                        player.getLocation()
                );
                if(location != null){
                    restartPlayerSnapshot(playerUuid, location);
                }
            }, 10L);
        } catch (RuntimeException e){
            resourcePackReloadRevisions.remove(
                    playerUuid,
                    Long.valueOf(revision)
            );
            getLogger().log(
                    Level.WARNING,
                    "无法提交资源包加载后的玩家声音刷新任务",
                    e
            );
        }
    }

    public void submitPlayerSnapshot(UUID playerUuid,
                                     PlayerLocationSnapshot location){
        if(playerUuid == null || location == null || stopping){
            return;
        }
        long revision = playerTaskCoordinator.nextRevision(playerUuid);
        try {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                if(stopping){
                    return;
                }
                playerTaskCoordinator.runIfCurrent(
                        playerUuid,
                        revision,
                        () -> processPlayerSnapshot(playerUuid, location)
                );
            });
        } catch (RuntimeException e){
            getLogger().log(Level.WARNING, "无法提交玩家区域判定任务", e);
        }
    }

    private void restartPlayerSnapshot(UUID playerUuid,
                                       PlayerLocationSnapshot location){
        pendingPlaybackRestarts.add(playerUuid);
        submitPlayerSnapshot(playerUuid, location);
    }

    public void enqueuePlaybackOperations(UUID playerUuid,
                                          List<PlaybackOperation> operations){
        if(stopping
                || playerUuid == null
                || operations == null
                || operations.isEmpty()){
            return;
        }
        PlayerOutputQueue queue = outputQueues.computeIfAbsent(
                playerUuid,
                ignored -> new PlayerOutputQueue()
        );
        // A task which passed runIfCurrent owns the per-player monitor until
        // this delta has been enqueued. Newer accepted tasks run afterwards
        // and append their deltas in FIFO order. Dropping an already-computed
        // delta here would advance the logical session without updating the
        // client, leaving the two states permanently inconsistent.
        queue.operations.addAll(operations);
        scheduleOutputDrain(playerUuid, queue);
    }

    public void requestImmediateScan(){
        if(stopping){
            return;
        }
        if(!Bukkit.isPrimaryThread()){
            try {
                Bukkit.getScheduler().runTask(this, this::requestImmediateScan);
            } catch (RuntimeException e){
                getLogger().log(Level.WARNING, "无法提交立即区域扫描", e);
            }
            return;
        }
        startParallelPlayerScan();
    }

    private BukkitTask createScanTask(){
        return Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    tickSoundSources();
                    startParallelPlayerScan();
                },
                configManager.getCheckPeriod(),
                configManager.getCheckPeriod()
        );
    }

    private void publishRuntimeState(){
        runtimeState = buildRuntimeState();
    }

    private RuntimeState buildRuntimeState(){
        return new RuntimeState(
                configManager == null ? null : configManager.getMusics(),
                configManager == null ? null : configManager.getChannelRegistry(),
                RegionSpatialIndex.build(
                        configManager == null ? null : configManager.getAreas()
                ),
                configManager == null ? null : configManager.getSoundSources()
        );
    }

    private void tickSoundSources(){
        if(stopping){
            return;
        }
        RuntimeState state = runtimeState;
        soundSourcePlaybackEngine.tick(
                state.getSoundSources(),
                System.currentTimeMillis(),
                soundSourceSink
        );
    }

    private boolean playSoundSource(SoundSource source){
        org.bukkit.World world = Bukkit.getWorld(source.getWorldName());
        if(world == null){
            return false;
        }
        try {
            world.playSound(
                    new Location(
                            world,
                            source.getX(),
                            source.getY(),
                            source.getZ()
                    ),
                    source.getSoundKey(),
                    org.bukkit.SoundCategory.AMBIENT,
                    source.getVolume(),
                    source.getPitch()
            );
            return true;
        } catch (RuntimeException e){
            getLogger().log(
                    Level.WARNING,
                    "音源播放失败: " + source.getWorldName()
                            + "/" + source.getSourceId(),
                    e
            );
            return false;
        }
    }

    private void stopSoundSource(SoundSource source){
        org.bukkit.World world = Bukkit.getWorld(source.getWorldName());
        if(world == null){
            return;
        }
        for(Player player : world.getPlayers()){
            try {
                player.stopSound(
                        source.getSoundKey(),
                        org.bukkit.SoundCategory.AMBIENT
                );
            } catch (RuntimeException e){
                getLogger().log(
                        Level.WARNING,
                        "音源停止失败: " + source.getWorldName()
                                + "/" + source.getSourceId()
                                + " -> " + player.getUniqueId(),
                        e
                );
            }
        }
    }

    private void startParallelPlayerScan(){
        if(stopping){
            return;
        }
        if(!Bukkit.isPrimaryThread()){
            requestImmediateScan();
            return;
        }
        if(!playerScanRunning.compareAndSet(false, true)){
            rescanRequested.set(true);
            return;
        }

        List<PlayerScanTarget> targets = new ArrayList<>();
        for(Player player : Bukkit.getOnlinePlayers()){
            PlayerLocationSnapshot location = PlayerLocationSnapshot.from(
                    player.getLocation()
            );
            if(location != null){
                UUID playerUuid = player.getUniqueId();
                targets.add(new PlayerScanTarget(
                        playerUuid,
                        location,
                        playerTaskCoordinator.nextRevision(playerUuid)
                ));
            }
        }
        if(targets.isEmpty()){
            finishPlayerScan();
            return;
        }

        int workerCount = Math.min(ASYNC_SCAN_WORKERS, targets.size());
        AtomicInteger remainingWorkers = new AtomicInteger(workerCount);
        for(int workerIndex = 0; workerIndex < workerCount; workerIndex++){
            final int shard = workerIndex;
            Runnable worker = () -> {
                try {
                    for(int targetIndex = shard;
                        targetIndex < targets.size();
                        targetIndex += workerCount){
                        PlayerScanTarget target = targets.get(targetIndex);
                        try {
                            playerTaskCoordinator.runIfCurrent(
                                    target.playerUuid,
                                    target.revision,
                                    () -> processPlayerSnapshot(
                                            target.playerUuid,
                                            target.location
                                    )
                            );
                        } catch (RuntimeException e){
                            getLogger().log(
                                    Level.WARNING,
                                    "玩家区域扫描失败: " + target.playerUuid,
                                    e
                            );
                        }
                    }
                } finally {
                    if(remainingWorkers.decrementAndGet() == 0){
                        finishPlayerScan();
                    }
                }
            };
            try {
                Bukkit.getScheduler().runTaskAsynchronously(this, worker);
            } catch (RuntimeException e){
                getLogger().log(Level.WARNING, "无法提交异步玩家扫描分片", e);
                if(remainingWorkers.decrementAndGet() == 0){
                    finishPlayerScan();
                }
            }
        }
    }

    private void processPlayerSnapshot(UUID playerUuid,
                                       PlayerLocationSnapshot location){
        if(stopping){
            return;
        }
        boolean restartPlayback = pendingPlaybackRestarts.remove(playerUuid);
        RuntimeState state = runtimeState;
        musicUtil.processPlayer(
                playerUuid,
                location,
                state,
                restartPlayback
        );
    }

    private void finishPlayerScan(){
        playerScanRunning.set(false);
        if(rescanRequested.getAndSet(false) && !stopping){
            try {
                Bukkit.getScheduler().runTask(this, this::startParallelPlayerScan);
            } catch (RuntimeException e){
                getLogger().log(Level.WARNING, "无法提交补充玩家扫描", e);
            }
        }
    }

    private void scheduleOutputDrain(UUID playerUuid,
                                     PlayerOutputQueue queue){
        if(!queue.scheduled.compareAndSet(false, true)){
            return;
        }
        try {
            Bukkit.getScheduler().runTask(
                    this,
                    () -> drainOutputQueue(playerUuid, queue)
            );
        } catch (RuntimeException e){
            queue.scheduled.set(false);
            getLogger().log(Level.WARNING, "无法提交声音输出任务", e);
        }
    }

    private void drainOutputQueue(UUID playerUuid,
                                  PlayerOutputQueue queue){
        while(true){
            if(outputQueues.get(playerUuid) != queue){
                queue.operations.clear();
                queue.scheduled.set(false);
                return;
            }

            List<PlaybackOperation> batch = new ArrayList<>();
            PlaybackOperation operation;
            while((operation = queue.operations.poll()) != null){
                batch.add(operation);
            }
            applyPlaybackOperations(playerUuid, batch, queue);

            queue.scheduled.set(false);
            if(queue.operations.isEmpty()
                    || !queue.scheduled.compareAndSet(false, true)){
                return;
            }
        }
    }

    private void applyPlaybackOperations(UUID playerUuid,
                                         List<PlaybackOperation> operations){
        applyPlaybackOperations(playerUuid, operations, null);
    }

    private void applyPlaybackOperations(UUID playerUuid,
                                         List<PlaybackOperation> operations,
                                         PlayerOutputQueue expectedQueue){
        if(operations == null || operations.isEmpty()){
            return;
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if(player == null || !player.isOnline()){
            return;
        }
        for(PlaybackOperation operation : operations){
            if(expectedQueue != null
                    && !isOutputContextCurrent(
                            playerUuid,
                            player,
                            expectedQueue
                    )){
                return;
            }
            try {
                if(operation.getType() == PlaybackOperation.Type.ENTER_COMMANDS){
                    EntryCommandDispatcher dispatcher = entryCommandDispatcher;
                    AreaDto area = operation.getArea();
                    if(dispatcher != null
                            && !hasActiveExitAction(playerUuid, area)
                            && isCurrentEntryArea(player, area)){
                        boolean accepted = dispatcher.dispatch(
                                player,
                                area,
                                operation.getCommandTemplates(),
                                EntryCommandDispatcher.ActionType.ENTER
                        );
                        if(accepted
                                && operation.hasFrozenExitCommands()
                                && isOutputContextCurrent(
                                        playerUuid,
                                        player,
                                        expectedQueue
                                )){
                            registerActiveExitAction(
                                    playerUuid,
                                    area,
                                    operation.getActionToken()
                            );
                        }
                    }
                    continue;
                }
                if(operation.getType() == PlaybackOperation.Type.EXIT_COMMANDS){
                    AreaDto area = operation.getArea();
                    if(consumeActiveExitAction(
                            playerUuid,
                            area,
                            operation.getActionToken()
                    )){
                        EntryCommandDispatcher dispatcher = entryCommandDispatcher;
                        if(dispatcher != null){
                            dispatcher.dispatch(
                                    player,
                                    area,
                                    operation.getCommandTemplates(),
                                    EntryCommandDispatcher.ActionType.EXIT
                            );
                        }
                    }
                    continue;
                }
                if(operation.getType() == PlaybackOperation.Type.STOP){
                    player.stopSound(
                            operation.getSoundKey(),
                            org.bukkit.SoundCategory.MUSIC
                    );
                    continue;
                }
                SelectedTrack track = operation.getTrack();
                player.playSound(
                        player,
                        track.getSoundKey(),
                        org.bukkit.SoundCategory.MUSIC,
                        track.getVolume(),
                        track.getPitch()
                );
            } catch (RuntimeException e){
                getLogger().log(
                        Level.WARNING,
                        "玩家声音操作失败: " + playerUuid,
                        e
                );
            }
        }
    }

    private boolean isOutputContextCurrent(UUID playerUuid,
                                           Player player,
                                           PlayerOutputQueue expectedQueue){
        return player != null
                && player.isOnline()
                && isEnabled()
                && !stopping
                && (expectedQueue == null
                || outputQueues.get(playerUuid) == expectedQueue);
    }

    private boolean hasActiveExitAction(UUID playerUuid, AreaDto area){
        if(playerUuid == null || area == null || area.getUuid() == null){
            return false;
        }
        return commandActivations.hasActivation(playerUuid, area.getUuid());
    }

    private void registerActiveExitAction(UUID playerUuid,
                                          AreaDto area,
                                          long actionToken){
        if(playerUuid == null
                || area == null
                || area.getUuid() == null
                || actionToken <= 0L){
            return;
        }
        commandActivations.activate(playerUuid, area.getUuid(), actionToken);
    }

    private boolean consumeActiveExitAction(UUID playerUuid,
                                            AreaDto area,
                                            long actionToken){
        if(playerUuid == null
                || area == null
                || area.getUuid() == null
                || actionToken <= 0L){
            return false;
        }
        return commandActivations.consume(
                playerUuid,
                area.getUuid(),
                actionToken
        );
    }

    private boolean isCurrentEntryArea(Player player, AreaDto expectedArea){
        if(expectedArea == null
                || !Boolean.TRUE.equals(expectedArea.getEnabled())){
            return false;
        }
        SlicedPolygonVolume shape = expectedArea.getShape();
        if(shape == null){
            return false;
        }

        Location location = player.getLocation();
        World world = location.getWorld();
        if(world == null
                || !world.getName().equals(expectedArea.getWorld())
                || !shape.contains(
                        location.getX(),
                        location.getY(),
                        location.getZ()
                )){
            return false;
        }

        RuntimeState currentState = runtimeState;
        List<AreaDto> candidates = currentState.getSpatialIndex().getCandidates(
                world.getName(),
                location.getX(),
                location.getZ()
        );
        for(AreaDto candidate : candidates){
            if(candidate == expectedArea){
                return true;
            }
        }
        return false;
    }

    private static final class PlayerScanTarget {
        private final UUID playerUuid;
        private final PlayerLocationSnapshot location;
        private final long revision;

        private PlayerScanTarget(UUID playerUuid,
                                 PlayerLocationSnapshot location,
                                 long revision) {
            this.playerUuid = playerUuid;
            this.location = location;
            this.revision = revision;
        }
    }

    private static final class PlayerOutputQueue {
        private final ConcurrentLinkedQueue<PlaybackOperation> operations =
                new ConcurrentLinkedQueue<>();
        private final AtomicBoolean scheduled = new AtomicBoolean();
    }
}
