package com.gitee.niocho.areamusic;

import com.gitee.niocho.areamusic.async.PlayerTaskCoordinator;
import com.gitee.niocho.areamusic.command.MainExecutor;
import com.gitee.niocho.areamusic.command.MainTabCompleter;
import com.gitee.niocho.areamusic.config.ConfigManager;
import com.gitee.niocho.areamusic.editor.RegionEditorService;
import com.gitee.niocho.areamusic.listener.RegionEditorListener;
import com.gitee.niocho.areamusic.listener.PlayerResourcePackStatusListener;
import com.gitee.niocho.areamusic.listener.PlayerJoinListener;
import com.gitee.niocho.areamusic.listener.PlayerQuitListener;
import com.gitee.niocho.areamusic.listener.PlayerTeleportListener;
import com.gitee.niocho.areamusic.music.PlaybackOperation;
import com.gitee.niocho.areamusic.music.PlaybackSequenceTracker;
import com.gitee.niocho.areamusic.music.PlayerPlaybackSession;
import com.gitee.niocho.areamusic.music.RecordingPlaybackSink;
import com.gitee.niocho.areamusic.music.SelectedTrack;
import com.gitee.niocho.areamusic.player.PlayerLocationSnapshot;
import com.gitee.niocho.areamusic.runtime.RuntimeState;
import com.gitee.niocho.areamusic.spatial.PlayerRegionCache;
import com.gitee.niocho.areamusic.spatial.RegionSpatialIndex;
import com.gitee.niocho.areamusic.source.SoundSource;
import com.gitee.niocho.areamusic.source.SoundSourcePlaybackEngine;
import com.gitee.niocho.areamusic.utils.MusicUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final String LEGACY_DATA_DIRECTORY = "AreaMusic";

    private ConfigManager configManager;
    private BukkitTask task;
    private RegionEditorService regionEditorService;
    private MusicUtil musicUtil;
    private final PlaybackSequenceTracker playbackSequenceTracker =
            new PlaybackSequenceTracker();
    private final PlayerRegionCache playerRegionCache =
            new PlayerRegionCache();
    private final PlayerTaskCoordinator playerTaskCoordinator =
            new PlayerTaskCoordinator();
    private final SoundSourcePlaybackEngine soundSourcePlaybackEngine =
            new SoundSourcePlaybackEngine();
    private final ConcurrentMap<UUID, PlayerPlaybackSession> playbackSessions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PlayerOutputQueue> outputQueues =
            new ConcurrentHashMap<>();
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
            migrateLegacyDataDirectory();
            configManager = new ConfigManager(this);
            musicUtil = new MusicUtil(this);
            configManager.load();
            publishRuntimeState();
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
        resourcePackReloadRevisions.clear();
        pendingPlaybackRestarts.clear();
        playbackSequenceTracker.clearAll();
        soundSourcePlaybackEngine.clear();
        playerRegionCache.clearAll();
        playerTaskCoordinator.clear();
        playerScanRunning.set(false);
        rescanRequested.set(false);
        runtimeState = RuntimeState.empty();
        craftEngineAvailable = false;

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

    private void migrateLegacyDataDirectory() throws IOException {
        Path current = getDataFolder().toPath();
        Path pluginsDirectory = current.getParent();
        if(pluginsDirectory == null){
            return;
        }
        Path legacy = pluginsDirectory.resolve(LEGACY_DATA_DIRECTORY);
        if(!Files.isDirectory(legacy)){
            return;
        }
        if(Files.exists(current)){
            try(java.util.stream.Stream<Path> entries = Files.list(current)){
                if(entries.findAny().isPresent()){
                    getLogger().warning(
                            "检测到旧目录 plugins/AreaMusic，但 plugins/RookieAreaMusic 已有内容，"
                                    + "为避免覆盖，未自动迁移"
                    );
                    return;
                }
            }
            Files.delete(current);
        }
        try {
            Files.move(legacy, current, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored){
            Files.move(legacy, current);
        }
        getLogger().info(
                "已将旧数据目录 plugins/AreaMusic 迁移为 plugins/RookieAreaMusic"
        );
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
                this::playSoundSource
        );
    }

    private void playSoundSource(SoundSource source){
        org.bukkit.World world = Bukkit.getWorld(source.getWorldName());
        if(world == null){
            return;
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
        } catch (RuntimeException e){
            getLogger().log(
                    Level.WARNING,
                    "音源播放失败: " + source.getWorldName()
                            + "/" + source.getSourceId(),
                    e
            );
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
            applyPlaybackOperations(playerUuid, batch);

            queue.scheduled.set(false);
            if(queue.operations.isEmpty()
                    || !queue.scheduled.compareAndSet(false, true)){
                return;
            }
        }
    }

    private void applyPlaybackOperations(UUID playerUuid,
                                         List<PlaybackOperation> operations){
        if(operations == null || operations.isEmpty()){
            return;
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if(player == null || !player.isOnline()){
            return;
        }
        for(PlaybackOperation operation : operations){
            try {
                if(operation.getType() == PlaybackOperation.Type.STOP){
                    player.stopSound(operation.getSoundKey());
                    continue;
                }
                SelectedTrack track = operation.getTrack();
                Location location = player.getLocation();
                player.playSound(
                        location,
                        track.getSoundKey(),
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
